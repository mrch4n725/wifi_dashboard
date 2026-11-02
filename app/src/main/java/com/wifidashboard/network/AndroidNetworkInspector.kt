package com.wifidashboard.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.DhcpInfo
import android.net.LinkProperties
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.text.format.Formatter
import android.net.TrafficStats
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

class AndroidNetworkInspector(context: Context) {
    private val appContext = context.applicationContext
    private val connectivityManager =
        appContext.getSystemService(ConnectivityManager::class.java)
    private val wifiManager = appContext.applicationContext
        .getSystemService(Context.WIFI_SERVICE) as WifiManager

    fun inspect(): NetworkSnapshot {
        val now = System.currentTimeMillis()
        val network = connectivityManager.activeNetwork
        val capabilities = network?.let(connectivityManager::getNetworkCapabilities)
        val linkProperties = network?.let(connectivityManager::getLinkProperties)
        val wifiInfo = getWifiInfo(capabilities)
        val isConnected = network != null && capabilities != null
        val frequency = wifiInfo?.frequency?.takeIf { it > 0 }

        return NetworkSnapshot(
            capturedAtMillis = now,
            connectionState = when {
                network == null -> "Offline"
                capabilities == null -> "Connected · details unavailable"
                else -> "Connected"
            },
            transport = capabilities?.let(::transportName),
            interfaceName = linkProperties?.interfaceName,
            ssid = wifiInfo?.ssid?.takeUnless { it == "<unknown ssid>" || it.isBlank() },
            bssid = wifiInfo?.bssid?.takeUnless { it.isNullOrBlank() || it == "02:00:00:00:00:00" },
            security = wifiInfo?.let(::securityName),
            frequencyMhz = frequency,
            channel = frequency?.let(WifiChannel::fromFrequencyMhz),
            band = frequency?.let(WifiChannel::bandFromFrequencyMhz),
            rssiDbm = wifiInfo?.rssi?.takeIf { it in -126..0 },
            linkSpeedMbps = wifiInfo?.linkSpeed?.takeIf { it > 0 },
            rxLinkSpeedMbps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                wifiInfo?.rxLinkSpeedMbps?.takeIf { it > 0 }
            } else {
                null
            },
            txLinkSpeedMbps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                wifiInfo?.txLinkSpeedMbps?.takeIf { it > 0 }
            } else {
                null
            },
            ipv4Addresses = linkProperties.addresses<Inet4Address>(),
            ipv6Addresses = linkProperties.addresses<Inet6Address>(),
            prefixes = linkProperties.prefixes(),
            gateways = linkProperties.defaultGateways(),
            dnsServers = linkProperties?.dnsServers.orEmpty().mapNotNull { it.hostAddress },
            dhcpServer = if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                getDhcpServer()
            } else {
                null
            },
            systemRxBytes = TrafficStats.getTotalRxBytes().takeIf { it >= 0 },
            systemTxBytes = TrafficStats.getTotalTxBytes().takeIf { it >= 0 },
        )
    }

    private fun getWifiInfo(capabilities: NetworkCapabilities?): WifiInfo? {
        if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) != true) {
            return null
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return capabilities.transportInfo as? WifiInfo
        }
        @Suppress("DEPRECATION")
        return wifiManager.connectionInfo
    }

    private fun transportName(capabilities: NetworkCapabilities): String =
        when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "Bluetooth"
            else -> "Other"
        }

    private fun securityName(info: WifiInfo): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        return when (info.currentSecurityType) {
            WifiInfo.SECURITY_TYPE_OPEN -> "Open"
            WifiInfo.SECURITY_TYPE_WEP -> "WEP"
            WifiInfo.SECURITY_TYPE_PSK -> "WPA/WPA2-Personal"
            WifiInfo.SECURITY_TYPE_EAP -> "WPA/WPA2-Enterprise"
            WifiInfo.SECURITY_TYPE_SAE -> "WPA3-Personal"
            WifiInfo.SECURITY_TYPE_OWE -> "Enhanced Open"
            WifiInfo.SECURITY_TYPE_EAP_WPA3_ENTERPRISE -> "WPA3-Enterprise"
            WifiInfo.SECURITY_TYPE_EAP_WPA3_ENTERPRISE_192_BIT -> "WPA3-Enterprise 192-bit"
            WifiInfo.SECURITY_TYPE_WAPI_PSK -> "WAPI-PSK"
            WifiInfo.SECURITY_TYPE_WAPI_CERT -> "WAPI-CERT"
            else -> "Unknown"
        }
    }

    private fun getDhcpServer(): String? {
        @Suppress("DEPRECATION")
        val dhcpInfo: DhcpInfo? = wifiManager.dhcpInfo
        val serverAddress = dhcpInfo?.serverAddress ?: return null
        return serverAddress.takeIf { it != 0 }?.let {
            @Suppress("DEPRECATION")
            Formatter.formatIpAddress(it)
        }
    }

    private inline fun <reified T : InetAddress> LinkProperties?.addresses(): List<String> =
        this?.linkAddresses.orEmpty()
            .map { it.address }
            .filterIsInstance<T>()
            .filterNot(InetAddress::isLoopbackAddress)
            .mapNotNull { it.hostAddress }

    private fun LinkProperties?.prefixes(): List<String> =
        this?.linkAddresses.orEmpty()
            .filterNot { it.address.isLoopbackAddress }
            .mapNotNull { address ->
                address.address.hostAddress?.let { "$it/${address.prefixLength}" }
            }

    private fun LinkProperties?.defaultGateways(): List<String> =
        this?.routes.orEmpty()
            .filter { it.isDefaultRoute }
            .mapNotNull { it.gateway?.hostAddress }
            .distinct()
}
