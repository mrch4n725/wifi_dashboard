package com.wifidashboard.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WifiChannelTest {
    @Test
    fun mapsCommonWifiFrequenciesToChannels() {
        assertEquals(1, WifiChannel.fromFrequencyMhz(2412))
        assertEquals(14, WifiChannel.fromFrequencyMhz(2484))
        assertEquals(36, WifiChannel.fromFrequencyMhz(5180))
        assertEquals(1, WifiChannel.fromFrequencyMhz(5955))
    }

    @Test
    fun identifiesWifiBands() {
        assertEquals("2.4 GHz", WifiChannel.bandFromFrequencyMhz(2437))
        assertEquals("5 GHz", WifiChannel.bandFromFrequencyMhz(5500))
        assertEquals("6 GHz", WifiChannel.bandFromFrequencyMhz(6115))
        assertNull(WifiChannel.bandFromFrequencyMhz(900))
    }

    @Test
    fun unsupportedFrequencyHasNoChannel() {
        assertNull(WifiChannel.fromFrequencyMhz(900))
        assertNull(WifiChannel.fromFrequencyMhz(5000))
    }
}
