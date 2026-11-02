package com.wifidashboard.discovery

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class NeighborTableReaderTest {
    @Test
    fun readsOnlyCompleteUnicastNeighborEntries() {
        val table = File.createTempFile("wifi-dashboard-arp", ".txt")
        try {
            table.writeText(
                """
                IP address       HW type     Flags       HW address            Mask     Device
                192.168.1.2      0x1         0x2         aa:bb:cc:dd:ee:ff     *        wlan0
                192.168.1.3      0x1         0x0         11:22:33:44:55:66     *        wlan0
                192.168.1.4      0x1         0x2         00:00:00:00:00:00     *        wlan0
                999.1.1.1        0x1         0x2         11:22:33:44:55:66     *        wlan0
                192.168.1.5      0x1         0x2         11:22:33:44:55:77     *        eth0
                """.trimIndent(),
            )

            val observations = NeighborTableReader(table).read(interfaceName = "wlan0", observedAt = 123L)

            assertEquals(1, observations.size)
            assertEquals("192.168.1.2", observations.single().ipAddress)
            assertEquals("aa:bb:cc:dd:ee:ff", observations.single().macAddress)
            assertEquals(123L, observations.single().observedAt)
            assertEquals("Cached neighbor on interface wlan0", observations.single().detail)
        } finally {
            table.delete()
        }
    }
}
