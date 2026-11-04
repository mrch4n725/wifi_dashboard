package com.wifidashboard.discovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class IPv4SubnetTest {
    @Test
    fun createsBoundedPrivateSubnetAndExcludesNetworkAndBroadcastAddresses() {
        val subnet = IPv4Subnet.forLocalAddress("192.168.10.17/29", "192.168.10.17")

        assertEquals("192.168.10.16/29", subnet.cidr)
        val hosts = subnet.hostAddresses().map { it.hostAddress }.toList()
        assertEquals(6, hosts.size)
        assertFalse(hosts.contains("192.168.10.16"))
        assertFalse(hosts.contains("192.168.10.23"))
        assertTrue(subnet.contains("192.168.10.22"))
    }

    @Test
    fun refusesExternalWrongLocalOversizedAndUnsupportedSubnets() {
        assertThrows(IllegalArgumentException::class.java) {
            IPv4Subnet.forLocalAddress("8.8.8.0/24", "8.8.8.8")
        }
        assertThrows(IllegalArgumentException::class.java) {
            IPv4Subnet.forLocalAddress("192.168.2.0/24", "192.168.1.5")
        }
        assertThrows(IllegalArgumentException::class.java) {
            IPv4Subnet.forLocalAddress("10.0.0.0/16", "10.0.1.2")
        }
        assertThrows(IllegalArgumentException::class.java) {
            IPv4Subnet.forLocalAddress("192.168.1.0/31", "192.168.1.1")
        }
    }
}
