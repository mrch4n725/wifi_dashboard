package com.wifidashboard.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DeviceIdentityTest {
    @Test
    fun usesNormalizedMacAsHighConfidenceIdentity() {
        val identity = DeviceIdentity.from(
            DeviceObservation(
                source = "test",
                observedAt = 1L,
                macAddress = "AA-BB-CC-DD-EE-FF",
                hostname = "Kitchen.local.",
                ipAddress = "192.168.1.20",
            ),
        )

        assertEquals("mac:aa:bb:cc:dd:ee:ff", identity.key)
        assertEquals(1f, identity.confidence, 0f)
    }

    @Test
    fun fallsBackToHostnameAndThenIpWithLowerConfidence() {
        val hostnameIdentity = DeviceIdentity.from(
            DeviceObservation("mDNS", 1L, ipAddress = "192.168.1.21", hostname = "Printer.local."),
        )
        val ipIdentity = DeviceIdentity.from(
            DeviceObservation("neighbor table", 1L, ipAddress = "192.168.1.22"),
        )

        assertEquals("host:printer.local", hostnameIdentity.key)
        assertEquals(0.65f, hostnameIdentity.confidence, 0f)
        assertEquals("ip:192.168.1.22", ipIdentity.key)
        assertEquals(0.3f, ipIdentity.confidence, 0f)
    }

    @Test
    fun rejectsObservationsWithoutAnyUsableIdentity() {
        assertThrows(IllegalArgumentException::class.java) {
            DeviceIdentity.from(DeviceObservation("mDNS", 1L))
        }
        assertThrows(IllegalArgumentException::class.java) {
            DeviceIdentity.from(
                DeviceObservation(
                    source = "neighbor table",
                    observedAt = 1L,
                    macAddress = "00:00:00:00:00:00",
                ),
            )
        }
    }
}
