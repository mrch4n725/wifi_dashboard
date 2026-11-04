package com.wifidashboard.discovery

import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MdnsObservationParserTest {
    @Test
    fun recordsAdvertisedServiceWithItsHostAndPort() {
        val observation = MdnsObservationParser.parse(
            serviceType = "_ipp._tcp.",
            serviceName = "Office printer",
            addresses = listOf(InetAddress.getByName("192.168.1.50")),
            hostname = "printer.local.",
            port = 631,
            observedAt = 123L,
        )

        assertEquals("mDNS _ipp._tcp.", observation.source)
        assertEquals("Office printer", observation.serviceName)
        assertEquals("printer.local.", observation.hostname)
        assertEquals("192.168.1.50", observation.ipAddress)
        assertEquals("Advertised on port 631", observation.detail)
        assertEquals(123L, observation.observedAt)
    }

    @Test
    fun doesNotUseLoopbackLinkLocalOrMulticastAddressesAsDeviceIdentity() {
        val observation = MdnsObservationParser.parse(
            serviceType = "_http._tcp.",
            serviceName = "test",
            addresses = listOf(
                InetAddress.getByName("127.0.0.1"),
                InetAddress.getByName("fe80::1"),
                InetAddress.getByName("224.0.0.1"),
            ),
            hostname = null,
            port = -1,
        )

        assertNull(observation.ipAddress)
        assertNull(observation.detail)
    }
}
