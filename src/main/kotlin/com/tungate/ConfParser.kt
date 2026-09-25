package com.tungate

data class WgPeer(
    val publicKey: String,
    val presharedKey: String = "",
    val endpoint: String = "",
    val allowedIps: List<String> = emptyList(),
    val keepalive: String = "",
    val amnezia: Map<String, String> = emptyMap(),
)

data class WgConfig(
    val name: String,
    val privateKey: String,
    val addresses: List<String> = emptyList(),
    val dns: List<String> = emptyList(),
    val mtu: Int = 1420,
    val listenPort: String = "",
    val peers: List<WgPeer> = emptyList(),
) {
    val capturesAllTraffic: Boolean
        get() = peers.any { p -> p.allowedIps.any { it == "0.0.0.0/0" || it == "::/0" } }
}

private val AMNEZIA_KEYS = setOf("jc", "jmin", "jmax", "s1", "s2", "h1", "h2")

fun WgConfig.toConfText(): String = buildString {
    appendLine("[Interface]")
    appendLine("PrivateKey = $privateKey")
    if (addresses.isNotEmpty()) appendLine("Address = ${addresses.joinToString(", ")}")
    if (dns.isNotEmpty()) appendLine("DNS = ${dns.joinToString(", ")}")
    appendLine("MTU = $mtu")
    if (listenPort.isNotBlank()) appendLine("ListenPort = $listenPort")
    peers.forEach { p ->
        appendLine()
        appendLine("[Peer]")
        appendLine("PublicKey = ${p.publicKey}")
        if (p.presharedKey.isNotBlank()) appendLine("PresharedKey = ${p.presharedKey}")
        if (p.endpoint.isNotBlank()) appendLine("Endpoint = ${p.endpoint}")
        if (p.allowedIps.isNotEmpty()) appendLine("AllowedIPs = ${p.allowedIps.joinToString(", ")}")
        if (p.keepalive.isNotBlank()) appendLine("PersistentKeepalive = ${p.keepalive}")
        p.amnezia.forEach { (k, v) -> appendLine("$k = $v") }
    }
}

object ConfParser {

    fun parse(text: String, fallbackName: String): Result<WgConfig> = runCatching {
        var privateKey = ""
        var addresses = listOf<String>()
        var dns = listOf<String>()
        var mtu = 1420
        var listenPort = ""
        val peers = mutableListOf<WgPeer>()
        var pending: Map<String, String>? = null

        fun flushPeer() {
            val c = pending ?: return
            c["publickey"]?.let { pk ->
                peers += WgPeer(
                    publicKey = pk,
                    presharedKey = c["presharedkey"].orEmpty(),
                    endpoint = c["endpoint"].orEmpty(),
                    allowedIps = c["allowedips"]?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty(),
                    keepalive = c["persistentkeepalive"].orEmpty(),
                    amnezia = c.filterKeys { it in AMNEZIA_KEYS }
                        .mapKeys { (k, _) -> k.replaceFirstChar { it.uppercase() } },
                )
            }
            pending = null
        }

        val section = LinkedHashMap<String, String>()
        fun closeSection() {
            if (section.isEmpty()) return
            if (section.containsKey("privatekey")) {
                privateKey = section["privatekey"].orEmpty()
                addresses = section["address"]?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
                dns = section["dns"]?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
                section["mtu"]?.toIntOrNull()?.let { mtu = it }
                listenPort = section["listenport"].orEmpty()
            } else {
                flushPeer()
                pending = section.toMap()
            }
            section.clear()
        }

        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            when {
                line.isEmpty() || line.startsWith("#") || line.startsWith(";") -> {}
                line.startsWith("[") -> closeSection()
                line.contains('=') -> {
                    val (k, v) = line.split('=', limit = 2)
                    section[k.trim().lowercase()] = v.trim()
                }
            }
        }
        closeSection()
        flushPeer()

        require(privateKey.isNotBlank() && peers.isNotEmpty()) {
            "Not a valid WireGuard/AmneziaWG config (missing PrivateKey or [Peer])"
        }
        WgConfig(
            name = fallbackName,
            privateKey = privateKey,
            addresses = addresses,
            dns = dns,
            mtu = mtu,
            listenPort = listenPort,
            peers = peers,
        )
    }
}
