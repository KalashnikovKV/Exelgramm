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
 * Единая точка работы со временем. Домен оперирует [Instant];
 * строковые ISO-представления существуют только на границах (wire/CSV) и в UI.
 */
object TimeFormats {

    private val ISO_WRITE = DateTimeFormatter
        .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        .withZone(ZoneOffset.UTC)

    /** Локальные форматы без зоны (UTC по умолчанию). */
    private val LOCAL_FORMATS = listOf(
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
    )

    private val DISPLAY = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
    private val FULL_DISPLAY =
        DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault())

    fun now(): Instant = Instant.now()

    /** Каноническое ISO-8601 UTC представление (`yyyy-MM-dd'T'HH:mm:ss.SSS'Z'`). */
    fun toIso(instant: Instant): String = ISO_WRITE.format(instant)

    fun nowIsoUtc(): String = toIso(now())

    /**
     * Толерантный парсер ISO-времени из разных источников (Apps Script, CSV/gviz).
     * Поддерживает: суффикс `Z`, явный offset, отсутствие миллисекунд,
     * формат без зоны (трактуется как UTC) и дату без времени.
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
                // пробуем следующий формат
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
