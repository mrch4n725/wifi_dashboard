package com.wifidashboard.router.openwrt

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest

class OpenWrtAdapter(
    private val transport: UbusTransport = HttpUbusTransport(),
) {
    fun connect(
        endpoint: OpenWrtEndpoint,
        credentials: RouterCredentials,
    ): OpenWrtRouterSession {
        val rpc = OpenWrtRpcClient(endpoint, transport)
        val sessionToken = rpc.login(credentials)
        val capabilities = try {
            detectCapabilities(rpc.listObjects())
        } catch (error: IOException) {
            RouterCapabilities(
                detected = false,
                methods = emptySet(),
                detectionMessage =
                    "Authenticated, but router capability listing failed: ${error.message}",
            )
        }
        return OpenWrtRouterSession(rpc, sessionToken, capabilities)
    }

    private fun detectCapabilities(objects: JSONObject): RouterCapabilities {
        val methods = buildSet {
            val objectNames = objects.keys()
            while (objectNames.hasNext()) {
                val objectName = objectNames.next()
                val objectMethods = objects.optJSONObject(objectName) ?: continue
                val methodNames = objectMethods.keys()
                while (methodNames.hasNext()) {
                    add(UbusMethod(objectName, methodNames.next()))
                }
            }
        }
        return RouterCapabilities(detected = true, methods = methods)
    }
}

