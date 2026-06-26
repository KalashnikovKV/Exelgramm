package com.example.exelgramm.ui.chat

import com.example.exelgramm.data.local.AuthSession
import com.example.exelgramm.data.local.ChatConfig
import com.example.exelgramm.data.local.SessionProvider
import com.example.exelgramm.data.repository.ChatRepository
import com.example.exelgramm.data.repository.DeleteMessageUseCase
import com.example.exelgramm.data.repository.SendMessageUseCase
import com.example.exelgramm.data.repository.UpdateMessageUseCase
import com.example.exelgramm.domain.model.Message
import com.example.exelgramm.data.repository.MessageSyncOptions
import com.example.exelgramm.domain.model.MessageType
import com.example.exelgramm.sync.ChatBackgroundSync
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val configuredConfig = ChatConfig(
        spreadsheetId = "sheet123",
        webAppUrl = "https://script.google.com/macros/s/test/exec",
        sheetName = "Sheet1",
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `sendMessage adds optimistic outgoing message with pending flag`() = runTest {
        val session = FakeSessionProvider(
            auth = AuthSession(username = "alice"),
            config = configuredConfig,
        )
        val repository = FakeChatRepository()
        val vm = createViewModel(session, repository)

        advanceUntilIdle()
        vm.setPollingActive(false)
        vm.onInputChanged("Привет")
        vm.sendMessage()
        advanceUntilIdle()

        val outgoing = vm.uiState.value.messages.filterIsInstance<MessageUiItem.Outgoing>()
        assertEquals(1, outgoing.size)
        assertEquals("Привет", outgoing.first().text)
        assertTrue(outgoing.first().isPending)
    }

    @Test
    fun `sendMessage clears pending flag after successful server sync on next load`() = runTest {
        val session = FakeSessionProvider(
            auth = AuthSession(username = "alice"),
            config = configuredConfig,
        )
        val repository = FakeChatRepository()
        val vm = createViewModel(session, repository)

        advanceUntilIdle()
        vm.setPollingActive(false)
        vm.onInputChanged("Привет")
        vm.sendMessage()
        advanceUntilIdle()

        val sentId = vm.uiState.value.messages.first().id
        repository.loadResult = Result.success(
            listOf(
                Message(
                    id = sentId,
                    timestamp = Instant.parse("2024-01-01T00:00:01Z"),
                    author = "alice",
                    text = "Привет",
                    type = MessageType.TEXT,
                ),
            ),
        )
        vm.refresh(showLoading = false)
        advanceUntilIdle()

        val outgoing = vm.uiState.value.messages.filterIsInstance<MessageUiItem.Outgoing>().first()
        assertTrue(!outgoing.isPending)
    }

    private fun createViewModel(
        session: FakeSessionProvider,
        repository: FakeChatRepository,
    ): ChatViewModel = ChatViewModel(
        sessionProvider = session,
        coordinatorFactory = coordinatorFactory(session, repository),
        sendMessageUseCase = SendMessageUseCase(repository),
        updateMessageUseCase = UpdateMessageUseCase(repository),
        deleteMessageUseCase = DeleteMessageUseCase(repository),
    )

    private fun coordinatorFactory(
        session: FakeSessionProvider,
        repository: FakeChatRepository,
    ): ChatSyncCoordinator.Factory = object : ChatSyncCoordinator.Factory {
        override fun create(scope: CoroutineScope): ChatSyncCoordinator =
            ChatSyncCoordinator(session, repository, NoOpChatBackgroundSync, scope)
    }

    private object NoOpChatBackgroundSync : ChatBackgroundSync {
        override fun schedule() = Unit
        override fun cancel() = Unit
    }

    private class FakeSessionProvider(
        auth: AuthSession,
        config: ChatConfig,
    ) : SessionProvider {
        private val _auth = MutableStateFlow(auth)
        private val _config = MutableStateFlow(config)
        override val authSession = _auth.asStateFlow()
        override val chatConfig = _config.asStateFlow()
    }

    private class FakeChatRepository : ChatRepository {
        var loadResult: Result<List<Message>> = Result.success(emptyList())

        override suspend fun loadMessages(
            config: ChatConfig,
            options: MessageSyncOptions,
        ): Result<List<Message>> = loadResult

        override suspend fun getCachedMessages(config: ChatConfig): Result<List<Message>> =
            loadResult

        override suspend fun getMessagesByAuthor(config: ChatConfig, author: String): Result<List<Message>> =
            Result.success(emptyList())

        override suspend fun getMessageById(config: ChatConfig, messageId: String): Result<Message?> =
            Result.success(null)

        override suspend fun sendMessage(config: ChatConfig, message: Message): Result<Message> =
            Result.success(message)

        override suspend fun updateMessage(
            config: ChatConfig,
            messageId: String,
            text: String,
        ): Result<Unit> = Result.success(Unit)

        override suspend fun deleteMessage(config: ChatConfig, messageId: String): Result<Unit> =
            Result.success(Unit)
    }
}
