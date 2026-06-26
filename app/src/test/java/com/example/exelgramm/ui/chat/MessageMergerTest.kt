package com.example.exelgramm.ui.chat

import com.example.exelgramm.domain.model.Message
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageMergerTest {

    private fun ts(id: String): Instant = when (id) {
        "local" -> Instant.parse("2024-01-01T00:00:02Z")
        else -> Instant.parse("2024-01-01T00:00:0${id}Z")
    }

    private fun msg(id: String, text: String = "text_$id", ts: Instant = ts(id)) =
        Message(id = id, timestamp = ts, author = "alice", text = text)

    // ---- базовое слияние ----

    @Test
    fun `пустые pending — возвращает серверный список`() {
        val remote = listOf(msg("1"), msg("2"))
        val (merged, pending) = mergeMessages(remote, PendingOps(), emptyList())
        assertEquals(remote, merged)
        assertTrue(pending.isEmpty)
    }

    // ---- pendingSends ----

    @Test
    fun `pendingSend снимается когда сообщение появилось на сервере`() {
        val remote = listOf(msg("1"), msg("2"))
        val (_, pending) = mergeMessages(
            remote,
            PendingOps(sends = setOf("1", "2")),
            emptyList(),
        )
        assertTrue(pending.sends.isEmpty())
    }

    @Test
    fun `unsynced send остаётся в списке если его нет на сервере`() {
        val optimistic = msg("local")
        val remote = listOf(msg("1"))
        val (merged, pending) = mergeMessages(
            remote,
            PendingOps(sends = setOf("local")),
            listOf(optimistic),
        )
        assertTrue("local" in merged.map { it.id })
        assertTrue("local" in pending.sends)
    }

    // ---- pendingDeletes ----

    @Test
    fun `pending delete скрывает сообщение присутствующее на сервере`() {
        val remote = listOf(msg("1"), msg("2"))
        val (merged, pending) = mergeMessages(
            remote,
            PendingOps(deletes = setOf("1")),
            emptyList(),
        )
        assertFalse("1" in merged.map { it.id })
        assertTrue("1" in pending.deletes)
    }

    @Test
    fun `pending delete снимается когда сообщение пропало с сервера`() {
        val remote = listOf(msg("2"))
        val (_, pending) = mergeMessages(
            remote,
            PendingOps(deletes = setOf("1")),
            emptyList(),
        )
        assertTrue(pending.deletes.isEmpty())
    }

    // ---- pendingEdits ----

    @Test
    fun `pending edit применяется поверх серверного текста`() {
        val remote = listOf(msg("1", text = "old"))
        val (merged, pending) = mergeMessages(
            remote,
            PendingOps(edits = mapOf("1" to "new")),
            emptyList(),
        )
        assertEquals("new", merged.first { it.id == "1" }.text)
        assertTrue("1" in pending.edits)
    }

    @Test
    fun `pending edit снимается когда сервер вернул новый текст`() {
        val remote = listOf(msg("1", text = "new"))
        val (_, pending) = mergeMessages(
            remote,
            PendingOps(edits = mapOf("1" to "new")),
            emptyList(),
        )
        assertTrue(pending.edits.isEmpty())
    }

    // ---- порядок ----

    @Test
    fun `сохраняет порядок предотсортированного серверного списка`() {
        val remote = listOf(
            msg("1", ts = Instant.parse("2024-01-01T00:00:01Z")),
            msg("3", ts = Instant.parse("2024-01-01T00:00:03Z")),
        )
        val (merged, _) = mergeMessages(remote, PendingOps(), emptyList())
        assertEquals(listOf("1", "3"), merged.map { it.id })
    }

    @Test
    fun `mergeSortedByTimestamp сливает два отсортированных списка за O(n)`() {
        val a = listOf(
            msg("1", ts = Instant.parse("2024-01-01T00:00:01Z")),
            msg("4", ts = Instant.parse("2024-01-01T00:00:04Z")),
        )
        val b = listOf(
            msg("2", ts = Instant.parse("2024-01-01T00:00:02Z")),
            msg("3", ts = Instant.parse("2024-01-01T00:00:03Z")),
        )
        assertEquals(listOf("1", "2", "3", "4"), mergeSortedByTimestamp(a, b).map { it.id })
    }

    @Test
    fun `unsynced send вставляется на своё место по timestamp`() {
        val optimistic = msg("local", ts = Instant.parse("2024-01-01T00:00:02Z"))
        val remote = listOf(
            msg("1", ts = Instant.parse("2024-01-01T00:00:01Z")),
            msg("3", ts = Instant.parse("2024-01-01T00:00:03Z")),
        )
        val (merged, _) = mergeMessages(
            remote,
            PendingOps(sends = setOf("local")),
            listOf(optimistic),
        )
        assertEquals(listOf("1", "local", "3"), merged.map { it.id })
    }

    // ---- mergeRemoteDelta ----

    @Test
    fun `mergeRemoteDelta добавляет новые и обновляет существующие`() {
        val existing = listOf(msg("1", text = "old"), msg("2"))
        val delta = listOf(msg("1", text = "new"), msg("3"))
        val merged = mergeRemoteDelta(existing, delta)
        assertEquals(listOf("1", "2", "3"), merged.map { it.id })
        assertEquals("new", merged.first { it.id == "1" }.text)
    }

    @Test
    fun `mergeRemoteDelta с пустой дельтой возвращает existing`() {
        val existing = listOf(msg("1"))
        assertEquals(existing, mergeRemoteDelta(existing, emptyList()))
    }
}
