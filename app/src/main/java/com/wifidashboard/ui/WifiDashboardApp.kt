package com.wifidashboard.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import com.wifidashboard.network.AndroidNetworkInspector
import com.wifidashboard.network.NetworkSnapshot
import com.wifidashboard.ui.format.formatBytes
import com.wifidashboard.ui.format.orUnavailable
import kotlinx.coroutines.delay

private enum class Destination(val title: String) {
    Dashboard("Dashboard"),
    Wifi("Wi-Fi"),
    Settings("Settings"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiDashboardApp() {
    val context = LocalContext.current
    val inspector = remember(context) { AndroidNetworkInspector(context) }
    var snapshot by remember { mutableStateOf<NetworkSnapshot?>(null) }
    var destination by remember { mutableStateOf(Destination.Dashboard) }
    var permissionGranted by remember(context) {
        mutableStateOf(hasWifiDetailsPermission(context))
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        permissionGranted = hasWifiDetailsPermission(context)
        snapshot = inspector.inspect()
    }

    fun refresh() {
        snapshot = inspector.inspect()
        permissionGranted = hasWifiDetailsPermission(context)
    }

    LaunchedEffect(Unit) {
        refresh()
        while (true) {
            delay(15_000)
            refresh()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.navigationBars,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "WIFI DASHBOARD",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = destination.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                },
                actions = {
                    TextButton(onClick = ::refresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh network information")
                        Spacer(Modifier.width(6.dp))
                        Text("Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                Destination.entries.forEach { item ->
                    val icon = when (item) {
                        Destination.Dashboard -> Icons.Default.Dashboard
                        Destination.Wifi -> Icons.Default.Wifi
                        Destination.Settings -> Icons.Default.Settings
                    }
                    NavigationBarItem(
                        selected = destination == item,
                        onClick = { destination = item },
                        icon = { Icon(icon, contentDescription = item.title) },
                        label = { Text(item.title) },
                    )
                }
            }
        },
    ) { padding ->
        when (destination) {
            Destination.Dashboard -> DashboardScreen(
                snapshot = snapshot,
                permissionGranted = permissionGranted,
                onRequestPermission = {
                    permissionLauncher.launch(requiredWifiPermissions())
                },
                onOpenWifi = { destination = Destination.Wifi },
                modifier = Modifier.padding(padding),
            )
            Destination.Wifi -> WifiScreen(
                snapshot = snapshot,
                permissionGranted = permissionGranted,
                onRequestPermission = {
                    permissionLauncher.launch(requiredWifiPermissions())
                },
                modifier = Modifier.padding(padding),
            )
            Destination.Settings -> SettingsScreen(
                permissionGranted = permissionGranted,
                onRequestPermission = {
                    permissionLauncher.launch(requiredWifiPermissions())
                },
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun DashboardScreen(
    snapshot: NetworkSnapshot?,
    permissionGranted: Boolean,
    onRequestPermission: () -> Unit,
    onOpenWifi: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            ConnectionCard(snapshot)
        }
        if (!permissionGranted) {
            item {
                PermissionCard(onRequestPermission)
            }
        }
        item {
            SectionHeading("CONNECTION SNAPSHOT", "Values from Android's active network")
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard(
                    label = "SIGNAL",
                    value = snapshot?.rssiDbm?.let { "$it dBm" } ?: "—",
                    supporting = "Wi-Fi radio",
                    modifier = Modifier.weight(1f),
                )
                MetricCard(
                    label = "LINK SPEED",
                    value = snapshot?.linkSpeedMbps?.let { "$it Mbps" } ?: "—",
                    supporting = "Reported by Android",
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard(
                    label = "SYSTEM RX",
                    value = formatBytes(snapshot?.systemRxBytes),
                    supporting = "Device total since boot",
                    modifier = Modifier.weight(1f),
                )
                MetricCard(
                    label = "SYSTEM TX",
                    value = formatBytes(snapshot?.systemTxBytes),
                    supporting = "Device total since boot",
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item {
            DetailCard(
                title = "Network path",
                rows = listOf(
                    "Transport" to snapshot?.transport.orUnavailable(),
                    "Interface" to snapshot?.interfaceName.orUnavailable(),
                    "Gateway" to snapshot?.gateways?.joinToString().orUnavailable(),
                    "DNS" to snapshot?.dnsServers?.joinToString().orUnavailable(),
                ),
                action = "View Wi-Fi details",
                onAction = onOpenWifi,
            )
        }
        item {
            Text(
                "Traffic counters are Android device-wide totals, not per-network or per-device measurements.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WifiScreen(
    snapshot: NetworkSnapshot?,
    permissionGranted: Boolean,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (!permissionGranted) {
            item { PermissionCard(onRequestPermission) }
        }
        item {
            SectionHeading("WIRELESS LINK", "Wi-Fi values exposed by this Android device")
        }
        item {
            DetailCard(
                title = snapshot?.ssid.orUnavailable(),
                rows = listOf(
                    "Connection" to snapshot?.connectionState.orUnavailable(),
                    "BSSID" to snapshot?.bssid.orUnavailable(),
                    "Security" to snapshot?.security.orUnavailable(),
                    "Band" to snapshot?.band.orUnavailable(),
                    "Channel" to snapshot?.channel?.toString().orUnavailable(),
                    "Frequency" to snapshot?.frequencyMhz?.let { "$it MHz" }.orUnavailable(),
                    "Signal" to snapshot?.rssiDbm?.let { "$it dBm" }.orUnavailable(),
                    "Link speed" to snapshot?.linkSpeedMbps?.let { "$it Mbps" }.orUnavailable(),
                    "Receive link speed" to snapshot?.rxLinkSpeedMbps?.let { "$it Mbps" }.orUnavailable(),
                    "Transmit link speed" to snapshot?.txLinkSpeedMbps?.let { "$it Mbps" }.orUnavailable(),
                ),
            )
        }
        item {
            DetailCard(
                title = "IP configuration",
                rows = listOf(
                    "Interface" to snapshot?.interfaceName.orUnavailable(),
                    "IPv4" to snapshot?.ipv4Addresses?.joinToString().orUnavailable(),
                    "IPv6" to snapshot?.ipv6Addresses?.joinToString().orUnavailable(),
                    "Address / prefix" to snapshot?.prefixes?.joinToString().orUnavailable(),
                    "Default gateway" to snapshot?.gateways?.joinToString().orUnavailable(),
                    "DNS servers" to snapshot?.dnsServers?.joinToString().orUnavailable(),
                    "DHCP server" to snapshot?.dhcpServer.orUnavailable(),
                ),
            )
        }
        item {
            Text(
                "Android may redact SSID, BSSID, or security details based on OS version, permissions, and device policy. Unavailable values are not inferred.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsScreen(
    permissionGranted: Boolean,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            SectionHeading("APP SETTINGS", "Local network administration")
        }
        item {
            DetailCard(
                title = "Wi-Fi information access",
                rows = listOf(
                    "Permission" to if (permissionGranted) "Granted" else "Not granted",
                    "Data handling" to "Read from Android; not uploaded",
                    "Router controls" to "Not available in Milestone 1",
                ),
                action = if (permissionGranted) null else "Grant permission",
                onAction = if (permissionGranted) null else onRequestPermission,
            )
        }
        item {
            Text(
                "This app currently inspects the phone's active connection only. Router administration and persistent device inventory will be added in later milestones; no controls are simulated.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ConnectionCard(snapshot: NetworkSnapshot?) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(52.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
            ) {
                Icon(
                    Icons.Default.Wifi,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(14.dp),
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    snapshot?.connectionState ?: "Reading network…",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    snapshot?.ssid ?: snapshot?.transport ?: "Active connection",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            StatusDot(connected = snapshot?.connectionState?.startsWith("Connected") == true)
        }
    }
}

@Composable
private fun MetricCard(
    label: String,
    value: String,
    supporting: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                supporting,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun DetailCard(
    title: String,
    rows: List<Pair<String, String>>,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            rows.forEach { (label, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(0.8f),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        value,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1.2f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    )
                }
            }
            if (action != null && onAction != null) {
                Button(onClick = onAction, modifier = Modifier.fillMaxWidth()) {
                    Text(action)
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(onRequestPermission: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Wi-Fi details are restricted", style = MaterialTheme.typography.titleMedium)
            Text(
                if (Build.VERSION.SDK_INT >= 33) {
                    "Grant the nearby Wi-Fi permission to let Android expose connection identifiers. Basic IP configuration remains available where the OS provides it."
                } else {
                    "Android requires location permission to expose Wi-Fi identifiers on this OS version. This app does not collect or use your location."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onRequestPermission) {
                Text("Allow Wi-Fi details")
            }
        }
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatusDot(connected: Boolean) {
    val color = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    Surface(
        modifier = Modifier.size(12.dp),
        shape = CircleShape,
        color = color,
        content = {},
    )
}

private fun requiredWifiPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= 33) {
        arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES)
    } else {
        arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
    }

private fun hasWifiDetailsPermission(context: android.content.Context): Boolean {
    val permission = if (Build.VERSION.SDK_INT >= 33) {
        Manifest.permission.NEARBY_WIFI_DEVICES
    } else {
        Manifest.permission.ACCESS_FINE_LOCATION
    }
    return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
