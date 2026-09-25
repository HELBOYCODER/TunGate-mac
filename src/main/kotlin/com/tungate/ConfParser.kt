package com.tungate

data class WgPeer(
    val publicKey: String,
    val presharedKey: String = "",
    val endpoint: String = "",
    val allowedIps: List<String> = emptyList(),
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
        get() = peers.any { p ->
            p.allowedIps.any { it == "0.0.0.0/0" || it == "::/0" }
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
        var cur: Map<String, String>? = null

        fun flushPeer() {
            val c = cur ?: return
            c["publickey"]?.let { pk ->
                peers += WgPeer(
                    publicKey = pk,
                    presharedKey = c["presharedkey"].orEmpty(),
                    endpoint = c["endpoint"].orEmpty(),
                    allowedIps = c["allowedips"]?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty(),
                )
            }
            cur = null
        }

        val section = LinkedHashMap<String, String>()
        fun closeSection() {
            if (section.isNotEmpty()) {
                if (cur == null && section.containsKey("privatekey")) {
                    privateKey = section["privatekey"].orEmpty()
                    addresses = section["address"]?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
                    dns = section["dns"]?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
                    section["mtu"]?.toIntOrNull()?.let { mtu = it }
                    listenPort = section["listenport"].orEmpty()
                } else {
                    flushPeer()
                    cur = section.toMap()
                }
                section.clear()
            }
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
