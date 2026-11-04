package com.wifidashboard.discovery

import com.wifidashboard.storage.DeviceObservation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.SocketFactory
import kotlin.coroutines.coroutineContext

class ActiveTcpScanner {
    fun scan(
        subnet: IPv4Subnet,
        concurrency: Int = DEFAULT_CONCURRENCY,
        connectTimeoutMillis: Int = DEFAULT_CONNECT_TIMEOUT_MILLIS,
        maxDurationMillis: Long = DEFAULT_MAX_DURATION_MILLIS,
        socketFactory: SocketFactory = SocketFactory.getDefault(),
    ): Flow<DiscoveryEvent> = channelFlow {
        require(concurrency in 1..MAX_CONCURRENCY) {
            "Scan concurrency must be between 1 and $MAX_CONCURRENCY"
        }
        require(connectTimeoutMillis in MIN_CONNECT_TIMEOUT_MILLIS..MAX_CONNECT_TIMEOUT_MILLIS) {
            "TCP timeout must be between $MIN_CONNECT_TIMEOUT_MILLIS and $MAX_CONNECT_TIMEOUT_MILLIS ms"
        }
        require(maxDurationMillis in MIN_MAX_DURATION_MILLIS..MAX_MAX_DURATION_MILLIS) {
            "Active scan duration must be between $MIN_MAX_DURATION_MILLIS and $MAX_MAX_DURATION_MILLIS ms"
        }

        val targets = subnet.hostAddresses().toList()
        val queue = Channel<InetAddress>(Channel.UNLIMITED)
        targets.forEach(queue::trySend)
        queue.close()
        val completed = java.util.concurrent.atomic.AtomicInteger()
        send(DiscoveryEvent.Progress(completed = 0, total = targets.size))

        val finished = withTimeoutOrNull(maxDurationMillis) {
            coroutineScope {
                repeat(concurrency) {
                    launch(Dispatchers.IO) {
                        for (address in queue) {
                            coroutineContext.ensureActive()
                            val openPorts = scanHost(address, connectTimeoutMillis, socketFactory = socketFactory)
                            if (openPorts.isNotEmpty()) {
                                val names = openPorts.map { port ->
                                    "$port/${SERVICE_NAMES[port] ?: "TCP"}"
                                }
                                send(
                                    DiscoveryEvent.Found(
                                        DeviceObservation(
                                            source = SOURCE,
                                            observedAt = System.currentTimeMillis(),
                                            ipAddress = address.hostAddress,
                                            serviceName = names.joinToString(),
                                            detail = "Open TCP ports: ${openPorts.joinToString()}",
                                        ),
                                    ),
                                )
                            }
                            send(
                                DiscoveryEvent.Progress(
                                    completed = completed.incrementAndGet(),
                                    total = targets.size,
                                ),
                            )
                        }
                    }
                }
            }
            true
        } ?: false
        if (!finished) {
            send(
                DiscoveryEvent.Warning(
                    "Active TCP scan reached its time limit after ${completed.get()} of ${targets.size} hosts; remaining hosts were not tested.",
                ),
            )
        }
    }

    internal suspend fun scanHost(
        address: InetAddress,
        connectTimeoutMillis: Int,
        ports: List<Int> = COMMON_TCP_PORTS,
        socketFactory: SocketFactory = SocketFactory.getDefault(),
    ): List<Int> {
        val openPorts = mutableListOf<Int>()
        for (port in ports) {
            coroutineContext.ensureActive()
            val isOpen = try {
                socketFactory.createSocket().use { socket ->
                    socket.connect(InetSocketAddress(address, port), connectTimeoutMillis)
                    true
                }
            } catch (_: IOException) {
                false
            }
            if (isOpen) openPorts += port
        }
        return openPorts
    }

    companion object {
        const val SOURCE = "Active TCP scan"
        const val DEFAULT_CONCURRENCY = 48
        const val MAX_CONCURRENCY = 64
        const val MIN_CONNECT_TIMEOUT_MILLIS = 100
        const val DEFAULT_CONNECT_TIMEOUT_MILLIS = 300
        const val MAX_CONNECT_TIMEOUT_MILLIS = 1_000
        const val DEFAULT_MAX_DURATION_MILLIS = 30_000L
        const val MIN_MAX_DURATION_MILLIS = 5_000L
        const val MAX_MAX_DURATION_MILLIS = 60_000L
        val COMMON_TCP_PORTS = listOf(22, 53, 80, 443, 445, 554, 631, 8008, 8080, 8443, 32400)

        private val SERVICE_NAMES = mapOf(
            22 to "SSH",
            53 to "DNS",
            80 to "HTTP",
            443 to "HTTPS",
            445 to "SMB",
            554 to "RTSP",
            631 to "IPP",
            8008 to "HTTP-alt",
            8080 to "HTTP-alt",
            8443 to "HTTPS-alt",
            32400 to "Plex",
        )
    }
}
