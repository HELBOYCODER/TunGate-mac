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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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

private val FoxOrange = Color(0xFFE8722A)
private val FoxNavy = Color(0xFF1B2A41)

fun main() {
    application {
        Window(
            onCloseRequest = {
                Engine.disconnect()
                Helper.releaseOnQuit()
                exitApplication()
            },
            title = "TunGate",
            state = rememberWindowState(width = 460.dp, height = 780.dp),
        ) {
            MaterialTheme(
                colorScheme = MaterialTheme.colorScheme.copy(
                    primary = FoxOrange,
                    onPrimary = Color.White,
                    secondary = FoxNavy,
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
    val engineState by Engine.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Shield, contentDescription = null, tint = FoxOrange)
                        Spacer(Modifier.width(8.dp))
                        Text("TunGate", fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
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
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Tunnels", style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { showAdd = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "Add tunnel", tint = FoxOrange)
                }
            }
            if (tunnels.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        "No tunnels yet.\nAdd a WireGuard or AmneziaWG .conf file, paste its text, or import a QR image.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(tunnels, key = { it.name + it.addedAt }) { tunnel ->
                        TunnelRow(tunnel, engineState) {
                            tunnels = tunnels.filterNot { it === tunnel }.toMutableList()
                            TunnelStore.save(tunnels)
                        }
                    }
                }
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
            containerColor = if (connected) FoxOrange.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(56.dp).background(
                    if (connected) FoxOrange else MaterialTheme.colorScheme.outlineVariant,
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
                        tunnelsForToggle()?.let { Engine.connect(it) }
                    } else {
                        Engine.disconnect()
                    }
                },
            )
        }
    }
}

private var lastSelected: StoredTunnel? = null

private fun tunnelsForToggle(): StoredTunnel? {
    val tunnels = TunnelStore.load()
    return lastSelected ?: tunnels.firstOrNull()
}

@Composable
private fun TunnelRow(tunnel: StoredTunnel, state: EngineState, onDelete: () -> Unit) {
    val isActive = state.state == TunnelState.CONNECTED && state.tunnelName == tunnel.name
    var selected by remember { mutableStateOf(lastSelected?.name == tunnel.name) }
    Card(
        Modifier.fillMaxWidth().clickable {
            selected = true
            lastSelected = tunnel
            if (state.state == TunnelState.DISCONNECTED) Engine.connect(tunnel) else Engine.disconnect()
        },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive || selected) FoxOrange.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tunnel.name, fontWeight = FontWeight.SemiBold)
                val parsed = remember(tunnel.conf) { ConfParser.parse(tunnel.conf, tunnel.name).getOrNull() }
                Text(
                    parsed?.peers?.firstOrNull()?.endpoint?.let { "Endpoint: $it" }
                        ?: "Invalid config",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (isActive) {
                Text("●", color = FoxOrange, fontSize = 18.sp)
                Spacer(Modifier.width(10.dp))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.outline)
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

    fun tryAdd(text: String, suggestedName: String) {
        val parsed = ConfParser.parse(text, suggestedName)
        if (parsed.isFailure) {
            error = parsed.exceptionOrNull()?.message
            return
        }
        var finalName = suggestedName
        var i = 2
        while (finalName in existing) { finalName = "$suggestedName-$i"; i++ }
        onAdd(StoredTunnel(name = finalName, conf = text))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add tunnel") },
        text = {
            Column {
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
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
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
                val suggested = name.ifBlank { "Tunnel ${existing.size + 1}" }
                if (confText.isBlank()) {
                    error = "Provide the config text, a file, or a QR image"
                } else {
                    tryAdd(confText, suggested)
                }
            }) { Text("Add") }
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
