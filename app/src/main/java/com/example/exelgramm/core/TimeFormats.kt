package com.example.exelgramm.core

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

object TimeFormats {

    private val ISO_WRITE = DateTimeFormatter
        .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        .withZone(ZoneOffset.UTC)

    private val ISO_READ = DateTimeFormatter
        .ofPattern("yyyy-MM-dd'T'HH:mm:ss")

    private val DISPLAY = DateTimeFormatter.ofPattern("HH:mm")
    private val FULL_DISPLAY = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")

    fun nowIsoUtc(): String = ISO_WRITE.format(Instant.now())

    fun formatChatTime(iso: String): String {
        if (iso.isBlank()) return ""
        return try {
            val ldt = LocalDateTime.parse(iso.take(19), ISO_READ)
            val zdt = ldt.atZone(ZoneOffset.UTC).withZoneSameInstant(ZoneId.systemDefault())
            DISPLAY.format(zdt)
        } catch (_: DateTimeParseException) {
            iso.take(16)
        }
    }

    fun formatFullDateTime(iso: String): String {
        if (iso.isBlank()) return ""
        return try {
            val ldt = LocalDateTime.parse(iso.take(19), ISO_READ)
            val zdt = ldt.atZone(ZoneOffset.UTC).withZoneSameInstant(ZoneId.systemDefault())
            FULL_DISPLAY.format(zdt)
        } catch (_: DateTimeParseException) {
            iso.take(16)
        }
    }
}