class OpenWrtRouterSession internal constructor(
    private val rpc: OpenWrtRpcClient,
    private val sessionToken: String,
    val capabilities: RouterCapabilities,
) {
    private var loggedOut = false
    private var transportError: String? = null

    fun inspect(): RouterSnapshot {
        check(!loggedOut) { "Router session is closed; reconnect to inspect it" }
        transportError = null
        val issues = mutableListOf<String>()
        val board = callIfSupported("system", "board", issues)
        val systemInfo = callIfSupported("system", "info", issues)
        val networkDump = callIfSupported("network.interface", "dump", issues)
        val leases = callIfSupported("dhcp", "ipv4leases", issues)
        val wirelessConfig = callIfSupported(
            "uci",
            "get",
            issues,
            JSONObject().put("config", "wireless"),
        )

        return RouterSnapshot(
            model = board?.stringOrNull("model") ?: board?.stringOrNull("board_name"),
            firmware = board?.optJSONObject("release")?.stringOrNull("description"),
            uptimeSeconds = systemInfo?.longOrNull("uptime"),
            interfaces = networkDump?.interfaces(),
            wirelessNetworks = wirelessConfig?.wirelessNetworks(),
            dhcpLeases = leases?.dhcpClients().orEmpty(),
            wirelessClients = wirelessClients(issues),
            capabilities = capabilities,
            issues = issues,
        )
    }

    fun previewInternetAccess(
        macAddress: String,
        sourceInterface: String,
        action: InternetAccessAction,
    ): InternetAccessPreview {
        check(!loggedOut) { "Router session is closed; reconnect to continue" }
        require(sourceInterface.matches(Regex("[A-Za-z0-9_.-]{1,64}"))) {
            "Router did not report a usable network interface for this client"
        }
        val requiredMethods = setOf("get", "add", "delete", "order", "changes", "revert", "apply", "confirm", "rollback")
        val missing = requiredMethods.filterNot { capabilities.supports("uci", it) }
        if (!capabilities.detected || missing.isNotEmpty()) {
            throw AccessControlNotSupportedException(
                if (!capabilities.detected) {
                    "Router capabilities could not be detected; internet access control is unavailable."
                } else {
                    "Router does not advertise required UCI methods: ${missing.joinToString()}."
                },
            )
        }

        val pending = rpc.call(sessionToken, "uci", "changes", JSONObject().put("config", "firewall"))
        val changes = pending.optJSONArray("changes")
        if (changes != null && changes.length() > 0) {
            throw AccessControlNotSupportedException(
                "This router session already has pending firewall edits. Reconnect before changing device access.",
            )
        }

        val configuration = rpc.call(sessionToken, "uci", "get", JSONObject().put("config", "firewall"))
        val sections = configuration.optJSONObject("values")
            ?: throw AccessControlNotSupportedException("Router did not return a readable firewall configuration.")
        val parsed = sections.toSectionList()
        val zones = parsed.filter { it.stringOrNull(".type") == "zone" }
        val forwards = parsed.filter { it.stringOrNull(".type") == "forwarding" }
        val sourceZones = zones.filter { it.hasNetwork(sourceInterface) }
        val sourceZone = sourceZones.singleOrNull()?.stringOrNull("name")
        val hasWanZone = zones.any { it.stringOrNull("name") == "wan" && it.hasNetwork("wan") }
        val forwardsToWan = sourceZone != null && forwards.any {
            it.stringOrNull("src") == sourceZone && it.stringOrNull("dest") == "wan"
        }
        if (sourceZone.isNullOrBlank() || !hasWanZone || !forwardsToWan) {
            throw AccessControlNotSupportedException(
                "The client interface must map to exactly one firewall zone with forwarding to WAN. No firewall changes were made.",
            )
        }
        val policy = InternetAccessPolicy.forDevice(macAddress, sourceZone)

        val matching = parsed.filter { it.stringOrNull(".name") == policy.sectionName }
        if (matching.size > 1 || (matching.isNotEmpty() && !matching.single().isOurPolicy(policy))) {
            throw AccessControlNotSupportedException(
                "A router firewall section conflicts with this app-owned policy name. No firewall changes were made.",
            )
        }
        if (action == InternetAccessAction.RESTORE && matching.isEmpty()) {
            throw AccessControlNotSupportedException("This device has no app-owned block rule to restore.")
        }
        return InternetAccessPreview(
            policy = policy,
            action = action,
            existingSection = matching.isNotEmpty(),
            firewallFingerprint = configuration.fingerprint(),
        )
    }

    fun applyInternetAccess(preview: InternetAccessPreview): InternetAccessOperation {
        check(!loggedOut) { "Router session is closed; reconnect to continue" }
        val pending = rpc.call(sessionToken, "uci", "changes", JSONObject().put("config", "firewall"))
        if ((pending.optJSONArray("changes")?.length() ?: 0) > 0) {
            throw AccessControlNotSupportedException("Pending firewall edits were found. Reconnect before applying this change.")
        }
        val latest = rpc.call(sessionToken, "uci", "get", JSONObject().put("config", "firewall"))
        if (latest.fingerprint() != preview.firewallFingerprint) {
            throw AccessControlNotSupportedException("Router firewall settings changed after preview. Refresh and review a new preview.")
        }

        if (preview.action == InternetAccessAction.BLOCK && preview.existingSection) {
            return InternetAccessOperation(
                preview.action,
                AccessControlState.APPLIED,
                "The app-owned block policy is already present in the router configuration. Effective device internet blocking cannot be verified through this router API.",
            )
        }

        val section = preview.policy.sectionName
        val appSection = JSONObject()
            .put("name", "WiFi Dashboard internet access")
            .put("src", preview.policy.sourceZone)
            .put("dest", preview.policy.destinationZone)
            .put("src_mac", preview.policy.macAddress)
            .put("proto", "all")
            .put("enabled", "1")
            .put("target", preview.policy.target)
        var applyStarted = false
        try {
            if (preview.action == InternetAccessAction.BLOCK && !preview.existingSection) {
                rpc.call(
                    sessionToken,
                    "uci",
                    "add",
                    JSONObject()
                        .put("config", "firewall")
                        .put("type", "rule")
                        .put("name", section)
                        .put("values", appSection),
                )
                // Move only the app-owned rule ahead of broad allow rules. Other sections keep their relative order.
                rpc.call(
                    sessionToken,
                    "uci",
                    "order",
                    JSONObject().put("config", "firewall").put("sections", JSONArray().put(section)),
                )
            } else if (preview.action == InternetAccessAction.RESTORE) {
                rpc.call(
                    sessionToken,
                    "uci",
                    "delete",
                    JSONObject().put("config", "firewall").put("section", section),
                )
            }

            rpc.call(
                sessionToken,
                "uci",
                "apply",
                JSONObject()
                    .put("rollback", true)
                    .put("timeout", APPLY_ROLLBACK_TIMEOUT_SECONDS)
                    .put("ubus_rpc_session", sessionToken),
            )
            applyStarted = true
            val afterApply = rpc.call(sessionToken, "uci", "get", JSONObject().put("config", "firewall"))
            val appliedSection = afterApply.optJSONObject("values")?.toSectionList()
                ?.singleOrNull { it.stringOrNull(".name") == section }
            val verifiedInConfig = if (preview.action == InternetAccessAction.BLOCK) {
                appliedSection?.isOurPolicy(preview.policy) == true
            } else {
                appliedSection == null
            }
            if (!verifiedInConfig) {
                rpc.call(sessionToken, "uci", "rollback", JSONObject().put("ubus_rpc_session", sessionToken))
                return InternetAccessOperation(
                    preview.action,
                    AccessControlState.ROLLED_BACK,
                    "Router configuration did not match the requested policy; the router rollback was requested.",
                )
            }

            rpc.call(sessionToken, "uci", "confirm", JSONObject().put("ubus_rpc_session", sessionToken))
            return InternetAccessOperation(
                preview.action,
                AccessControlState.APPLIED,
                "Router accepted the ${preview.action.name.lowercase()} policy. Effective device internet blocking cannot be verified through this router API.",
            )
        } catch (error: IOException) {
            runCatching {
                if (applyStarted) {
                    rpc.call(sessionToken, "uci", "rollback", JSONObject().put("ubus_rpc_session", sessionToken))
                } else {
                    rpc.call(sessionToken, "uci", "revert", JSONObject().put("config", "firewall"))
                }
            }
            throw error
        }
    }

    fun previewWirelessSsid(sectionName: String, newSsid: String): WirelessSsidPreview {
        check(!loggedOut) { "Router session is closed; reconnect to continue" }
        require(sectionName.matches(Regex("[A-Za-z0-9_.-]{1,64}"))) {
            "Router did not report a usable wireless section"
        }
        validateSsid(newSsid)
        requireWirelessConfigMethods()
        requireNoPendingUciChanges()

        val configuration = readWirelessConfiguration()
        val network = configuration.wirelessNetworks()
            ?.singleOrNull { it.sectionName == sectionName }
            ?: throw WirelessConfigNotSupportedException("The selected wireless section is unavailable or ambiguous.")
        if (network.mode != "ap") {
            throw WirelessConfigNotSupportedException("Only router-reported access-point interfaces can be renamed.")
        }
        if (network.ssid.isNullOrBlank()) {
            throw WirelessConfigNotSupportedException("The selected access point has no readable SSID.")
        }
        require(newSsid != network.ssid) { "Enter a different SSID" }
        return WirelessSsidPreview(network, newSsid, configuration.fingerprint())
    }

    fun applyWirelessSsid(preview: WirelessSsidPreview): WirelessConfigOperation {
        check(!loggedOut) { "Router session is closed; reconnect to continue" }
        requireWirelessConfigMethods()
        validateSsid(preview.newSsid)
        requireNoPendingUciChanges()
        val latestConfiguration = readWirelessConfiguration()
        if (latestConfiguration.fingerprint() != preview.wirelessFingerprint) {
            throw WirelessConfigNotSupportedException(
                "Router wireless settings changed after preview. Refresh and review a new preview.",
            )
        }
        val currentNetwork = latestConfiguration.wirelessNetworks()
            ?.singleOrNull { it.sectionName == preview.network.sectionName }
        if (currentNetwork?.mode != "ap" || currentNetwork != preview.network) {
            throw WirelessConfigNotSupportedException(
                "The preview does not match the selected access point. Refresh and review a new preview.",
            )
        }

        var applyStarted = false
        try {
            rpc.call(
                sessionToken,
                "uci",
                "set",
                JSONObject()
                    .put("config", "wireless")
                    .put("section", preview.network.sectionName)
                    .put("values", JSONObject().put("ssid", preview.newSsid)),
            )
            rpc.call(
                sessionToken,
                "uci",
                "apply",
                JSONObject()
                    .put("rollback", true)
                    .put("timeout", APPLY_ROLLBACK_TIMEOUT_SECONDS)
                    .put("ubus_rpc_session", sessionToken),
            )
            applyStarted = true
            val savedNetwork = readWirelessConfiguration().wirelessNetworks()
                ?.singleOrNull { it.sectionName == preview.network.sectionName }
            if (savedNetwork?.ssid != preview.newSsid) {
                rpc.call(sessionToken, "uci", "rollback", JSONObject().put("ubus_rpc_session", sessionToken))
                return WirelessConfigOperation(
                    WirelessConfigState.ROLLED_BACK,
                    "Router wireless configuration did not match the requested SSID; rollback was requested.",
                )
            }

            rpc.call(sessionToken, "uci", "confirm", JSONObject().put("ubus_rpc_session", sessionToken))
            return WirelessConfigOperation(
                WirelessConfigState.APPLIED,
                "Router accepted the new SSID. This confirms saved router configuration, not that every client can reconnect.",
            )
        } catch (error: IOException) {
            runCatching {
                if (applyStarted) {
                    rpc.call(sessionToken, "uci", "rollback", JSONObject().put("ubus_rpc_session", sessionToken))
                } else {
                    rpc.call(sessionToken, "uci", "revert", JSONObject().put("config", "wireless"))
                }
            }
            throw error
        }
    }

    private fun requireWirelessConfigMethods() {
        val requiredMethods = setOf("get", "set", "changes", "revert", "apply", "confirm", "rollback")
        val missing = requiredMethods.filterNot { capabilities.supports("uci", it) }
        if (!capabilities.detected || missing.isNotEmpty()) {
            throw WirelessConfigNotSupportedException(
                if (!capabilities.detected) {
                    "Router capabilities could not be detected; wireless configuration is unavailable."
                } else {
                    "Router does not advertise required UCI methods: ${missing.joinToString()}."
                },
            )
        }
    }

    private fun validateSsid(ssid: String) {
        require(ssid.isNotBlank() && ssid.toByteArray(Charsets.UTF_8).size <= MAX_SSID_BYTES) {
            "SSID must contain 1 to $MAX_SSID_BYTES UTF-8 bytes"
        }
        require(ssid.none { it.isISOControl() }) { "SSID cannot contain control characters" }
    }

    private fun requireNoPendingUciChanges() {
        val pending = rpc.call(sessionToken, "uci", "changes")
        if ((pending.optJSONArray("changes")?.length() ?: 0) > 0) {
            throw WirelessConfigNotSupportedException(
                "This router session has pending UCI edits. Reconnect before changing the SSID.",
            )
        }
    }

    private fun readWirelessConfiguration(): JSONObject = rpc.call(
        sessionToken,
        "uci",
        "get",
        JSONObject().put("config", "wireless"),
    ).takeIf { it.optJSONObject("values") != null }
        ?: throw WirelessConfigNotSupportedException("Router did not return a readable wireless configuration.")

    fun logout() {
        if (loggedOut) return
        rpc.call(
            sessionToken,
            "session",
            "logout",
            JSONObject(),
        )
        loggedOut = true
    }

    private fun callIfSupported(
        objectName: String,
        methodName: String,
        issues: MutableList<String>,
        arguments: JSONObject = JSONObject(),
    ): JSONObject? {
        if (!capabilities.supports(objectName, methodName)) return null
        if (transportError != null) return null
        return try {
            rpc.call(sessionToken, objectName, methodName, arguments)
        } catch (error: OpenWrtRpcException) {
            issues += "$objectName.$methodName failed: ${error.message}"
            null
        } catch (error: IOException) {
            issues += "$objectName.$methodName failed: ${error.message}"
            transportError = error.message ?: "Router transport failed"
            null
        }
    }

    private fun wirelessClients(issues: MutableList<String>): List<RouterClient> =
        capabilities.methods
            .filter { it.objectName.startsWith("hostapd.") && it.methodName == "get_clients" }
            .flatMap { method ->
                val response = callIfSupported(method.objectName, method.methodName, issues)
                    ?: return@flatMap emptyList()
                val clients = response.optJSONObject("clients") ?: return@flatMap emptyList()
                val keys = clients.keys()
                buildList {
                    while (keys.hasNext()) {
                        val mac = keys.next()
                        val details = clients.optJSONObject(mac) ?: continue
                        add(
                            RouterClient(
                                macAddress = details.stringOrNull("mac") ?: mac,
                                ipAddress = null,
                                hostname = null,
                                source = method.objectName,
                                details = details.clientDetails(),
                            ),
                        )
                    }
                }
            }
}

