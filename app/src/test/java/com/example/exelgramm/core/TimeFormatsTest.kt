package com.example.exelgramm.core

import org.junit.Assert.assertEquals
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
    fun `formatChatTime returns empty string for blank input`() {
        assertEquals("", TimeFormats.formatChatTime(""))
        assertEquals("", TimeFormats.formatChatTime("   "))
    }

    @Test
    fun `formatChatTime returns HH_mm format for valid ISO string`() {
        val result = TimeFormats.formatChatTime("2024-06-15T14:30:45.000Z")
        assertTrue(
            "Expected HH:mm format but got: $result",
            result.matches(Regex("""\d{2}:\d{2}""")),
        )
    }

    @Test
    fun `formatChatTime handles string without milliseconds`() {
        val result = TimeFormats.formatChatTime("2024-06-15T09:05:00")
        assertTrue(result.matches(Regex("""\d{2}:\d{2}""")))
    }

    @Test
    fun `formatChatTime returns fallback for invalid input`() {
        val result = TimeFormats.formatChatTime("not-a-valid-date")
        assertTrue(result.isNotEmpty())
        assertTrue(result.length <= 16)
    }
}
