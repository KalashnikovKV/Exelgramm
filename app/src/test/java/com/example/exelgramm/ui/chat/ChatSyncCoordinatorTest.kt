package com.example.exelgramm.ui.chat

import com.example.exelgramm.data.local.AuthSession
import com.example.exelgramm.data.local.ChatConfig
import com.example.exelgramm.data.local.SessionProvider
import com.example.exelgramm.data.repository.ChatRepository
import com.example.exelgramm.data.repository.MessageSyncOptions
import com.example.exelgramm.domain.model.Message
import com.example.exelgramm.sync.ChatBackgroundSync
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatSyncCoordinatorTest {

    private val config = ChatConfig(
        spreadsheetId = "sheet123",
        webAppUrl = "https://script.google.com/macros/s/test/exec",
        sheetName = "Sheet1",
    )

    private fun msg(id: Int, text: String = "m$id"): Message = Message(
        id = id.toString(),
        timestamp = Instant.ofEpochSecond(id.toLong()),
        author = "alice",
        text = text,
    )

    @Test
    fun `refresh paginates to last page and loadMore expands window`() = runTest {
        val repo = FakeRepo(full = (1..60).map { msg(it) })
        val coordinator = newCoordinator(repo, this)

        coordinator.refresh(config)
        advanceUntilIdle()

        var snap = coordinator.snapshot.value
        assertEquals(50, snap.visibleMessages.size)
        assertTrue(snap.canLoadMore)
        assertEquals((11..60).map { it.toString() }, snap.visibleMessages.map { it.id })

        coordinator.loadMoreHistory()
        advanceUntilIdle()

        snap = coordinator.snapshot.value
        assertEquals(60, snap.visibleMessages.size)
        assertFalse(snap.canLoadMore)
    }

    @Test
    fun `incremental sync dedups boundary id and applies update`() = runTest {
        val repo = FakeRepo(full = listOf(msg(1, "old"), msg(2)))
        val coordinator = newCoordinator(repo, this)

        coordinator.refresh(config)
        advanceUntilIdle()

        // Дельта повторяет граничное сообщение id=1 (>= since) с новым текстом и добавляет id=3.
        repo.delta = listOf(msg(1, "new"), msg(3))
        coordinator.syncIncremental(config)
        advanceUntilIdle()

        val raw = coordinator.snapshot.value.internalState.rawMessages
        assertEquals(listOf("1", "2", "3"), raw.map { it.id })
        assertEquals("new", raw.first { it.id == "1" }.text)
    }

    private fun newCoordinator(repo: ChatRepository, scope: CoroutineScope) =
        ChatSyncCoordinator(
            sessionProvider = FakeSessionProvider(config),
            repository = repo,
            backgroundSync = NoOpBackgroundSync,
            scope = scope,
        )

    private object NoOpBackgroundSync : ChatBackgroundSync {
        override fun schedule() = Unit
        override fun cancel() = Unit
    }

    private class FakeSessionProvider(config: ChatConfig) : SessionProvider {
        override val authSession = MutableStateFlow(AuthSession(username = "alice")).asStateFlow()
        override val chatConfig = MutableStateFlow(config).asStateFlow()
    }

    private class FakeRepo(
        var full: List<Message>,
        var delta: List<Message> = emptyList(),
    ) : ChatRepository {
        override suspend fun loadMessages(
            config: ChatConfig,
            options: MessageSyncOptions,
        ): Result<List<Message>> =
            Result.success(if (options.fullRefresh) full else delta)

        override suspend fun getCachedMessages(config: ChatConfig) = Result.success(full)
        override suspend fun getMessagesByAuthor(config: ChatConfig, author: String) =
            Result.success(emptyList<Message>())

        override suspend fun getMessageById(config: ChatConfig, messageId: String) =
            Result.success(null)

        override suspend fun sendMessage(config: ChatConfig, message: Message) =
            Result.success(message)

        override suspend fun updateMessage(config: ChatConfig, messageId: String, text: String) =
            Result.success(Unit)

        override suspend fun deleteMessage(config: ChatConfig, messageId: String) =
            Result.success(Unit)
    }
}
