package com.example.exelgramm.ui.chat

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.exelgramm.core.CHAT_PAGE_SIZE
import com.example.exelgramm.core.ErrorTexts
import com.example.exelgramm.core.TimeFormats
import com.example.exelgramm.core.UiText
import com.example.exelgramm.data.local.AuthSession
import com.example.exelgramm.data.local.ChatConfig
import com.example.exelgramm.data.local.SessionProvider
import com.example.exelgramm.data.repository.ChatRepository
import com.example.exelgramm.data.repository.DeleteMessageUseCase
import com.example.exelgramm.data.repository.MessageSyncOptions
import com.example.exelgramm.data.repository.SendMessageUseCase
import com.example.exelgramm.data.repository.UpdateMessageUseCase
import com.example.exelgramm.domain.model.Message
import com.example.exelgramm.domain.model.MessageType
import com.example.exelgramm.sync.ChatBackgroundSync
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.ExperimentalCoroutinesApi

data class ChatUiState(
    val auth: AuthSession = AuthSession(),
    val chatConfig: ChatConfig = ChatConfig(),
    val messages: List<MessageUiItem> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val canLoadMore: Boolean = false,
    val error: UiText? = null,
    val inputText: String = "",
    val inputType: MessageType = MessageType.TEXT,
)

sealed interface ChatEffect {
    data class ShowError(@param:StringRes val resId: Int) : ChatEffect
}

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val sessionProvider: SessionProvider,
    private val repository: ChatRepository,
    private val sendMessageUseCase: SendMessageUseCase,
    private val updateMessageUseCase: UpdateMessageUseCase,
    private val deleteMessageUseCase: DeleteMessageUseCase,
    private val chatSyncScheduler: ChatBackgroundSync,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _effects = Channel<ChatEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private val stateLock = Mutex()
    private val internalState = MutableStateFlow(ChatInternalState())
    private val pollingActive = MutableStateFlow(false)
    private val fastPollUntil = MutableStateFlow(0L)
    private var displayLimit = CHAT_PAGE_SIZE

    init {
        viewModelScope.launch {
            sessionProvider.authSession.collect { auth ->
                val previousDisplayName = _uiState.value.auth.displayName
                _uiState.update { it.copy(auth = auth) }
                if (auth.displayName != previousDisplayName) {
                    publishMessages(auth.displayName)
                }
            }
        }
        viewModelScope.launch {
            sessionProvider.chatConfig.collect { config ->
                val previous = _uiState.value.chatConfig
                _uiState.update { it.copy(chatConfig = config) }
                when {
                    !config.isConfigured -> {
                        chatSyncScheduler.cancel()
                        resetChatState()
                    }
                    !previous.isConfigured ||
                        previous.spreadsheetId != config.spreadsheetId ||
                        previous.webAppUrl != config.webAppUrl ||
                        previous.sheetName != config.sheetName -> {
                        chatSyncScheduler.schedule()
                        displayLimit = CHAT_PAGE_SIZE
                        refresh(showLoading = true)
                    }
                }
            }
        }
        startPollingPipeline()
    }

    fun setPollingActive(active: Boolean) {
        pollingActive.value = active
    }

    fun refresh(showLoading: Boolean = true) {
        val config = _uiState.value.chatConfig
        if (!config.isConfigured) return
        viewModelScope.launch { syncMessages(config, showLoading = showLoading, fullRefresh = true) }
    }

    fun loadMoreHistory() {
        val total = internalState.value.rawMessages.size
        if (displayLimit >= total) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            displayLimit = (displayLimit + CHAT_PAGE_SIZE).coerceAtMost(total)
            publishMessages(_uiState.value.auth.displayName)
            _uiState.update { it.copy(isLoadingMore = false) }
        }
    }

    fun onInputChanged(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun toggleInputType() {
        _uiState.update {
            it.copy(
                inputType = when (it.inputType) {
                    MessageType.TEXT -> MessageType.IMPORTANT
                    MessageType.IMPORTANT -> MessageType.TEXT
                },
            )
        }
    }

    fun sendMessage() {
        val auth = _uiState.value.auth
        val config = _uiState.value.chatConfig
        val text = _uiState.value.inputText.trim()
        val type = _uiState.value.inputType
        if (text.isEmpty() || !config.isConfigured) return

        val optimistic = Message(
            id = UUID.randomUUID().toString(),
            timestamp = TimeFormats.nowIsoUtc(),
            author = auth.displayName,
            text = text,
            type = type,
        )

        viewModelScope.launch {
            updateInternal { state ->
                state.copy(
                    pendingOps = state.pendingOps.copy(sends = state.pendingOps.sends + optimistic.id),
                    failedIds = state.failedIds - optimistic.id,
                    rawMessages = (state.rawMessages + optimistic).sortedBy { it.timestamp },
                )
            }
            _uiState.update { it.copy(inputText = "", inputType = MessageType.TEXT, error = null) }
            publishMessages(auth.displayName)

            sendMessageUseCase(config, optimistic).fold(
                onSuccess = {
                    triggerFastPolling()
                    viewModelScope.launch { syncMessages(config, showLoading = false, fullRefresh = false) }
                },
                onFailure = { e ->
                    updateInternal { state ->
                        state.copy(
                            pendingOps = state.pendingOps.copy(sends = state.pendingOps.sends - optimistic.id),
                            failedIds = state.failedIds + optimistic.id,
                        )
                    }
                    _uiState.update {
                        it.copy(inputText = text, error = UiText.Dynamic(ErrorTexts.from(e)))
                    }
                    publishMessages(auth.displayName)
                },
            )
        }
    }

    fun editMessage(messageId: String, updatedText: String) {
        val auth = _uiState.value.auth
        val config = _uiState.value.chatConfig
        val text = updatedText.trim()
        if (!config.isConfigured || text.isEmpty()) return
        val currentMessage = internalState.value.rawMessages.firstOrNull { it.id == messageId } ?: return
        if (!currentMessage.isMine(auth.displayName)) return
        if (currentMessage.text == text) return

        optimisticUpdate(
            messageId = messageId,
            apply = {
                updateInternal { state ->
                    state.copy(
                        pendingOps = state.pendingOps.copy(edits = state.pendingOps.edits + (messageId to text)),
                        failedIds = state.failedIds - messageId,
                        rawMessages = state.rawMessages.map { m ->
                            if (m.id == messageId) m.copy(text = text) else m
                        },
                    )
                }
            },
            rollback = {
                updateInternal { state ->
                    state.copy(
                        pendingOps = state.pendingOps.copy(edits = state.pendingOps.edits - messageId),
                        failedIds = state.failedIds + messageId,
                        rawMessages = state.rawMessages.map { m ->
                            if (m.id == messageId) m.copy(text = currentMessage.text) else m
                        },
                    )
                }
            },
            request = { updateMessageUseCase(config, messageId, text) },
        )
    }

    fun deleteMessage(messageId: String) {
        val auth = _uiState.value.auth
        val config = _uiState.value.chatConfig
        if (!config.isConfigured) return
        val currentMessage = internalState.value.rawMessages.firstOrNull { it.id == messageId } ?: return
        if (!currentMessage.isMine(auth.displayName)) return

        optimisticUpdate(
            messageId = messageId,
            apply = {
                updateInternal { state ->
                    state.copy(
                        pendingOps = state.pendingOps.copy(deletes = state.pendingOps.deletes + messageId),
                        failedIds = state.failedIds - messageId,
                        rawMessages = state.rawMessages.filterNot { it.id == messageId },
                    )
                }
            },
            rollback = {
                updateInternal { state ->
                    state.copy(
                        pendingOps = state.pendingOps.copy(deletes = state.pendingOps.deletes - messageId),
                        failedIds = state.failedIds + messageId,
                        rawMessages = (state.rawMessages + currentMessage).sortedBy { it.timestamp },
                    )
                }
            },
            request = { deleteMessageUseCase(config, messageId) },
        )
    }

    private fun startPollingPipeline() {
        viewModelScope.launch {
            @OptIn(ExperimentalCoroutinesApi::class)
            combine(sessionProvider.chatConfig, pollingActive) { config, active ->
                config.takeIf { it.isConfigured && active }
            }.flatMapLatest { config: ChatConfig? ->
                if (config == null) {
                    emptyFlow<Unit>()
                } else {
                    flow {
                        var backoffMs = POLL_INTERVAL_MS
                        while (true) {
                            val success = syncMessages(config, showLoading = false, fullRefresh = false)
                            backoffMs = if (success) {
                                currentPollInterval()
                            } else {
                                (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
                            }
                            delay(backoffMs)
                        }
                    }
                }
            }.collect { }
        }
    }

    private fun currentPollInterval(): Long =
        if (System.currentTimeMillis() < fastPollUntil.value) FAST_POLL_INTERVAL_MS else POLL_INTERVAL_MS

    private fun triggerFastPolling() {
        fastPollUntil.value = System.currentTimeMillis() + FAST_POLL_DURATION_MS
    }

    private suspend fun syncMessages(
        config: ChatConfig,
        showLoading: Boolean,
        fullRefresh: Boolean,
    ): Boolean {
        if (!config.isConfigured) return false
        if (showLoading) _uiState.update { it.copy(isLoading = true, error = null) }

        val since = if (fullRefresh) null else internalState.value.lastRemoteTimestamp
        val options = MessageSyncOptions(since = since, fullRefresh = fullRefresh)
        val result = repository.loadMessages(config, options)
        result.fold(
            onSuccess = { messages ->
                mergeRaw(messages, incremental = !fullRefresh)
                publishMessages(_uiState.value.auth.displayName)
                _uiState.update { it.copy(isLoading = false, error = null) }
            },
            onFailure = { e ->
                if (showLoading) {
                    _uiState.update {
                        it.copy(isLoading = false, error = UiText.Dynamic(ErrorTexts.from(e)))
                    }
                }
            },
        )
        return result.isSuccess
    }

    private fun optimisticUpdate(
        messageId: String,
        apply: suspend () -> Unit,
        rollback: suspend () -> Unit,
        request: suspend () -> Result<*>,
    ) {
        viewModelScope.launch {
            apply()
            publishMessages(_uiState.value.auth.displayName)
            request().fold(
                onSuccess = {
                    updateInternal { state -> state.copy(failedIds = state.failedIds - messageId) }
                    publishMessages(_uiState.value.auth.displayName)
                },
                onFailure = { e ->
                    rollback()
                    publishMessages(_uiState.value.auth.displayName)
                    _uiState.update { it.copy(error = UiText.Dynamic(ErrorTexts.from(e))) }
                },
            )
        }
    }

    private suspend fun mergeRaw(incoming: List<Message>, incremental: Boolean) {
        updateInternal { state ->
            val remote = if (incremental) {
                val serverMessages = state.rawMessages.filter { it.id !in state.pendingOps.sends }
                mergeRemoteDelta(serverMessages, incoming)
            } else {
                incoming
            }
            val remoteIds = remote.associateBy { it.id }
            val unsyncedSends = state.rawMessages.filter {
                it.id in state.pendingOps.sends && it.id !in remoteIds
            }
            val (merged, newPending) = mergeMessages(remote, state.pendingOps, unsyncedSends)
            val lastTs = merged.maxOfOrNull { it.timestamp }
            state.copy(
                rawMessages = merged,
                pendingOps = newPending,
                lastRemoteTimestamp = lastTs ?: state.lastRemoteTimestamp,
            )
        }
    }

    private fun mergeRemoteDelta(existing: List<Message>, delta: List<Message>): List<Message> {
        if (delta.isEmpty()) return existing
        val map = existing.associateBy { it.id }.toMutableMap()
        delta.forEach { map[it.id] = it }
        return map.values.sortedBy { it.timestamp }
    }

    private suspend fun resetChatState() {
        displayLimit = CHAT_PAGE_SIZE
        updateInternal { ChatInternalState() }
        _uiState.update { it.copy(messages = emptyList(), canLoadMore = false) }
    }

    private suspend fun updateInternal(transform: (ChatInternalState) -> ChatInternalState) {
        stateLock.withLock {
            internalState.value = transform(internalState.value)
        }
    }

    private fun publishMessages(displayName: String) {
        val state = internalState.value
        val all = state.rawMessages
        val visible = if (all.size <= displayLimit) all else all.takeLast(displayLimit)
        val pendingSends = state.pendingOps.sends
        val failedIds = state.failedIds
        val items = visible.map { m ->
            m.toUiItem(
                displayName = displayName,
                isPending = m.id in pendingSends,
                hasError = m.id in failedIds,
            )
        }
        _uiState.update {
            it.copy(
                messages = items,
                canLoadMore = all.size > displayLimit,
            )
        }
    }

    private fun Message.toUiItem(
        displayName: String,
        isPending: Boolean = false,
        hasError: Boolean = false,
    ): MessageUiItem =
        if (isMine(displayName)) {
            MessageUiItem.Outgoing(
                id = id,
                text = text,
                time = TimeFormats.formatChatTime(timestamp),
                messageType = type,
                isPending = isPending,
                hasError = hasError,
            )
        } else {
            MessageUiItem.Incoming(id, author, text, TimeFormats.formatChatTime(timestamp), type)
        }

    companion object {
        private const val POLL_INTERVAL_MS = 5_000L
        private const val FAST_POLL_INTERVAL_MS = 2_000L
        private const val FAST_POLL_DURATION_MS = 30_000L
        private const val MAX_BACKOFF_MS = 60_000L
    }
}
