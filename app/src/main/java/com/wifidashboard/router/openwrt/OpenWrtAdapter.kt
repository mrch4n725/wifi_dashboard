package com.wifidashboard.router.openwrt

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

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

        return RouterSnapshot(
            model = board?.stringOrNull("model") ?: board?.stringOrNull("board_name"),
            firmware = board?.optJSONObject("release")?.stringOrNull("description"),
            uptimeSeconds = systemInfo?.longOrNull("uptime"),
            interfaces = networkDump?.interfaces(),
            dhcpLeases = leases?.dhcpClients().orEmpty(),
            wirelessClients = wirelessClients(issues),
            capabilities = capabilities,
            issues = issues,
        )
    }

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
    ): JSONObject? {
        if (!capabilities.supports(objectName, methodName)) return null
        if (transportError != null) return null
        return try {
            rpc.call(sessionToken, objectName, methodName)
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

private fun JSONObject.dhcpClients(): List<RouterClient> =
    optJSONArray("device")
        ?.jsonObjects()
        .orEmpty()
        .flatMap { device ->
            device.optJSONArray("leases")
                ?.jsonObjects()
                .orEmpty()
                .map { lease ->
                    RouterClient(
                        macAddress = lease.stringOrNull("macaddr"),
                        ipAddress = lease.stringOrNull("ipaddr"),
                        hostname = lease.stringOrNull("hostname"),
                        source = "OpenWrt DHCP lease",
                        details = lease.longOrNull("expires")?.let { "Lease expires in $it seconds" },
                    )
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