class AccessControlNotSupportedException(message: String) : IOException(message)

class WirelessConfigNotSupportedException(message: String) : IOException(message)

private const val APPLY_ROLLBACK_TIMEOUT_SECONDS = 45
private const val MAX_SSID_BYTES = 32

private fun JSONObject.toSectionList(): List<JSONObject> {
    val keys = keys()
    return buildList {
        while (keys.hasNext()) optJSONObject(keys.next())?.let(::add)
    }
}

private fun JSONObject.hasNetwork(network: String): Boolean = when (val value = opt("network")) {
    is JSONArray -> (0 until value.length()).any { value.optString(it) == network }
    is String -> value.split(Regex("\\s+")).contains(network)
    else -> false
}

private fun JSONObject.wirelessNetworks(): List<RouterWirelessNetwork> {
    val sections = optJSONObject("values")?.toSectionList().orEmpty()
    val radios = sections
        .filter { it.stringOrNull(".type") == "wifi-device" }
        .associate { it.stringOrNull(".name") to it.stringOrNull("channel") }
    return sections
        .filter { it.stringOrNull(".type") == "wifi-iface" }
        .mapNotNull { section ->
            val name = section.stringOrNull(".name") ?: return@mapNotNull null
            val radio = section.stringOrNull("device")
            RouterWirelessNetwork(
                sectionName = name,
                mode = section.stringOrNull("mode"),
                ssid = section.stringOrNull("ssid"),
                radio = radio,
                encryption = section.stringOrNull("encryption"),
                channel = radios[radio],
                disabled = section.stringOrNull("disabled") == "1",
            )
        }
}

