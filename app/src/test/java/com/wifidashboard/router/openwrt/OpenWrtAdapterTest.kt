package com.wifidashboard.router.openwrt

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OpenWrtAdapterTest {
    @Test
    fun endpointRequiresExplicitHttpConfirmationAndAcceptsOnlyAnOrigin() {
        assertEquals(
            "https://192.168.1.1",
            OpenWrtEndpoint.parse("https://192.168.1.1/", allowCleartext = false).origin,
        )
        assertEquals(
            "http://router.local:8080",
            OpenWrtEndpoint.parse("http://router.local:8080", allowCleartext = true).origin,
        )
        assertTrue(
            runCatching { OpenWrtEndpoint.parse("http://192.168.1.1", allowCleartext = false) }
                .exceptionOrNull() is IllegalArgumentException,
        )
        assertTrue(
            runCatching { OpenWrtEndpoint.parse("https://router.local/cgi-bin", allowCleartext = false) }
                .exceptionOrNull() is IllegalArgumentException,
        )
        assertTrue(
            runCatching { OpenWrtEndpoint.parse("https://user:pass@router.local", allowCleartext = false) }
                .exceptionOrNull() is IllegalArgumentException,
        )
    }

    @Test
    fun authenticatesDetectsCapabilitiesAndReadsSupportedRouterData() {
        val transport = ResponseQueue(
            success(
                JSONObject()
                    .put("ubus_rpc_session", "temporary-session-token"),
            ),
            success(
                JSONObject()
                    .put("system", methods("board", "info"))
                    .put("network.interface", methods("dump"))
                    .put("dhcp", methods("ipv4leases"))
                    .put("hostapd.wlan0", methods("get_clients")),
            ),
            success(JSONObject().put("model", "Test Router").put("release", JSONObject().put("description", "OpenWrt test"))),
            success(JSONObject().put("uptime", 7_200)),
            success(
                JSONObject().put(
                    "interface",
                    JSONArray().put(
                        JSONObject()
                            .put("interface", "lan")
                            .put("proto", "static")
                            .put("l3_device", "br-lan")
                            .put("up", true)
                            .put(
                                "ipv4-address",
                                JSONArray().put(JSONObject().put("address", "192.168.1.1")),
                            ),
                    ),
                ),
            ),
            success(
                JSONObject().put(
                    "device",
                    JSONObject().put(
                        "lan",
                        JSONObject().put(
                            "leases",
                            JSONArray().put(
                                JSONObject()
                                    .put("hostname", "printer")
                                    .put("address", "192.168.1.42")
                                    .put("mac", "aa:bb:cc:dd:ee:ff")
                                    .put("valid", 300),
                            ),
                        ),
                    ),
                ),
            ),
            success(
                JSONObject().put(
                    "clients",
                    JSONObject().put(
                        "aa:bb:cc:dd:ee:ff",
                        JSONObject().put("signal", -45).put("authorized", true),
                    ),
                ),
            ),
        )
        val endpoint = OpenWrtEndpoint.parse("https://192.168.1.1", allowCleartext = false)

        val session = OpenWrtAdapter(transport).connect(
            endpoint,
            RouterCredentials("root", "test-password"),
        )
        val snapshot = session.inspect()

        assertTrue(session.capabilities.detected)
        assertTrue(session.capabilities.supports("dhcp", "ipv4leases"))
        assertEquals("Test Router", snapshot.model)
        assertEquals("OpenWrt test", snapshot.firmware)
        assertEquals(7_200L, snapshot.uptimeSeconds)
        assertEquals("192.168.1.1", snapshot.interfaces?.single()?.addresses?.single())
        assertEquals("printer", snapshot.dhcpLeases.single().hostname)
        assertEquals("192.168.1.42", snapshot.dhcpLeases.single().ipAddress)
        assertEquals("lan", snapshot.dhcpLeases.single().networkInterface)
        assertTrue(snapshot.dhcpLeases.single().details.orEmpty().contains("300"))
        assertEquals("aa:bb:cc:dd:ee:ff", snapshot.wirelessClients.single().macAddress)
        assertTrue(snapshot.wirelessClients.single().details.orEmpty().contains("Signal -45 dBm"))
        assertTrue(snapshot.issues.isEmpty())
        assertEquals(7, transport.requests.size)
        assertEquals("login", transport.requests.first().getJSONArray("params").getString(2))
        assertEquals("call", transport.requests[2].getString("method"))
        assertEquals("system", transport.requests[2].getJSONArray("params").getString(1))
    }

    @Test
    fun unsupportedMethodsAreNotCalledAndRouterErrorsAreReported() {
        val transport = ResponseQueue(
            success(JSONObject().put("ubus_rpc_session", "session")),
            success(JSONObject().put("system", methods("board"))),
            failure(6),
        )
        val session = OpenWrtAdapter(transport).connect(
            OpenWrtEndpoint.parse("https://router.local", allowCleartext = false),
            RouterCredentials("root", "test-password"),
        )

        val snapshot = session.inspect()

        assertEquals(3, transport.requests.size)
        assertNull(snapshot.interfaces)
        assertTrue(snapshot.dhcpLeases.isEmpty())
        assertEquals(1, snapshot.issues.size)
        assertTrue(snapshot.issues.single().contains("ubus status 6"))
    }

    @Test
    fun capabilityListingFailureDoesNotPretendCapabilitiesWereDetected() {
        val transport = ResponseQueue(
            success(JSONObject().put("ubus_rpc_session", "session")),
            failure( ubusStatus = 6),
        )
        val session = OpenWrtAdapter(transport).connect(
            OpenWrtEndpoint.parse("https://router.local", allowCleartext = false),
            RouterCredentials("root", "test-password"),
        )

        assertFalse(session.capabilities.detected)
        assertTrue(session.capabilities.methods.isEmpty())
        assertTrue(session.capabilities.detectionMessage.orEmpty().contains("listing failed"))
        assertEquals(2, transport.requests.size)
    }

    @Test
    fun internetAccessBlockUsesNamedAppRuleAndTimedApplyThenConfirmsOnlyAfterConfigCheck() {
        val mac = "aa:bb:cc:dd:ee:ff"
        val policy = InternetAccessPolicy.forDevice(mac, "lan")
        val emptyFirewall = firewallConfig()
        val blockedFirewall = firewallConfig(
            JSONObject()
                .put(".type", "rule")
                .put(".name", policy.sectionName)
                .put("name", "WiFi Dashboard internet access")
                .put("src", "lan")
                .put("dest", "wan")
                .put("src_mac", mac)
                .put("proto", "all")
                .put("target", "REJECT"),
        )
        val transport = ResponseQueue(
            success(JSONObject().put("ubus_rpc_session", "session")),
            success(accessControlCapabilities()),
            success(JSONObject().put("changes", JSONArray())),
            success(emptyFirewall),
            success(JSONObject().put("changes", JSONArray())),
            success(emptyFirewall),
            success(JSONObject().put("section", policy.sectionName)),
            success(JSONObject()),
            success(JSONObject()),
            success(blockedFirewall),
            success(JSONObject()),
        )
        val session = OpenWrtAdapter(transport).connect(
            OpenWrtEndpoint.parse("https://router.local", allowCleartext = false),
            RouterCredentials("root", "test-password"),
        )
        val preview = session.previewInternetAccess(mac, "lan", InternetAccessAction.BLOCK)

        assertFalse(preview.existingSection)
        assertEquals(
            AccessControlState.APPLIED,
            session.applyInternetAccess(preview).state,
        )
        val calls = transport.requests.filter { it.getString("method") == "call" }
            .map { it.getJSONArray("params") }
        val methods = calls.map { "${it.getString(1)}.${it.getString(2)}" }
        assertTrue(methods.contains("uci.add"))
        assertTrue(methods.contains("uci.order"))
        assertTrue(methods.contains("uci.apply"))
        assertTrue(methods.contains("uci.confirm"))
        val applyArgs = calls.single { it.getString(2) == "apply" }.getJSONObject(3)
        assertTrue(applyArgs.getBoolean("rollback"))
        assertEquals(45, applyArgs.getInt("timeout"))
    }

    @Test
    fun internetAccessDoesNotModifyNonstandardFirewallZones() {
        val transport = ResponseQueue(
            success(JSONObject().put("ubus_rpc_session", "session")),
            success(accessControlCapabilities()),
            success(JSONObject().put("changes", JSONArray())),
            success(firewallConfig(zones = listOf("home", "uplink"))),
        )
        val session = OpenWrtAdapter(transport).connect(
            OpenWrtEndpoint.parse("https://router.local", allowCleartext = false),
            RouterCredentials("root", "test-password"),
        )

        assertThrows(AccessControlNotSupportedException::class.java) {
            session.previewInternetAccess("aa:bb:cc:dd:ee:ff", "lan", InternetAccessAction.BLOCK)
        }
        assertEquals(4, transport.requests.size)
    }

    @Test
    fun internetAccessUsesTheSelectedClientInterfaceZone() {
        val firewall = firewallConfig()
        val values = firewall.getJSONObject("values")
            .put(
                "cfg-guest",
                JSONObject().put(".type", "zone").put(".name", "cfg-guest")
                    .put("name", "guest").put("network", JSONArray().put("guest")),
            )
            .put(
                "cfg-guest-forward",
                JSONObject().put(".type", "forwarding").put(".name", "cfg-guest-forward")
                    .put("src", "guest").put("dest", "wan"),
            )
        val transport = ResponseQueue(
            success(JSONObject().put("ubus_rpc_session", "session")),
            success(accessControlCapabilities()),
            success(JSONObject().put("changes", JSONArray())),
            success(firewall),
        )
        val session = OpenWrtAdapter(transport).connect(
            OpenWrtEndpoint.parse("https://router.local", allowCleartext = false),
            RouterCredentials("root", "test-password"),
        )

        val preview = session.previewInternetAccess("aa:bb:cc:dd:ee:ff", "guest", InternetAccessAction.BLOCK)

        assertEquals("guest", preview.policy.sourceZone)
    }

    @Test
    fun internetAccessRequestsRouterRollbackWhenReadBackDoesNotMatch() {
        val mac = "aa:bb:cc:dd:ee:ff"
        val firewall = firewallConfig()
        val transport = ResponseQueue(
            success(JSONObject().put("ubus_rpc_session", "session")),
            success(accessControlCapabilities()),
            success(JSONObject().put("changes", JSONArray())),
            success(firewall),
            success(JSONObject().put("changes", JSONArray())),
            success(firewall),
            success(JSONObject().put("section", InternetAccessPolicy.forDevice(mac, "lan").sectionName)),
            success(JSONObject()),
            success(JSONObject()),
            success(firewall),
            success(JSONObject()),
        )
        val session = OpenWrtAdapter(transport).connect(
            OpenWrtEndpoint.parse("https://router.local", allowCleartext = false),
            RouterCredentials("root", "test-password"),
        )
        val preview = session.previewInternetAccess(mac, "lan", InternetAccessAction.BLOCK)

        val result = session.applyInternetAccess(preview)

        assertEquals(AccessControlState.ROLLED_BACK, result.state)
        val methods = transport.requests.filter { it.getString("method") == "call" }
            .map { it.getJSONArray("params").getString(2) }
        assertTrue(methods.contains("rollback"))
        assertFalse(methods.contains("confirm"))
    }

    @Test
    fun wirelessInspectionReadsAccessPointSettingsAndRadioChannel() {
        val transport = ResponseQueue(
            success(JSONObject().put("ubus_rpc_session", "session")),
            success(JSONObject().put("uci", methods("get"))),
            success(wirelessConfig("Home network")),
        )
        val session = OpenWrtAdapter(transport).connect(
            OpenWrtEndpoint.parse("https://router.local", allowCleartext = false),
            RouterCredentials("root", "test-password"),
        )

        val snapshot = session.inspect()

        assertEquals("Home network", snapshot.wirelessNetworks?.single()?.ssid)
        assertEquals("radio0", snapshot.wirelessNetworks?.single()?.radio)
        assertEquals("11", snapshot.wirelessNetworks?.single()?.channel)
        assertEquals("psk2", snapshot.wirelessNetworks?.single()?.encryption)
    }

    @Test
    fun wirelessSsidChangeUsesTimedApplyAndConfirmsOnlyAfterReadBack() {
        val original = wirelessConfig("Old network")
        val updated = wirelessConfig("New network")
        val transport = ResponseQueue(
            success(JSONObject().put("ubus_rpc_session", "session")),
            success(wirelessWriteCapabilities()),
            success(JSONObject().put("changes", JSONArray())),
            success(original),
            success(JSONObject().put("changes", JSONArray())),
            success(original),
            success(JSONObject()),
            success(JSONObject()),
            success(updated),
            success(JSONObject()),
        )
        val session = OpenWrtAdapter(transport).connect(
            OpenWrtEndpoint.parse("https://router.local", allowCleartext = false),
            RouterCredentials("root", "test-password"),
        )
        val preview = session.previewWirelessSsid("cfg-ap", "New network")

        val result = session.applyWirelessSsid(preview)

        assertEquals(WirelessConfigState.APPLIED, result.state)
        val calls = transport.requests.filter { it.getString("method") == "call" }
            .map { it.getJSONArray("params") }
        val methods = calls.map { "${it.getString(1)}.${it.getString(2)}" }
        assertTrue(methods.contains("uci.set"))
        assertTrue(methods.contains("uci.apply"))
        assertTrue(methods.contains("uci.confirm"))
        val applyArgs = calls.single { it.getString(2) == "apply" }.getJSONObject(3)
        assertTrue(applyArgs.getBoolean("rollback"))
        assertEquals(45, applyArgs.getInt("timeout"))
        val setArgs = calls.single { it.getString(2) == "set" }.getJSONObject(3)
        assertEquals("New network", setArgs.getJSONObject("values").getString("ssid"))
    }

    @Test
    fun wirelessSsidChangeRefusesPendingUciEditsBeforeReadingWirelessConfig() {
        val transport = ResponseQueue(
            success(JSONObject().put("ubus_rpc_session", "session")),
            success(wirelessWriteCapabilities()),
            success(JSONObject().put("changes", JSONArray().put("firewall.rule=changed"))),
        )
        val session = OpenWrtAdapter(transport).connect(
            OpenWrtEndpoint.parse("https://router.local", allowCleartext = false),
            RouterCredentials("root", "test-password"),
        )

        assertThrows(WirelessConfigNotSupportedException::class.java) {
            session.previewWirelessSsid("cfg-ap", "New network")
        }
        assertEquals(3, transport.requests.size)
    }

    @Test
    fun wirelessSsidChangeDoesNotAllowRenamingStationInterfaces() {
        val transport = ResponseQueue(
            success(JSONObject().put("ubus_rpc_session", "session")),
            success(wirelessWriteCapabilities()),
            success(JSONObject().put("changes", JSONArray())),
            success(wirelessConfig("Client network", mode = "sta")),
        )
        val session = OpenWrtAdapter(transport).connect(
            OpenWrtEndpoint.parse("https://router.local", allowCleartext = false),
            RouterCredentials("root", "test-password"),
        )

        assertThrows(WirelessConfigNotSupportedException::class.java) {
            session.previewWirelessSsid("cfg-ap", "New network")
        }
        assertEquals(4, transport.requests.size)
    }

    @Test
    fun wirelessSsidChangeRequestsRollbackWhenReadBackDoesNotMatch() {
        val original = wirelessConfig("Old network")
        val transport = ResponseQueue(
            success(JSONObject().put("ubus_rpc_session", "session")),
            success(wirelessWriteCapabilities()),
            success(JSONObject().put("changes", JSONArray())),
            success(original),
            success(JSONObject().put("changes", JSONArray())),
            success(original),
            success(JSONObject()),
            success(JSONObject()),
            success(original),
            success(JSONObject()),
        )
        val session = OpenWrtAdapter(transport).connect(
            OpenWrtEndpoint.parse("https://router.local", allowCleartext = false),
            RouterCredentials("root", "test-password"),
        )
        val preview = session.previewWirelessSsid("cfg-ap", "New network")

        val result = session.applyWirelessSsid(preview)

        assertEquals(WirelessConfigState.ROLLED_BACK, result.state)
        val methods = transport.requests.filter { it.getString("method") == "call" }
            .map { it.getJSONArray("params").getString(2) }
        assertTrue(methods.contains("rollback"))
        assertFalse(methods.contains("confirm"))
    }

    private fun accessControlCapabilities(): JSONObject =
        JSONObject()
            .put("uci", methods("get", "add", "delete", "order", "changes", "revert", "apply", "confirm", "rollback"))

    private fun wirelessWriteCapabilities(): JSONObject =
        JSONObject()
            .put("uci", methods("get", "set", "changes", "revert", "apply", "confirm", "rollback"))

    private fun wirelessConfig(ssid: String, mode: String = "ap"): JSONObject =
        JSONObject().put(
            "values",
            JSONObject()
                .put(
                    "cfg-radio",
                    JSONObject().put(".type", "wifi-device").put(".name", "radio0")
                        .put("channel", "11"),
                )
                .put(
                    "cfg-ap",
                    JSONObject().put(".type", "wifi-iface").put(".name", "cfg-ap")
                        .put("mode", mode).put("ssid", ssid).put("device", "radio0")
                        .put("encryption", "psk2"),
                ),
        )

    private fun firewallConfig(
        rule: JSONObject? = null,
        zones: List<String> = listOf("lan", "wan"),
    ): JSONObject {
        val values = JSONObject()
        zones.forEachIndexed { index, zone ->
            values.put(
                "cfg${index + 1}",
                JSONObject()
                    .put(".type", "zone")
                    .put(".name", "cfg${index + 1}")
                    .put("name", zone)
                    .put("network", JSONArray().put(zone)),
            )
        }
        values.put(
            "cfg-forward",
            JSONObject()
                .put(".type", "forwarding")
                .put(".name", "cfg-forward")
                .put("src", "lan")
                .put("dest", "wan"),
        )
        rule?.let { values.put(it.optString(".name"), it) }
        return JSONObject().put("values", values)
    }

    private fun methods(vararg names: String): JSONObject =
        names.fold(JSONObject()) { result, name -> result.put(name, JSONObject()) }

    private fun success(payload: JSONObject): JSONObject =
        JSONObject().put("result", JSONArray().put(0).put(payload))

    private fun failure(ubusStatus: Int): JSONObject =
        JSONObject().put("result", JSONArray().put(ubusStatus))

    private class ResponseQueue(vararg responses: JSONObject) : UbusTransport {
        private val remaining = ArrayDeque(responses.toList())
        val requests = mutableListOf<JSONObject>()

        override fun post(endpoint: OpenWrtEndpoint, body: JSONObject): JSONObject {
            requests += body
            return remaining.removeFirst().put("jsonrpc", "2.0").put("id", body.getInt("id"))
        }
    }
}
