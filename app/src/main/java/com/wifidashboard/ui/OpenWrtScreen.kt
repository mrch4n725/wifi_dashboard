package com.wifidashboard.ui

import android.database.sqlite.SQLiteException
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.wifidashboard.router.openwrt.OpenWrtAdapter
import com.wifidashboard.router.openwrt.OpenWrtEndpoint
import com.wifidashboard.router.openwrt.OpenWrtRouterSession
import com.wifidashboard.router.openwrt.OpenWrtRpcException
import com.wifidashboard.router.openwrt.RouterClient
import com.wifidashboard.router.openwrt.RouterCredentialStore
import com.wifidashboard.router.openwrt.RouterCredentials
import com.wifidashboard.router.openwrt.RouterSnapshot
import com.wifidashboard.storage.DeviceIdentity
import com.wifidashboard.storage.DeviceObservation
import com.wifidashboard.storage.DeviceRepository
import java.io.IOException
import java.security.GeneralSecurityException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException

@Composable
fun OpenWrtScreen(
    repository: DeviceRepository,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val adapter = remember { OpenWrtAdapter() }
    val credentialStore = remember(context) { RouterCredentialStore(context) }
    val scope = rememberCoroutineScope()
    var endpointText by remember { mutableStateOf("https://192.168.1.1") }
    var username by remember { mutableStateOf("root") }
    var password by remember { mutableStateOf("") }
    var allowCleartext by remember { mutableStateOf(false) }
    var rememberCredentials by remember { mutableStateOf(false) }
    var session by remember { mutableStateOf<OpenWrtRouterSession?>(null) }
    var snapshot by remember { mutableStateOf<RouterSnapshot?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "OPENWRT ROUTER",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "Connect directly to rpcd on your local network. Only methods advertised by this router are queried.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = endpointText,
            onValueChange = { endpointText = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Router URL") },
            supportingText = { Text("Origin only, for example https://192.168.1.1") },
            singleLine = true,
            enabled = !busy && session == null,
        )
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Username") },
            singleLine = true,
            enabled = !busy && session == null,
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            enabled = !busy && session == null,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = allowCleartext,
                onCheckedChange = { allowCleartext = it },
                enabled = !busy && session == null,
            )
            Column {
                Text("Allow unencrypted HTTP")
                Text(
                    "Only enable for a trusted local network. Credentials and router data will not be encrypted in transit.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = rememberCredentials,
                onCheckedChange = { rememberCredentials = it },
                enabled = !busy && session == null,
            )
            Text("Save credentials encrypted with Android Keystore")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    val endpoint = try {
                        OpenWrtEndpoint.parse(endpointText, allowCleartext)
                    } catch (error: IllegalArgumentException) {
                        message = error.message ?: "Enter a valid router URL"
                        return@Button
                    }
                    val credentials = try {
                        RouterCredentials(username.trim(), password)
                    } catch (error: IllegalArgumentException) {
                        message = error.message ?: "Enter router credentials"
                        return@Button
                    }
                    busy = true
                    message = "Connecting to router…"
                    scope.launch {
                        try {
                            val result = withContext(Dispatchers.IO) {
                                val connected = adapter.connect(endpoint, credentials)
                                val inspection = connected.inspect()
                                val inventoryMessage = persistRouterClients(repository, inspection)
                                val saveError = if (rememberCredentials) {
                                    try {
                                        credentialStore.save(endpoint, credentials)
                                        null
                                    } catch (error: GeneralSecurityException) {
                                        error.message ?: "Keystore could not save credentials"
                                    } catch (error: JSONException) {
                                        error.message ?: "Could not encode credentials for storage"
                                    } catch (error: IllegalStateException) {
                                        error.message ?: "Android could not save credentials"
                                    }
                                } else {
                                    null
                                }
                                Triple(connected, inspection, saveError ?: inventoryMessage)
                            }
                            session = result.first
                            snapshot = result.second
                            password = ""
                            message = result.third
                                ?: if (result.first.capabilities.detected) {
                                    "Connected and authenticated. Router data is read-only."
                                } else {
                                    result.first.capabilities.detectionMessage
                                        ?: "Connected, but router capabilities could not be detected"
                                }
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: OpenWrtRpcException) {
                            message = error.message ?: "Router rejected the request"
                        } catch (error: IOException) {
                            message = error.message ?: "Could not reach the router"
                        } catch (error: GeneralSecurityException) {
                            message = error.message ?: "Android Keystore operation failed"
                        } catch (error: JSONException) {
                            message = error.message ?: "Router returned invalid data"
                        } finally {
                            busy = false
                        }
                    }
                },
                enabled = !busy && session == null,
            ) {
                Text("Connect")
            }
            TextButton(
                onClick = {
                    val endpoint = try {
                        OpenWrtEndpoint.parse(endpointText, allowCleartext)
                    } catch (error: IllegalArgumentException) {
                        message = error.message ?: "Enter a valid router URL"
                        return@TextButton
                    }
                    busy = true
                    scope.launch {
                        try {
                            val credentials = withContext(Dispatchers.IO) {
                                credentialStore.load(endpoint)
                            }
                            if (credentials == null) {
                                message = "No saved credentials for this router"
                            } else {
                                username = credentials.username
                                password = credentials.password
                                rememberCredentials = true
                                message = "Saved credentials loaded"
                            }
                        } catch (error: GeneralSecurityException) {
                            message = error.message ?: "Could not decrypt saved credentials"
                        } catch (error: JSONException) {
                            message = error.message ?: "Saved credentials are invalid"
                        } catch (error: IllegalStateException) {
                            message = error.message ?: "Saved credentials could not be loaded"
                        } finally {
                            busy = false
                        }
                    }
                },
                enabled = !busy && session == null,
            ) {
                Text("Load saved")
            }
        }
        if (busy) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator()
                Spacer(Modifier.width(12.dp))
                Text(message.orEmpty())
            }
        } else {
            message?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        session?.let { activeSession ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = {
                        busy = true
                        scope.launch {
                            try {
                                val latest = withContext(Dispatchers.IO) { activeSession.inspect() }
                                snapshot = latest
                                message = withContext(Dispatchers.IO) {
                                    persistRouterClients(repository, latest)
                                }
                            } catch (error: IOException) {
                                message = error.message ?: "Could not refresh router information"
                            } finally {
                                busy = false
                            }
                        }
                    },
                    enabled = !busy,
                ) {
                    Text("Refresh")
                }
                TextButton(
                    onClick = {
                        busy = true
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) { activeSession.logout() }
                                session = null
                                snapshot = null
                                message = "Disconnected"
                            } catch (error: IOException) {
                                message = error.message ?: "Router logout failed"
                            } finally {
                                busy = false
                            }
                        }
                    },
                    enabled = !busy,
                ) {
                    Text("Disconnect")
                }
            }
        }
        snapshot?.let { RouterInformation(it) }
    }
}

