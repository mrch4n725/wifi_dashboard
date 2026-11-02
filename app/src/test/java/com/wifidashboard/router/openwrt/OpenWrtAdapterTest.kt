package com.wifidashboard.router.openwrt

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
                    JSONArray().put(
                        JSONObject().put(
                            "leases",
                            JSONArray().put(
                                JSONObject()
                                    .put("hostname", "printer")
                                    .put("ipaddr", "192.168.1.42")
                                    .put("macaddr", "aa:bb:cc:dd:ee:ff")
                                    .put("expires", 300),
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
