package com.tungate

import java.io.File

object Paths {
    val dataDir: File by lazy {
        File(System.getProperty("user.home"), "Library/Application Support/TunGate").apply { mkdirs() }
    }
    val helperDir: File by lazy { File(dataDir, "helper").apply { mkdirs() } }
    val tunnelsFile = File(dataDir, "tunnels.json")

    fun bundledTool(name: String): File? {
        val candidates = mutableListOf<File>()
        val codeBase = runCatching {
            File(Paths::class.java.protectionDomain.codeSource.location.toURI())
        }.getOrNull()
        if (codeBase != null) {
            val base = if (codeBase.isDirectory) codeBase else codeBase.parentFile
            base?.let {
                candidates.add(File(it, "resources/$name"))
                candidates.add(File(it.parentFile ?: it, "Resources/$name"))
            }
        }
        candidates.add(File(dataDir, "vendor/$name"))
        candidates.add(File(System.getProperty("user.dir"), "vendor/$name"))
        candidates.add(File(System.getProperty("user.dir"), "tun-go/$name"))
        return candidates.firstOrNull {
            if (!it.isFile) return@firstOrNull false
            runCatching { it.setExecutable(true, false) }
            true
        }
    }
}