@Composable
private fun RouterInformation(snapshot: RouterSnapshot) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        RouterCard(
            "ROUTER",
            listOfNotNull(
                snapshot.model?.let { "Model" to it },
                snapshot.firmware?.let { "Firmware" to it },
                snapshot.uptimeSeconds?.let { "Uptime" to formatDuration(it) },
                "Capability detection" to if (snapshot.capabilities.detected) {
                    "${snapshot.capabilities.methods.size} methods reported"
                } else {
                    snapshot.capabilities.detectionMessage ?: "Unavailable"
                },
            ),
        )
        RouterCard(
            "INTERFACES",
            snapshot.interfaces?.map { routerInterface ->
                val state = when (routerInterface.isUp) {
                    true -> "up"
                    false -> "down"
                    null -> "state unavailable"
                }
                routerInterface.name to listOfNotNull(
                    routerInterface.protocol,
                    routerInterface.device,
                    state,
                    routerInterface.addresses.takeIf(List<String>::isNotEmpty)?.joinToString(),
                ).joinToString(" · ")
            } ?: listOf("Interface data" to "Not supported by this router"),
        )
        RouterCard(
            "DHCP LEASES",
            snapshot.dhcpLeases.map { it.displayName() to it.displayAddress() }
                .ifEmpty { listOf("Leases" to "None reported or unsupported") },
        )
        RouterCard(
            "WIRELESS CLIENTS",
            snapshot.wirelessClients.map { it.displayName() to it.displayAddress() }
                .ifEmpty { listOf("Clients" to "None reported or unsupported") },
        )
        if (snapshot.capabilities.methods.isNotEmpty()) {
            RouterCard(
                "DETECTED RPC METHODS",
                snapshot.capabilities.methods
                    .sortedWith(compareBy({ it.objectName }, { it.methodName }))
                    .map { "ubus" to it.toString() },
            )
        }
        if (snapshot.issues.isNotEmpty()) {
            RouterCard("ROUTER RESPONSE ISSUES", snapshot.issues.mapIndexed { index, issue ->
                "Issue ${index + 1}" to issue
            })
        }
        Text(
            "All information above is router-reported and read-only. Unsupported methods are omitted; no firewall, wireless, or device configuration has been changed.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RouterCard(title: String, rows: List<Pair<String, String>>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            rows.forEach { (label, value) ->
                Column {
                    Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun RouterClient.displayName(): String =
    hostname ?: macAddress ?: "Unknown client"

private fun RouterClient.displayAddress(): String =
    listOfNotNull(ipAddress, macAddress, details, source).joinToString(" · ")
        .ifBlank { "Router reported no address details" }

private fun formatDuration(seconds: Long): String {
    val days = seconds / 86_400
    val hours = seconds % 86_400 / 3_600
    val minutes = seconds % 3_600 / 60
    return if (days > 0) "${days}d ${hours}h ${minutes}m" else "${hours}h ${minutes}m"
}

private suspend fun persistRouterClients(
    repository: DeviceRepository,
    snapshot: RouterSnapshot,
): String {
    val clients = snapshot.dhcpLeases + snapshot.wirelessClients
    if (clients.isEmpty()) return "Router reported no DHCP or wireless clients"

    var stored = 0
    var skipped = 0
    clients.forEach { client ->
        val observation = DeviceObservation(
            source = "OpenWrt ${client.source}",
            observedAt = System.currentTimeMillis(),
            macAddress = client.macAddress,
            hostname = client.hostname,
            ipAddress = client.ipAddress,
            detail = client.details,
        )
        try {
            DeviceIdentity.from(observation)
        } catch (_: IllegalArgumentException) {
            skipped++
            return@forEach
        }
        try {
            repository.record(observation)
            stored++
        } catch (error: SQLiteException) {
            return "Router data read successfully, but inventory storage failed after $stored observations: ${error.message}"
        }
    }

    return "Stored $stored router observations in Devices" +
        if (skipped > 0) "; skipped $skipped entries without an identity" else ""
}
