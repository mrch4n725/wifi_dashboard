package com.wifidashboard.ui

import android.Manifest
import android.database.sqlite.SQLiteException
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import com.wifidashboard.discovery.DiscoveryEvent
import com.wifidashboard.discovery.ActiveTcpScanner
import com.wifidashboard.discovery.DiscoverySettingsStore
import com.wifidashboard.discovery.IPv4Subnet
import com.wifidashboard.discovery.MdnsDiscovery
import com.wifidashboard.discovery.NeighborTableReader
import com.wifidashboard.discovery.SsdpDiscovery
import com.wifidashboard.network.AndroidNetworkInspector
import com.wifidashboard.network.NetworkSnapshot
import com.wifidashboard.storage.DeviceListItem
import com.wifidashboard.storage.DeviceObservationEntity
import com.wifidashboard.storage.DeviceRepository
import com.wifidashboard.storage.NetworkDatabase
import com.wifidashboard.ui.format.formatBytes
import com.wifidashboard.ui.format.orUnavailable
import java.io.IOException
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Destination(val title: String) {
    Dashboard("Dashboard"),
    Devices("Devices"),
    Router("Router"),
    Wifi("Wi-Fi"),
    Settings("Settings"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiDashboardApp() {
    val context = LocalContext.current
    val inspector = remember(context) { AndroidNetworkInspector(context) }
    val deviceRepository = remember(context) {
        DeviceRepository(NetworkDatabase.get(context))
    }
    val discoverySettingsStore = remember(context) { DiscoverySettingsStore(context) }
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
                        Destination.Devices -> Icons.Default.Devices
                        Destination.Router -> Icons.Default.Router
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
            Destination.Devices -> DevicesScreen(
                snapshot = snapshot,
                permissionGranted = permissionGranted,
                onRequestPermission = {
                    permissionLauncher.launch(requiredWifiPermissions())
                },
                repository = deviceRepository,
                modifier = Modifier.padding(padding),
            )
            Destination.Router -> OpenWrtScreen(
                repository = deviceRepository,
                defaultGateway = snapshot?.gateways?.firstOrNull(::isIpv4Literal),
                modifier = Modifier.padding(padding),
            )
            Destination.Settings -> SettingsScreen(
                permissionGranted = permissionGranted,
                onRequestPermission = {
                    permissionLauncher.launch(requiredWifiPermissions())
                },
                discoverySettingsStore = discoverySettingsStore,
                snapshot = snapshot,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun DevicesScreen(
    snapshot: NetworkSnapshot?,
    permissionGranted: Boolean,
    onRequestPermission: () -> Unit,
    repository: DeviceRepository,
    modifier: Modifier = Modifier,
) {
    val devices by repository.observeDevices().collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val mdns = remember(context) { MdnsDiscovery(context) }
    val ssdp = remember(context) { SsdpDiscovery(context) }
    val activeScanner = remember { ActiveTcpScanner() }
    val neighborReader = remember { NeighborTableReader() }
    val discoverySettingsStore = remember(context) { DiscoverySettingsStore(context) }
    var selectedDeviceId by remember { mutableStateOf<String?>(null) }
    var isScanning by remember { mutableStateOf(false) }
    var scanMessage by remember { mutableStateOf<String?>(null) }
    var scanJob by remember { mutableStateOf<Job?>(null) }

    val selectedDevice = devices.firstOrNull { it.id == selectedDeviceId }
    if (selectedDevice != null) {
        DeviceDetailScreen(
            device = selectedDevice,
            repository = repository,
            onBack = { selectedDeviceId = null },
            modifier = modifier,
        )
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            SectionHeading(
                "LOCAL DEVICE INVENTORY",
                "Persisted observations from this handset and this LAN",
            )
        }
        item {
            Button(
                onClick = {
                    if (isScanning) {
                        scanJob?.cancel()
                        scanMessage = "Cancelling discovery…"
                        return@Button
                    }
                    if (!permissionGranted) {
                        onRequestPermission()
                        return@Button
                    }
                    if (snapshot?.transport !in setOf("Wi-Fi", "Ethernet")) {
                        scanMessage = "Connect to a local Wi-Fi or Ethernet network before discovering devices."
                        return@Button
                    }
                    val activeSnapshot = snapshot
                    val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
                    val scanNetwork = connectivityManager.activeNetwork
                    val scanLinkProperties = scanNetwork?.let(connectivityManager::getLinkProperties)
                    val scanNetworkMatchesSnapshot = activeSnapshot != null &&
                        scanLinkProperties != null &&
                        (
                            activeSnapshot.interfaceName.isNullOrBlank() ||
                                scanLinkProperties.interfaceName == activeSnapshot.interfaceName
                            ) &&
                        activeSnapshot.ipv4Addresses.any { address ->
                            scanLinkProperties.linkAddresses.any { it.address.hostAddress == address }
                        }
                    val scanSocketFactory = scanNetwork
                        ?.takeIf { scanNetworkMatchesSnapshot }
                        ?.socketFactory
                    scanJob = scope.launch {
                        isScanning = true
                        scanMessage = "Reading router, neighbor, active TCP, mDNS, and SSDP sources…"
                        var recorded = 0
                        var activeHostsFound = 0
                        val warnings = mutableSetOf<String>()
                        try {
                            val settings = discoverySettingsStore.load()
                            val localAddress = activeSnapshot?.ipv4Addresses?.firstOrNull()
                            val cidr = settings.selectedSubnetCidr
                                ?: activeSnapshot?.prefixes?.firstOrNull {
                                    it.substringBefore('/') == localAddress
                                }
                            val subnet = if (localAddress != null && cidr != null) {
                                try {
                                    IPv4Subnet.forLocalAddress(cidr, localAddress)
                                } catch (error: IllegalArgumentException) {
                                    warnings += error.message ?: "The active IPv4 subnet is not eligible for scanning."
                                    null
                                }
                            } else {
                                warnings += "Android did not expose a local IPv4 subnet; active IPv4 scanning is unavailable."
                                null
                            }

                            suspend fun consume(event: DiscoveryEvent) {
                                when (event) {
                                    is DiscoveryEvent.Found -> {
                                        repository.record(event.observation)
                                        recorded++
                                        if (event.observation.source == ActiveTcpScanner.SOURCE) {
                                            activeHostsFound++
                                        }
                                    }
                                    is DiscoveryEvent.Warning -> warnings += event.message
                                    is DiscoveryEvent.Progress -> {
                                        scanMessage =
                                            "Active TCP scan: ${event.completed}/${event.total} hosts checked; " +
                                                "$activeHostsFound hosts found. Tap to cancel."
                                    }
                                }
                            }

                            val neighbors = try {
                                withContext(Dispatchers.IO) {
                                    neighborReader.read(interfaceName = activeSnapshot?.interfaceName)
                                }
                            } catch (error: IOException) {
                                warnings += "Android's cached neighbor table is unavailable: ${error.message}"
                                emptyList()
                            }
                            neighbors.forEach { observation ->
                                repository.record(observation)
                                recorded++
                            }

                            if (subnet != null && scanSocketFactory != null) {
                                activeScanner.scan(
                                    subnet = subnet,
                                    concurrency = settings.concurrency,
                                    connectTimeoutMillis = settings.connectTimeoutMillis,
                                    maxDurationMillis = settings.maxDurationMillis,
                                    socketFactory = scanSocketFactory,
                                ).collect(::consume)

                                val refreshedNeighbors = try {
                                    withContext(Dispatchers.IO) {
                                        neighborReader.read(interfaceName = activeSnapshot?.interfaceName)
                                    }
                                } catch (error: IOException) {
                                    warnings += "Android's neighbor table could not be reread after active discovery: ${error.message}"
                                    emptyList()
                                }
                                refreshedNeighbors.forEach { observation ->
                                    repository.record(observation)
                                    recorded++
                                }
                            } else if (subnet != null) {
                                warnings += "The selected Android network is no longer active; active TCP and SSDP discovery were skipped."
                            }
                            mdns.scan().collect(::consume)
                            if (subnet != null && scanSocketFactory != null) {
                                try {
                                    ssdp.scan(subnet, activeSnapshot?.interfaceName).collect(::consume)
                                } catch (error: IOException) {
                                    warnings += "SSDP/UPnP discovery failed: ${error.message}"
                                }
                            }
                            scanMessage = buildString {
                                append("Discovery finished: $recorded observations saved; ")
                                append("$activeHostsFound hosts exposed one or more scanned TCP ports.")
                                if (neighbors.isEmpty()) {
                                    append(" No cached neighbors were exposed by Android.")
                                }
                                if (warnings.isNotEmpty()) {
                                    append(" ")
                                    append(warnings.joinToString(" "))
                                }
                            }
                        } catch (cancelled: CancellationException) {
                            scanMessage = "Discovery cancelled; $recorded observations already saved were retained."
                            throw cancelled
                        } catch (error: IOException) {
                            scanMessage = "Network discovery failed: ${error.message}"
                        } catch (error: SecurityException) {
                            scanMessage = "Android denied local network discovery: ${error.message}"
                        } catch (error: IllegalStateException) {
                            scanMessage = "Local discovery failed: ${error.message}"
                        } catch (error: SQLiteException) {
                            scanMessage = "Device inventory could not be saved: ${error.message}"
                        } finally {
                            isScanning = false
                            scanJob = null
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (isScanning) "Cancel discovery" else "Discover on this network")
            }
        }
        if (!permissionGranted) {
            item {
                PermissionCard(
                    onRequestPermission = onRequestPermission,
                    discovery = true,
                )
            }
        }
        scanMessage?.let { message ->
            item {
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Text(
                "Discovery combines cached Android neighbors, bounded TCP connects to 11 common ports on a private IPv4 subnet, selected mDNS service types, and SSDP/UPnP responses. It is cancellable and limited to 1,024 local hosts; no ICMP, raw packets, UDP port scan, or complete traffic capture is performed. Results are partial—not an authoritative client list—and service scans do not prove a port is publicly reachable.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (devices.isEmpty()) {
            item {
                DetailCard(
                    title = "No devices observed yet",
                    rows = listOf(
                        "Discovery" to "Run a local discovery above",
                        "Completeness" to "Router data is needed for an authoritative client list",
                    ),
                )
            }
        } else {
            item {
                Text(
                    "${devices.size} persisted ${if (devices.size == 1) "identity" else "identities"}",
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            items(devices, key = DeviceListItem::id) { device ->
                DeviceSummaryCard(device) {
                    selectedDeviceId = device.id
                }
            }
        }
    }
}

@Composable
private fun DeviceSummaryCard(device: DeviceListItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                device.customName ?: device.latestHostname ?: device.latestIp
                    ?: device.latestServiceName ?: "Unidentified device",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                listOfNotNull(device.latestIp, device.latestMac, device.latestServiceName)
                    .joinToString(" · ")
                    .ifBlank { "Address unavailable" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Last observed ${formatTimestamp(device.lastSeenAt)} · ${device.latestSource.orUnavailable()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DeviceDetailScreen(
    device: DeviceListItem,
    repository: DeviceRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentDevice by repository.observeDevice(device.id)
        .collectAsStateWithLifecycle(initialValue = device)
    val history by repository.observeHistory(device.id)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    var showEditDialog by remember(device.id) { mutableStateOf(false) }
    var editError by remember(device.id) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val item = currentDevice ?: device

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            TextButton(onClick = onBack) {
                Text("‹  All devices")
            }
        }
        item {
            DetailCard(
                title = item.customName ?: item.latestHostname ?: item.latestIp ?: "Unidentified device",
                rows = listOf(
                    "Current observed IP" to item.latestIp.orUnavailable(),
                    "Observed MAC" to item.latestMac.orUnavailable(),
                    "Hostname" to item.latestHostname.orUnavailable(),
                    "mDNS service name" to item.latestServiceName.orUnavailable(),
                    "Group" to item.groupName.orUnavailable(),
                    "Identity confidence" to when {
                        item.identityConfidence >= 0.9f -> "MAC-based"
                        item.identityConfidence >= 0.5f -> "Hostname-based; may be shared"
                        else -> "IP-only; may change"
                    },
                    "First seen" to formatTimestamp(item.firstSeenAt),
                    "Last observed" to formatTimestamp(item.lastSeenAt),
                    "Latest source" to item.latestSource.orUnavailable(),
                    "Latest observation detail" to item.latestDetail.orUnavailable(),
                    "Connection status" to "Not authoritative without router data",
                ),
                action = "Edit name and group",
                onAction = { showEditDialog = true },
            )
        }
        item {
            Text("OBSERVATION HISTORY", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        if (history.isEmpty()) {
            item { Text("No observations recorded.") }
        } else {
            items(history, key = DeviceObservationEntity::id) { observation ->
                ObservationHistoryRow(observation)
            }
        }
        editError?.let { message ->
            item {
                Text(message, color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (showEditDialog) {
        DeviceEditDialog(
            device = item,
            onDismiss = { showEditDialog = false },
            onSave = { name, group ->
                scope.launch {
                    try {
                        repository.updateName(device.id, name)
                        repository.updateGroup(device.id, group)
                        showEditDialog = false
                        editError = null
                    } catch (error: SQLiteException) {
                        editError = "Could not save device labels: ${error.message}"
                    }
                }
            },
        )
    }
}

@Composable
private fun ObservationHistoryRow(observation: DeviceObservationEntity) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(observation.source, style = MaterialTheme.typography.titleSmall)
            Text(
                listOfNotNull(observation.ipAddress, observation.macAddress, observation.hostname)
                    .plus(listOfNotNull(observation.serviceName))
                    .joinToString(" · ")
                    .ifBlank { "Address unavailable" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                formatTimestamp(observation.observedAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            observation.detail?.let { detail ->
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DeviceEditDialog(
    device: DeviceListItem,
    onDismiss: () -> Unit,
    onSave: (name: String, group: String) -> Unit,
) {
    var name by remember(device.id) { mutableStateOf(device.customName.orEmpty()) }
    var group by remember(device.id) { mutableStateOf(device.groupName.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit device") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Custom name") },
                    singleLine = true,
                )
                TextField(
                    value = group,
                    onValueChange = { group = it },
                    label = { Text("Group") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, group) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private fun formatTimestamp(timestamp: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timestamp))

private fun isIpv4Literal(value: String): Boolean {
    val octets = value.split('.')
    return octets.size == 4 && octets.all { octet ->
        octet.toIntOrNull()?.let { it in 0..255 && (octet == "0" || !octet.startsWith('0')) } == true
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
    discoverySettingsStore: DiscoverySettingsStore,
    snapshot: NetworkSnapshot?,
    modifier: Modifier = Modifier,
) {
    val initialSettings = remember(discoverySettingsStore) { discoverySettingsStore.load() }
    var concurrency by remember(discoverySettingsStore) {
        mutableStateOf(initialSettings.concurrency.toString())
    }
    var timeout by remember(discoverySettingsStore) {
        mutableStateOf(initialSettings.connectTimeoutMillis.toString())
    }
    var duration by remember(discoverySettingsStore) {
        mutableStateOf((initialSettings.maxDurationMillis / 1_000).toString())
    }
    var selectedSubnet by remember(discoverySettingsStore) {
        mutableStateOf(initialSettings.selectedSubnetCidr.orEmpty())
    }
    var settingsMessage by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            SectionHeading("APP SETTINGS", "Local network administration")
        }
        item {
            Text("DISCOVERY SETTINGS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            OutlinedTextField(
                value = selectedSubnet,
                onValueChange = { selectedSubnet = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Selected local IPv4 subnet (optional)") },
                supportingText = { Text("CIDR, for example 192.168.1.0/24; blank uses Android's current subnet.") },
                singleLine = true,
            )
            OutlinedTextField(
                value = concurrency,
                onValueChange = { concurrency = it.filter(Char::isDigit) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Concurrent hosts (1–64)") },
                singleLine = true,
            )
            OutlinedTextField(
                value = timeout,
                onValueChange = { timeout = it.filter(Char::isDigit) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("TCP connect timeout in ms (100–1000)") },
                singleLine = true,
            )
            OutlinedTextField(
                value = duration,
                onValueChange = { duration = it.filter(Char::isDigit) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Active scan limit in seconds (5–60)") },
                singleLine = true,
            )
            Text(
                "A custom subnet must contain this handset's current local address. Scanning is limited to private or link-local IPv4 networks and at most 1,024 hosts; blank uses Android's current subnet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = {
                    try {
                        selectedSubnet.trim().takeIf(String::isNotEmpty)?.let { cidr ->
                            val localAddress = snapshot?.ipv4Addresses?.firstOrNull()
                                ?: throw IllegalArgumentException(
                                    "Connect to the local network before selecting a subnet.",
                                )
                            IPv4Subnet.forLocalAddress(cidr, localAddress)
                        }
                        discoverySettingsStore.save(
                            com.wifidashboard.discovery.DiscoverySettings(
                                concurrency = concurrency.toInt(),
                                connectTimeoutMillis = timeout.toInt(),
                                maxDurationMillis = duration.toLong() * 1_000,
                                selectedSubnetCidr = selectedSubnet.trim().takeIf(String::isNotEmpty),
                            ),
                        )
                        settingsMessage = "Discovery settings saved and will apply to the next scan."
                    } catch (error: IllegalArgumentException) {
                        settingsMessage = error.message ?: "Enter valid discovery settings."
                    } catch (error: IllegalStateException) {
                        settingsMessage = error.message ?: "Could not save discovery settings."
                    }
                },
            ) {
                Text("Save discovery settings")
            }
            settingsMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
        item {
            DetailCard(
                title = "Wi-Fi information access",
                rows = listOf(
                    "Permission" to if (permissionGranted) "Granted" else "Not granted",
                    "Data handling" to "Read from Android; not uploaded",
                    "Router integration" to "OpenWrt read-only inspection is available",
                ),
                action = if (permissionGranted) null else "Grant permission",
                onAction = if (permissionGranted) null else onRequestPermission,
            )
        }
        item {
            Text(
                "OpenWrt support currently provides authenticated, read-only router inspection. Enforcement and configuration controls are not yet available; no controls are simulated.",
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
private fun PermissionCard(
    onRequestPermission: () -> Unit,
    discovery: Boolean = false,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                if (discovery) "Nearby device permission is required"
                else "Wi-Fi details are restricted",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                if (discovery) {
                    "Android requires Nearby devices permission before this app can browse local mDNS services."
                } else if (Build.VERSION.SDK_INT >= 33) {
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