private fun JSONObject.isOurPolicy(policy: InternetAccessPolicy): Boolean =
    stringOrNull(".type") == "rule" &&
        stringOrNull("name") == "WiFi Dashboard internet access" &&
        stringOrNull("src") == policy.sourceZone &&
        stringOrNull("dest") == policy.destinationZone &&
        stringOrNull("src_mac")?.lowercase() == policy.macAddress &&
        stringOrNull("proto") == "all" &&
        stringOrNull("enabled").let { it == null || it == "1" } &&
        stringOrNull("target") == policy.target

private fun JSONObject.fingerprint(): String = MessageDigest.getInstance("SHA-256")
    .digest(toString().toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(it) }

private fun JSONObject.interfaces(): List<RouterInterface> =
    optJSONArray("interface")
        ?.jsonObjects()
        .orEmpty()
        .map { item ->
            val addresses = buildList {
                item.optJSONArray("ipv4-address")?.jsonObjects().orEmpty().forEach { address ->
                    address.stringOrNull("address")?.let { add(it) }
                }
                item.optJSONArray("ipv6-address")?.jsonObjects().orEmpty().forEach { address ->
                    address.stringOrNull("address")?.let { add(it) }
                }
            }
            RouterInterface(
                name = item.stringOrNull("interface") ?: "Unknown interface",
                protocol = item.stringOrNull("proto"),
                device = item.stringOrNull("l3_device") ?: item.stringOrNull("device"),
                isUp = item.booleanOrNull("up"),
                addresses = addresses.distinct(),
            )
        }

