package com.wifidashboard.ui.format

import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayFormatTest {
    @Test
    fun formatsByteCountersWithoutMislabeling() {
        assertEquals("0 B", formatBytes(0))
        assertEquals("1023 B", formatBytes(1023))
        assertEquals("1.0 KB", formatBytes(1024))
        assertEquals("1.0 MB", formatBytes(1024 * 1024))
    }

    @Test
    fun marksUnavailableCounters() {
        assertEquals("Unavailable", formatBytes(null))
        assertEquals("Unavailable", formatBytes(-1))
    }
}
