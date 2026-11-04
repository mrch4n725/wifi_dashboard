package com.wifidashboard.discovery

import android.content.Context
import android.net.wifi.WifiManager
import com.wifidashboard.storage.DeviceObservation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

class SsdpDiscovery(context: Context) {
    private val wifiManager = context.applicationContext.getSystemService(WifiManager::class.java)

    fun scan(
        subnet: IPv4Subnet,
        interfaceName: String?,
        durationMillis: Long = DEFAULT_SCAN_DURATION_MILLIS,
    ): Flow<DiscoveryEvent> = channelFlow {
        require(durationMillis in MIN_SCAN_DURATION_MILLIS..MAX_SCAN_DURATION_MILLIS) {
            "SSDP scan duration must be between 500 ms and 5 seconds"
        }
        val lock = wifiManager?.createMulticastLock("wifi-dashboard-ssdp")
        lock?.setReferenceCounted(false)
        lock?.acquire()
        try {
            withContext(Dispatchers.IO) {
                scanBlocking(subnet, interfaceName, durationMillis) { event -> send(event) }
            }
        } finally {
            if (lock?.isHeld == true) lock.release()
        }
    }

    private suspend fun scanBlocking(
        subnet: IPv4Subnet,
        interfaceName: String?,
        durationMillis: Long,
        emitEvent: suspend (DiscoveryEvent) -> Unit,
    ) {
        if (interfaceName.isNullOrBlank()) {
            throw IOException("Android did not expose a network interface for local SSDP discovery")
        }
        val multicastAddress = InetAddress.getByName(SSDP_MULTICAST_ADDRESS)
        val networkInterface = NetworkInterface.getByName(interfaceName)
        if (networkInterface == null) {
            throw IOException("Android network interface $interfaceName is unavailable for SSDP")
        }
        MulticastSocket(null).use { socket ->
            socket.bind(InetSocketAddress(0))
            socket.soTimeout = RECEIVE_POLL_MILLIS
            socket.networkInterface = networkInterface
            socket.joinGroup(InetSocketAddress(multicastAddress, SSDP_PORT), networkInterface)
            try {
                SEARCH_TARGETS.forEach { target ->
                    val search = buildString {
                        append("M-SEARCH * HTTP/1.1\r\n")
                        append("HOST: $SSDP_MULTICAST_ADDRESS:$SSDP_PORT\r\n")
                        append("MAN: \"ssdp:discover\"\r\n")
                        append("MX: 1\r\n")
                        append("ST: $target\r\n\r\n")
                    }.toByteArray(Charsets.US_ASCII)
                    socket.send(
                        DatagramPacket(
                            search,
                            search.size,
                            multicastAddress,
                            SSDP_PORT,
                        ),
                    )
                }

                val deadline = System.nanoTime() + durationMillis * NANOS_PER_MILLI
                val buffer = ByteArray(MAX_RESPONSE_BYTES)
                while (System.nanoTime() < deadline) {
                    currentCoroutineContext().ensureActive()
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(packet)
                    } catch (_: java.net.SocketTimeoutException) {
                        continue
                    }
                    val sourceIp = packet.address.hostAddress?.substringBefore('%') ?: continue
                    if (!subnet.contains(sourceIp)) continue
                    val response = String(packet.data, packet.offset, packet.length, Charsets.US_ASCII)
                    val observation = parseResponse(response, sourceIp) ?: continue
                    emitEvent(DiscoveryEvent.Found(observation))
                }
            } finally {
                socket.leaveGroup(InetSocketAddress(multicastAddress, SSDP_PORT), networkInterface)
            }
        }
    }

    companion object {
        const val SOURCE = "SSDP/UPnP"
        const val DEFAULT_SCAN_DURATION_MILLIS = 2_500L
        const val MIN_SCAN_DURATION_MILLIS = 500L
        const val MAX_SCAN_DURATION_MILLIS = 5_000L
        private const val SSDP_MULTICAST_ADDRESS = "239.255.255.250"
        private const val SSDP_PORT = 1900
        private const val RECEIVE_POLL_MILLIS = 250
        private const val MAX_RESPONSE_BYTES = 8 * 1024
        private const val MAX_HEADER_VALUE_LENGTH = 160
        private const val NANOS_PER_MILLI = 1_000_000L
        private val SEARCH_TARGETS = listOf(
            "ssdp:all",
            "upnp:rootdevice",
            "urn:schemas-upnp-org:device:MediaServer:1",
            "urn:schemas-upnp-org:device:MediaRenderer:1",
        )
        private val SKY_Q_PATTERN = Regex("(?i)sky\\s*q|skyq")

        internal fun parseResponse(response: String, sourceIp: String): DeviceObservation? {
            val lines = response.lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
            if (lines.firstOrNull()?.matches(Regex("(?i)HTTP/1\\.[01] 200(?: .*)?")) != true) {
                return null
            }
            val headers = lines.drop(1).mapNotNull { line ->
                val separator = line.indexOf(':')
                if (separator <= 0) null
                else line.substring(0, separator).trim().lowercase(Locale.ROOT) to
                    line.substring(separator + 1).trim()
            }.toMap()
            val server = headers["server"].orEmpty().take(MAX_HEADER_VALUE_LENGTH)
            val searchTarget = (headers["st"] ?: headers["nt"]).orEmpty().take(MAX_HEADER_VALUE_LENGTH)
            val locationHost = headers["location"]?.let(::locationHost)
            val identifiedSkyQ =
                SKY_Q_PATTERN.containsMatchIn("$server $searchTarget ${headers["usn"].orEmpty()}")
            val serviceName = when {
                identifiedSkyQ -> "Sky Q (SSDP/UPnP)"
                searchTarget.isNotBlank() -> "UPnP $searchTarget"
                else -> "UPnP device"
            }
            val details = listOfNotNull(
                "SSDP/UPnP response",
                server.takeIf(String::isNotBlank)?.let { "Server $it" },
                locationHost?.let { "Description host $it" },
            ).joinToString(" · ")
            return DeviceObservation(
                source = SOURCE,
                observedAt = System.currentTimeMillis(),
                ipAddress = sourceIp,
                serviceName = serviceName,
                detail = details,
            )
        }

        private fun locationHost(value: String): String? =
            try {
                URI(value).host?.take(MAX_HEADER_VALUE_LENGTH)
            } catch (_: URISyntaxException) {
                null
            }
    }
}
