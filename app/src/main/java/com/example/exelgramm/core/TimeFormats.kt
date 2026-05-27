package com.example.exelgramm.core

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object TimeFormats {

    fun nowIsoUtc(): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(Date())
    }

    fun formatChatTime(iso: String): String {
        if (iso.isBlank()) return ""
        return try {
            val parsed = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.parse(iso.take(19))
            if (parsed == null) iso.takeLast(8).take(5) else {
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(parsed)
            }
        } catch (_: Exception) {
            iso.take(16)
        }
    }
}
