package com.wifidashboard.discovery

import java.net.InetAddress

class IPv4Subnet private constructor(
    val cidr: String,
    private val network: Long,
    private val prefixLength: Int,
    private val hostCount: Int,
) {
    fun contains(address: String): Boolean {
        val parsed = parseAddress(address) ?: return false
        val mask = maskFor(prefixLength)
        return parsed and mask == network
    }

    fun hostAddresses(): Sequence<InetAddress> = sequence {
        for (offset in 1 until hostCount + 1) {
            yield(InetAddress.getByAddress(toBytes(network + offset)))
        }
    }

    companion object {
        const val MAX_HOSTS = 1_024

        fun forLocalAddress(cidr: String, localAddress: String): IPv4Subnet {
            val parts = cidr.trim().split('/')
            require(parts.size == 2) { "Select an IPv4 subnet in address/prefix form" }
            val address = parseAddress(parts[0])
                ?: throw IllegalArgumentException("The selected subnet must use a valid IPv4 address")
            val prefix = parts[1].toIntOrNull()
                ?: throw IllegalArgumentException("The selected subnet prefix is invalid")
            require(prefix in 8..30) { "IPv4 discovery requires a prefix between /8 and /30" }

            val local = parseAddress(localAddress)
                ?: throw IllegalArgumentException("Android did not provide a usable local IPv4 address")
            val mask = maskFor(prefix)
            val network = address and mask
            val broadcast = network or (0xffff_ffffL xor mask)
            require(local and mask == network) {
                "The selected subnet does not contain this Android device"
            }
            require(isPrivateIpv4(network) && isPrivateIpv4(broadcast)) {
                "Discovery is limited to private or link-local IPv4 networks"
            }

            val hostCount = (broadcast - network - 1).toInt()
            require(hostCount in 1..MAX_HOSTS) {
                "This subnet has $hostCount host addresses; discovery is limited to $MAX_HOSTS"
            }
            return IPv4Subnet("${formatAddress(network)}/$prefix", network, prefix, hostCount)
        }

        private fun maskFor(prefix: Int): Long =
            (0xffff_ffffL shl (32 - prefix)) and 0xffff_ffffL

        private fun isPrivateIpv4(address: Long): Boolean {
            val first = ((address ushr 24) and 0xff).toInt()
            val second = ((address ushr 16) and 0xff).toInt()
            return first == 10 ||
                (first == 172 && second in 16..31) ||
                (first == 192 && second == 168) ||
                (first == 169 && second == 254)
        }

        private fun parseAddress(value: String): Long? {
            val octets = value.trim().split('.')
            if (octets.size != 4) return null
            var address = 0L
            for (octet in octets) {
                if (octet.isEmpty() || (octet.length > 1 && octet.startsWith('0'))) return null
                val number = octet.toIntOrNull()?.takeIf { it in 0..255 } ?: return null
                address = (address shl 8) or number.toLong()
            }
            return address
        }

        private fun toBytes(address: Long): ByteArray = byteArrayOf(
            (address ushr 24).toByte(),
            (address ushr 16).toByte(),
            (address ushr 8).toByte(),
            address.toByte(),
        )

        private fun formatAddress(address: Long): String =
            (0..3).joinToString(".") { shift ->
                ((address ushr (24 - shift * 8)) and 0xff).toString()
            }
    }
}
