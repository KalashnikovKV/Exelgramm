package com.example.exelgramm.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvStableIdTest {

    @Test
    fun `stableCsvRowId differs for different row numbers with same content`() {
        val cols = listOf("", "2024-01-01", "alice", "hello")
        val id1 = stableCsvRowId(2, cols)
        val id2 = stableCsvRowId(3, cols)
        assertNotEquals(id1, id2)
        assertTrue(id1.startsWith("csv_2_"))
        assertTrue(id2.startsWith("csv_3_"))
    }

    @Test
    fun `stableCsvRowId is deterministic for same input`() {
        val cols = listOf("", "2024-01-01", "alice", "hello")
        assertEquals(stableCsvRowId(2, cols), stableCsvRowId(2, cols))
    }
}
