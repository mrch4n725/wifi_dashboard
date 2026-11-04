package com.wifidashboard.discovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SsdpDiscoveryTest {
    @Test
    fun identifiesSkyQFromSsdpHeaders() {
        val observation = SsdpDiscovery.parseResponse(
            """
            HTTP/1.1 200 OK
            ST: urn:schemas-upnp-org:device:MediaRenderer:1
            SERVER: Linux/3.x UPnP/1.0 SkyQ
            LOCATION: http://192.168.1.20:49152/device.xml
            """.trimIndent(),
            "192.168.1.20",
        )

        assertEquals("Sky Q (SSDP/UPnP)", observation?.serviceName)
        assertEquals("192.168.1.20", observation?.ipAddress)
        assertEquals("SSDP/UPnP", observation?.source)
    }

    @Test
    fun keepsGenericUpnpDeviceWithoutClaimingSkyQ() {
        val observation = SsdpDiscovery.parseResponse(
            """
            HTTP/1.1 200 OK
            ST: upnp:rootdevice
            SERVER: Linux/6.1 UPnP/1.1 GenericDevice/1
            """.trimIndent(),
            "192.168.1.30",
        )

        assertEquals("UPnP upnp:rootdevice", observation?.serviceName)
    }

    @Test
    fun ignoresNonSuccessAndMalformedResponses() {
        assertNull(SsdpDiscovery.parseResponse("HTTP/1.1 404 Not Found", "192.168.1.3"))
        assertNull(SsdpDiscovery.parseResponse("not an SSDP response", "192.168.1.3"))
    }
}
