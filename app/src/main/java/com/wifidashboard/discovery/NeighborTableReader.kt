package com.wifidashboard.discovery

import com.wifidashboard.storage.DeviceObservation
import java.io.File
import java.io.IOException

class NeighborTableReader(
    private val table: File = File("/proc/net/arp"),
) {
    @Throws(IOException::class)
    fun read(
        interfaceName: String? = null,
        observedAt: Long = System.currentTimeMillis(),
    ): List<DeviceObservation> {
        if (!table.exists() || !table.canRead()) {
            throw IOException("Android's neighbor table is unavailable")
        }
        return table.useLines { lines ->
            lines.drop(1).mapNotNull { line ->
                val fields = line.trim().split(Regex("\\s+"))
                if (fields.size < 6 || fields[2] != "0x2") return@mapNotNull null
                if (interfaceName != null && fields[5] != interfaceName) return@mapNotNull null
                val ip = fields[0].takeIf(::isIpv4Address) ?: return@mapNotNull null
                val mac = fields[3].takeIf(::isUsableMac) ?: return@mapNotNull null
                DeviceObservation(
                    source = SOURCE,
                    observedAt = observedAt,
                    ipAddress = ip,
                    macAddress = mac,
                    detail = "Cached neighbor on interface ${fields[5]}",
                )
            }.toList()
        }
    }

    private fun isIpv4Address(value: String): Boolean {
        val parts = value.split('.')
        return parts.size == 4 && parts.all { part ->
            part.toIntOrNull()?.let { it in 0..255 && (part == "0" || !part.startsWith('0')) } == true
        }
    }

    private fun isUsableMac(value: String): Boolean =
        value.matches(Regex("(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}")) &&
            !value.equals("00:00:00:00:00:00", ignoreCase = true) &&
            !value.equals("ff:ff:ff:ff:ff:ff", ignoreCase = true)

    companion object {
        const val SOURCE = "Android neighbor table"
    }
}