private fun JSONObject.dhcpClients(): List<RouterClient> {
    val devices = optJSONObject("device") ?: return emptyList()
    val names = devices.keys()
    return buildList {
        while (names.hasNext()) {
            val networkInterface = names.next()
            val iface = devices.optJSONObject(networkInterface) ?: continue
            iface.optJSONArray("leases")?.jsonObjects().orEmpty().forEach { lease ->
                val valid = lease.signedLongOrNull("valid") ?: lease.signedLongOrNull("expires")
                add(
                    RouterClient(
                        macAddress = lease.stringOrNull("mac") ?: lease.stringOrNull("macaddr"),
                        ipAddress = lease.stringOrNull("address") ?: lease.stringOrNull("ipaddr"),
                        hostname = lease.stringOrNull("hostname"),
                        source = "OpenWrt DHCP lease",
                        details = valid?.let { if (it < 0) "Lease does not expire" else "Lease valid for $it seconds" },
                        networkInterface = networkInterface,
                    ),
                )
            }
        }
    }
}

private fun JSONObject.clientDetails(): String? {
    val details = buildList {
        signedLongOrNull("signal")?.let { add("Signal $it dBm") }
        longOrNull("rx_bytes")?.let { add("RX $it bytes") }
        longOrNull("tx_bytes")?.let { add("TX $it bytes") }
        booleanOrNull("authorized")?.let { add(if (it) "Authorized" else "Not authorized") }
    }
    return details.takeIf(List<String>::isNotEmpty)?.joinToString(" · ")
}
