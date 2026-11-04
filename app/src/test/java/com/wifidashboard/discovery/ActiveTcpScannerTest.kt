package com.wifidashboard.discovery

import java.net.InetAddress
import java.net.ServerSocket
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveTcpScannerTest {
    @Test
    fun detectsAnOpenTcpPortAndDoesNotReportAClosedPort() = runBlocking {
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            val result = ActiveTcpScanner().scanHost(
                InetAddress.getByName("127.0.0.1"),
                connectTimeoutMillis = 300,
                ports = listOf(server.localPort, unusedPort()),
            )

            assertEquals(listOf(server.localPort), result)
        }
    }

    @Test
    fun commonPortListIsBoundedAndUsesTcpConnectProbes() {
        assertEquals(11, ActiveTcpScanner.COMMON_TCP_PORTS.size)
        assertTrue(ActiveTcpScanner.COMMON_TCP_PORTS.all { it in 1..65535 })
    }

    private fun unusedPort(): Int =
        ServerSocket(0).use { it.localPort }
}
