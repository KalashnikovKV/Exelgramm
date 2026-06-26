package com.example.exelgramm.core

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Single place for time handling. Domain uses [Instant];
 * string ISO forms exist only at boundaries (wire/CSV) and in the UI.
 */
object TimeFormats {

    private val ISO_WRITE = DateTimeFormatter
        .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        .withZone(ZoneOffset.UTC)

    /** Local patterns without zone (treated as UTC). */
    private val LOCAL_FORMATS = listOf(
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
    )

    private val DISPLAY = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
    private val FULL_DISPLAY =
        DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault())

    fun now(): Instant = Instant.now()

    /** Canonical ISO-8601 UTC (`yyyy-MM-dd'T'HH:mm:ss.SSS'Z'`). */
    fun toIso(instant: Instant): String = ISO_WRITE.format(instant)

    fun nowIsoUtc(): String = toIso(now())

    /**
     * Tolerant ISO time parser for Apps Script, CSV/gviz, etc.
     * Supports: `Z` suffix, explicit offset, no milliseconds,
     * no-zone (UTC), and date-only values.
     */
    fun parse(raw: String?): Instant? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return null

        runCatching { return Instant.parse(value) }
        runCatching { return OffsetDateTime.parse(value).toInstant() }
        for (fmt in LOCAL_FORMATS) {
            try {
                return LocalDateTime.parse(value, fmt).toInstant(ZoneOffset.UTC)
            } catch (_: DateTimeParseException) {
                // try next format
            }
        }
        runCatching {
            return LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant()
        }
        return null
    }

    fun formatChatTime(instant: Instant?): String =
        instant?.let(DISPLAY::format).orEmpty()

    fun formatFullDateTime(instant: Instant?): String =
        instant?.let(FULL_DISPLAY::format).orEmpty()
}
