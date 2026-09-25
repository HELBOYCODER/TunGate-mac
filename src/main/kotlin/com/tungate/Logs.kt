package com.tungate

import kotlinx.coroutines.flow.MutableStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Logs {
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)
    val lines = MutableStateFlow<List<String>>(emptyList())

    fun log(message: String) {
        println("I/TunGate: $message")
        lines.value = (lines.value + "${fmt.format(Date())}  $message").takeLast(500)
    }
}
