package com.tungate

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import kotlin.math.abs

private val GateTeal = Color(0xFF2EC4B6)
private val GateNavy = Color(0xFF14213D)
private val GateAmber = Color(0xFFFFB703)

fun main() {
    application {
        Window(
            onCloseRequest = {
                Engine.disconnect()
                Helper.releaseOnQuit()
                exitApplication()
            },
            title = "TunGate",
            state = rememberWindowState(width = 480.dp, height = 820.dp),
        ) {
            MaterialTheme(
                colorScheme = MaterialTheme.colorScheme.copy(
                    primary = GateTeal,
                    onPrimary = Color.White,
                    secondary = GateNavy,
                    onSecondary = Color.White,
                    tertiary = GateAmber,
                    surface = Color(0xFFF7FAFA),
                ),
            ) {
                Surface(Modifier.fillMaxSize()) { App() }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun App() {
    var tunnels by remember { mutableStateOf(TunnelStore.load()) }
    var showAdd by remember { mutableStateOf(false) }
    var showLogs by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<StoredTunnel?>(null) }
    val engineState by Engine.state.collectAsState()
    val pings by Pinger.results.collectAsState()

    LaunchedEffect(tunnels.size) { Pinger.pingAll(tunnels) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Shield, contentDescription = null, tint = GateTeal)
                        Spacer(Modifier.width(8.dp))
                        Text("TunGate", fontWeight = FontWeight.Bold, color = GateNavy)
                    }
                },
                actions = {
                    IconButton(onClick = { Pinger.pingAll(tunnels) }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh pings")
                    }
                    IconButton(onClick = { showLogs = true }) {
                        Icon(Icons.Filled.Description, contentDescription = "Logs")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            StatusCard(engineState)
            Spacer(Modifier.height(16.dp))
            Text("Tunnels", style = MaterialTheme.typography.titleMedium, color = GateNavy)
            Spacer(Modifier.height(8.dp))
            if (tunnels.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        "No tunnels yet.\nAdd a WireGuard or AmneziaWG .conf file,\npaste its text, or import a QR image.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(tunnels, key = { it.name + it.addedAt }) { tunnel ->
                        TunnelRow(
                            tunnel = tunnel,
                            state = engineState,
                            ping = pings[tunnel.name],
                            onEdit = { editing = tunnel },
                            onDelete = {
                                if (engineState.tunnelName == tunnel.name) Engine.disconnect()
                                tunnels = tunnels.filterNot { it === tunnel }.toMutableList()
                                TunnelStore.save(tunnels)
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = { showAdd = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = GateTeal)
                Text("  Add tunnel", color = GateNavy, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showAdd) {
        AddTunnelDialog(
            existing = tunnels.map { it.name }.toSet(),
            onDismiss = { showAdd = false },
            onAdd = { stored ->
                tunnels = (tunnels.filterNot { it.name == stored.name } + stored).toMutableList()
                TunnelStore.save(tunnels)
                showAdd = false
            },
        )
    }

    editing?.let { current ->
        EditTunnelDialog(
            current = current,
            otherNames = tunnels.filter { it.name != current.name }.map { it.name }.toSet(),
            onDismiss = { editing = null },
            onSave = { updated ->
                tunnels = tunnels.map { if (it === current) updated else it }.toMutableList()
                TunnelStore.save(tunnels)
                editing = null
            },
        )
    }

    if (showLogs) {
        LogsDialog(onDismiss = { showLogs = false })
    }
}

@Composable
private fun StatusCard(state: EngineState) {
    val connected = state.state == TunnelState.CONNECTED
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (connected) GateTeal.copy(alpha = 0.18f) else GateNavy.copy(alpha = 0.06f),
        ),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(56.dp).background(
                    if (connected) GateTeal else GateNavy.copy(alpha = 0.25f),
                    CircleShape,
                ),
                contentAlignment = Alignment.Center,
            ) {
                if (state.state == TunnelState.CONNECTING) {
                    CircularProgressIndicator(Modifier.size(28.dp), color = Color.White)
                } else {
                    Icon(
                        Icons.Filled.PowerSettingsNew,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(30.dp),
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    when (state.state) {
                        TunnelState.CONNECTED -> "Connected"
                        TunnelState.CONNECTING -> "Connecting…"
                        TunnelState.DISCONNECTED -> if (state.detail.isNotBlank()) state.detail else "Disconnected"
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = GateNavy,
                )
                if (connected) {
                    Text(
                        "${state.tunnelName} via ${state.detail}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Switch(
                checked = state.state != TunnelState.DISCONNECTED,
                onCheckedChange = { on ->
                    if (on) {
                        lastSelected ?: TunnelStore.load().firstOrNull()?.let { Engine.connect(it) }
                    } else {
                        Engine.disconnect()
                    }
                },
            )
        }
    }
}

private var lastSelected: StoredTunnel? = null

@Composable
private fun TunnelRow(
    tunnel: StoredTunnel,
    state: EngineState,
    ping: String?,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val isActive = state.state == TunnelState.CONNECTED && state.tunnelName == tunnel.name
    Card(
        Modifier.fillMaxWidth().clickable {
            lastSelected = tunnel
            if (state.state == TunnelState.DISCONNECTED) Engine.connect(tunnel) else Engine.disconnect()
        },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) GateTeal.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tunnel.name, fontWeight = FontWeight.SemiBold, color = GateNavy)
                val parsed = remember(tunnel.conf) { ConfParser.parse(tunnel.conf, tunnel.name).getOrNull() }
                val endpoint = parsed?.peers?.firstOrNull()?.endpoint
                Text(
                    buildString {
                        if (endpoint != null) append(endpoint)
                        append("  ·  ")
                        append(
                            when {
                                parsed == null -> "invalid config"
                                parsed.capturesAllTraffic -> "full tunnel"
                                else -> "split tunnel"
                            },
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                ping ?: "…",
                style = MaterialTheme.typography.labelLarge,
                color = when {
                    ping == null -> MaterialTheme.colorScheme.outline
                    ping == "unreachable" -> MaterialTheme.colorScheme.error
                    else -> GateTeal
                },
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(8.dp))
            if (isActive) {
                Text("●", color = GateTeal, fontSize = 18.sp)
                Spacer(Modifier.width(4.dp))
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = GateNavy, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun AddTunnelDialog(
    existing: Set<String>,
    onDismiss: () -> Unit,
    onAdd: (StoredTunnel) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var confText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun add(suggestedName: String) {
        val parsed = ConfParser.parse(confText, suggestedName)
        if (parsed.isFailure) {
            error = parsed.exceptionOrNull()?.message
            return
        }
        var finalName = suggestedName
        var i = 2
        while (finalName in existing) { finalName = "$suggestedName-$i"; i++ }
        onAdd(StoredTunnel(name = finalName, conf = confText))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add tunnel") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Tunnel name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = confText,
                    onValueChange = { confText = it; error = null },
                    label = { Text("Paste .conf content") },
                    minLines = 5,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        pickFile("Select WireGuard/AmneziaWG config", ".conf")?.let { f ->
                            confText = f.readText()
                            if (name.isBlank()) name = f.nameWithoutExtension
                            error = null
                        }
                    }) { Text("From file…") }
                    TextButton(onClick = {
                        pickFile("Select QR image", ".png", ".jpg", ".jpeg")?.let { f ->
                            val decoded = QrDecode.fromImage(f)
                            if (decoded.isNullOrBlank()) {
                                error = "No WireGuard config found in that QR image"
                            } else {
                                confText = decoded
                                if (name.isBlank()) name = "QR ${abs(f.lastModified() % 1000)}"
                                error = null
                            }
                        }
                    }) { Text("From QR image…") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (confText.isBlank()) error = "Provide the config text, a file, or a QR image"
                else add(name.ifBlank { "Tunnel ${existing.size + 1}" })
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun EditTunnelDialog(
    current: StoredTunnel,
    otherNames: Set<String>,
    onDismiss: () -> Unit,
    onSave: (StoredTunnel) -> Unit,
) {
    val parsed = remember(current) { ConfParser.parse(current.conf, current.name).getOrNull() }
    var name by remember { mutableStateOf(current.name) }
    var privateKey by remember { mutableStateOf(parsed?.privateKey.orEmpty()) }
    var addresses by remember { mutableStateOf(parsed?.addresses?.joinToString(", ").orEmpty()) }
    var dns by remember { mutableStateOf(parsed?.dns?.joinToString(", ").orEmpty()) }
    var mtu by remember { mutableStateOf((parsed?.mtu ?: 1420).toString()) }
    var listenPort by remember { mutableStateOf(parsed?.listenPort.orEmpty()) }
    val peer = parsed?.peers?.firstOrNull()
    var publicKey by remember { mutableStateOf(peer?.publicKey.orEmpty()) }
    var presharedKey by remember { mutableStateOf(peer?.presharedKey.orEmpty()) }
    var endpoint by remember { mutableStateOf(peer?.endpoint.orEmpty()) }
    var allowedIps by remember { mutableStateOf(peer?.allowedIps?.joinToString(", ").orEmpty()) }
    var keepalive by remember { mutableStateOf(peer?.keepalive.orEmpty()) }
    var jc by remember { mutableStateOf(peer?.amnezia?.get("Jc").orEmpty()) }
    var jmin by remember { mutableStateOf(peer?.amnezia?.get("Jmin").orEmpty()) }
    var jmax by remember { mutableStateOf(peer?.amnezia?.get("Jmax").orEmpty()) }
    var s1 by remember { mutableStateOf(peer?.amnezia?.get("S1").orEmpty()) }
    var s2 by remember { mutableStateOf(peer?.amnezia?.get("S2").orEmpty()) }
    var h1 by remember { mutableStateOf(peer?.amnezia?.get("H1").orEmpty()) }
    var h2 by remember { mutableStateOf(peer?.amnezia?.get("H2").orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }

    @Composable
    fun field(label: String, value: String, set: (String) -> Unit, single: Boolean = true) {
        OutlinedTextField(
            value = value,
            onValueChange = { set(it); error = null },
            label = { Text(label) },
            singleLine = single,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodySmall,
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit tunnel") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                field("Name", name, set = { name = it })
                Text("Interface", fontWeight = FontWeight.Bold, color = GateNavy, fontSize = 13.sp)
                field("Private key", privateKey, set = { privateKey = it })
                field("Addresses (CIDR, comma separated)", addresses, set = { addresses = it })
                field("DNS servers", dns, set = { dns = it })
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    Box(Modifier.weight(1f)) { field("MTU", mtu, set = { mtu = it }) }
                    Box(Modifier.weight(1f)) { field("Listen port", listenPort, set = { listenPort = it }) }
                }
                Text("Peer / Server", fontWeight = FontWeight.Bold, color = GateNavy, fontSize = 13.sp)
                field("Public key", publicKey, set = { publicKey = it })
                field("Preshared key (optional)", presharedKey, set = { presharedKey = it })
                field("Endpoint (host:port)", endpoint, set = { endpoint = it })
                field("Allowed IPs", allowedIps, set = { allowedIps = it })
                field("Persistent keepalive (s)", keepalive, set = { keepalive = it })
                Text("AmneziaWG anti-DPI", fontWeight = FontWeight.Bold, color = GateNavy, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    Box(Modifier.weight(1f)) { field("Jc (junk packets)", jc, set = { jc = it }) }
                    Box(Modifier.weight(1f)) { field("Jmin", jmin, set = { jmin = it }) }
                    Box(Modifier.weight(1f)) { field("Jmax", jmax, set = { jmax = it }) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    Box(Modifier.weight(1f)) { field("S1", s1, set = { s1 = it }) }
                    Box(Modifier.weight(1f)) { field("S2", s2, set = { s2 = it }) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    Box(Modifier.weight(1f)) { field("H1", h1, set = { h1 = it }) }
                    Box(Modifier.weight(1f)) { field("H2", h2, set = { h2 = it }) }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank() || name in otherNames) {
                    error = "Pick a unique non-empty name"
                    return@TextButton
                }
                val amnezia = buildMap {
                    jc.ifBlank { null }?.let { put("Jc", it) }
                    jmin.ifBlank { null }?.let { put("Jmin", it) }
                    jmax.ifBlank { null }?.let { put("Jmax", it) }
                    s1.ifBlank { null }?.let { put("S1", it) }
                    s2.ifBlank { null }?.let { put("S2", it) }
                    h1.ifBlank { null }?.let { put("H1", it) }
                    h2.ifBlank { null }?.let { put("H2", it) }
                }
                val config = WgConfig(
                    name = name,
                    privateKey = privateKey.trim(),
                    addresses = addresses.split(',').map { it.trim() }.filter { it.isNotEmpty() },
                    dns = dns.split(',').map { it.trim() }.filter { it.isNotEmpty() },
                    mtu = mtu.trim().toIntOrNull() ?: 1420,
                    listenPort = listenPort.trim(),
                    peers = listOf(
                        WgPeer(
                            publicKey = publicKey.trim(),
                            presharedKey = presharedKey.trim(),
                            endpoint = endpoint.trim(),
                            allowedIps = allowedIps.split(',').map { it.trim() }.filter { it.isNotEmpty() },
                            keepalive = keepalive.trim(),
                            amnezia = amnezia,
                        ),
                    ),
                )
                val check = ConfParser.parse(config.toConfText(), name)
                if (check.isFailure) {
                    error = check.exceptionOrNull()?.message
                    return@TextButton
                }
                onSave(StoredTunnel(name = name, conf = config.toConfText(), addedAt = current.addedAt))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun LogsDialog(onDismiss: () -> Unit) {
    val lines by Logs.lines.collectAsState()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Logs") },
        text = {
            LazyColumn(Modifier.fillMaxWidth().height(360.dp)) {
                items(lines.reversed()) { line ->
                    Text(line, fontSize = 11.sp)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

private fun pickFile(title: String, vararg suffixes: String): File? {
    val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
    dialog.filenameFilter = java.io.FilenameFilter { _, n -> suffixes.any { n.endsWith(it, ignoreCase = true) } }
    dialog.isVisible = true
    val dir = dialog.directory ?: return null
    val file = dialog.file ?: return null
    return File(dir, file)
}
