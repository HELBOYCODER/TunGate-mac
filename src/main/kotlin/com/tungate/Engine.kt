package com.tungate

import com.tungate.Logs.log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class TunnelState { DISCONNECTED, CONNECTING, CONNECTED }

data class EngineState(
    val state: TunnelState = TunnelState.DISCONNECTED,
    val tunnelName: String = "",
    val detail: String = "",
    val connectedSince: Long = 0L,
)

object Engine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var heartbeatJob: Job? = null

    private val _state = MutableStateFlow(EngineState())
    val state: StateFlow<EngineState> = _state

    fun connect(tunnel: StoredTunnel) {
        if (_state.value.state != TunnelState.DISCONNECTED) return
        _state.value = EngineState(TunnelState.CONNECTING, tunnel.name)
        scope.launch {
            val parsed = ConfParser.parse(tunnel.conf, tunnel.name)
            val config = parsed.getOrNull()
            if (config == null) {
                log("Config invalid: ${parsed.exceptionOrNull()?.message}")
                _state.value = EngineState(TunnelState.DISCONNECTED, detail = "Invalid config: ${parsed.exceptionOrNull()?.message}")
                return@launch
            }
            val endpoint = config.peers.firstOrNull()?.endpoint
            log("Connecting \"${config.name}\" → ${endpoint ?: "no endpoint"}")
            val ok = runCatching { Helper.startTunnel(tunnel.conf, config.dns) }.getOrDefault(false)
            if (ok) {
                log("Tunnel up on ${Helper.activeInterface()}${if (config.capturesAllTraffic) " (all traffic)" else ""}")
                _state.value = EngineState(TunnelState.CONNECTED, config.name, Helper.activeInterface(), System.currentTimeMillis())
                startHeartbeat()
            } else {
                _state.value = EngineState(TunnelState.DISCONNECTED, detail = "Could not start the tunnel")
            }
        }
    }

    fun disconnect() {
        if (_state.value.state == TunnelState.DISCONNECTED) return
        heartbeatJob?.cancel()
        scope.launch {
            runCatching { Helper.stopTunnel() }
            log("Tunnel stopped")
            _state.value = EngineState()
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                Helper.heartbeat()
                delay(5_000)
            }
        }
    }
}
