package com.tungate

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.net.InetSocketAddress
import java.net.Socket

/** Measures TCP latency to each tunnel's endpoint, like the Android client. */
object Pinger {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val _results = MutableStateFlow<Map<String, String>>(emptyMap())
    val results: kotlinx.coroutines.flow.StateFlow<Map<String, String>> = _results

    fun pingAll(tunnels: List<StoredTunnel>) {
        tunnels.forEach { t ->
            scope.launch {
                val hostPort = ConfParser.parse(t.conf, t.name).getOrNull()
                    ?.peers?.firstOrNull()?.endpoint ?: return@launch
                val host = hostPort.substringBeforeLast(':')
                val port = hostPort.substringAfterLast(':').toIntOrNull() ?: return@launch
                val ms = runCatching {
                    val start = System.nanoTime()
                    Socket().use { s ->
                        s.connect(InetSocketAddress(host, port), 2_000)
                    }
                    ((System.nanoTime() - start) / 1_000_000L).toInt()
                }.getOrNull()
                _results.value = _results.value + (t.name to (ms?.let { "$it ms" } ?: "unreachable"))
            }
        }
    }
}
