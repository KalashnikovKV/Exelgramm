package com.example.exelgramm.core

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeFormatsTest {

    @Test
    fun `nowIsoUtc returns ISO 8601 format with milliseconds and Z`() {
        val result = TimeFormats.nowIsoUtc()
        assertTrue(
            "Expected yyyy-MM-dd'T'HH:mm:ss.SSSZ but got: $result",
            result.matches(Regex("""\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z""")),
        )
    }

    @Test
    fun `toIso and parse round-trip preserve the instant`() {
        val instant = Instant.parse("2024-06-15T14:30:45.123Z")
        assertEquals(instant, TimeFormats.parse(TimeFormats.toIso(instant)))
    }

    @Test
    fun `parse handles Z suffix`() {
        assertEquals(
            Instant.parse("2024-06-15T14:30:45Z"),
            TimeFormats.parse("2024-06-15T14:30:45Z"),
        )
    }

    @Test
    fun `parse handles explicit offset`() {
        assertEquals(
            Instant.parse("2024-06-15T14:30:45Z"),
            TimeFormats.parse("2024-06-15T17:30:45+03:00"),
        )
    }

    @Test
    fun `parse treats no-zone value as UTC`() {
        assertEquals(
            Instant.parse("2024-06-15T09:05:00Z"),
            TimeFormats.parse("2024-06-15T09:05:00"),
        )
    }

    @Test
    fun `parse handles date-only value`() {
        assertEquals(
            Instant.parse("2024-06-15T00:00:00Z"),
            TimeFormats.parse("2024-06-15"),
        )
    }

    @Test
    fun `parse returns null for blank or invalid input`() {
        assertNull(TimeFormats.parse(""))
        assertNull(TimeFormats.parse("   "))
        assertNull(TimeFormats.parse(null))
        assertNull(TimeFormats.parse("not-a-valid-date"))
    }

    @Test
    fun `formatChatTime returns empty string for null`() {
        assertEquals("", TimeFormats.formatChatTime(null))
    }

    @Test
    fun `formatChatTime returns HH_mm format for valid instant`() {
        val result = TimeFormats.formatChatTime(Instant.parse("2024-06-15T14:30:45Z"))
        assertTrue("Expected HH:mm but got: $result", result.matches(Regex("""\d{2}:\d{2}""")))
    }

    @Test
    fun `formatFullDateTime returns full pattern for valid instant`() {
        val result = TimeFormats.formatFullDateTime(Instant.parse("2024-06-15T14:30:45Z"))
        assertTrue(result.matches(Regex("""\d{2}\.\d{2}\.\d{4} \d{2}:\d{2}""")))
    }
}
