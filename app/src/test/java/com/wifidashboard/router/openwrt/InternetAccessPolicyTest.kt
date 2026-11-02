package com.wifidashboard.router.openwrt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InternetAccessPolicyTest {
    @Test
    fun policyUsesStableAppOwnedSectionAndExactDeviceMac() {
        val policy = InternetAccessPolicy.forDevice("AA:BB:CC:DD:EE:FF", "lan")

        assertTrue(policy.sectionName.matches(Regex("wifi_dashboard_[0-9a-f]{16}")))
        assertEquals("aa:bb:cc:dd:ee:ff", policy.macAddress)
        assertEquals("lan", policy.sourceZone)
        assertEquals("wan", policy.destinationZone)
        assertEquals("REJECT", policy.target)
    }

    @Test
    fun policyRejectsMissingOrMalformedMacInsteadOfFallingBackToIp() {
        assertTrue(runCatching { InternetAccessPolicy.forDevice("192.168.1.20", "lan") }
            .exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { InternetAccessPolicy.forDevice("00:00:00:00:00:00", "lan") }
            .exceptionOrNull() is IllegalArgumentException)
    }
}
