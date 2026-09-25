package com.tungate

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class StoredTunnel(val name: String, val conf: String, val addedAt: Long = System.currentTimeMillis())

object TunnelStore {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    fun load(): MutableList<StoredTunnel> = runCatching {
        val file = Paths.tunnelsFile
        if (!file.exists()) mutableListOf() else json.decodeFromString<List<StoredTunnel>>(file.readText()).toMutableList()
    }.getOrDefault(mutableListOf())

    fun save(tunnels: List<StoredTunnel>) {
        runCatching {
            Paths.tunnelsFile.writeText(
                json.encodeToString(ListSerializer(StoredTunnel.serializer()), tunnels),
            )
        }
    }
}
