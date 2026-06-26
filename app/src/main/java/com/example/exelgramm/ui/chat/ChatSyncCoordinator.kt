package com.example.exelgramm.ui.chat

import com.example.exelgramm.core.CHAT_PAGE_SIZE
import com.example.exelgramm.core.ErrorTexts
import com.example.exelgramm.core.UiText
import com.example.exelgramm.data.local.ChatConfig
import com.example.exelgramm.data.local.SessionProvider
import com.example.exelgramm.data.repository.ChatRepository
import com.example.exelgramm.data.repository.MessageSyncOptions
import com.example.exelgramm.domain.model.Message
import com.example.exelgramm.sync.ChatBackgroundSync
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Sync state snapshot for the UI layer.
 * ViewModel maps [visibleMessages] to [MessageUiItem].
 */
data class ChatMessagesSnapshot(
    val internalState: ChatInternalState = ChatInternalState(),
    val visibleMessages: List<Message> = emptyList(),
    val canLoadMore: Boolean = false,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: UiText? = null,
)

/**
 * Chat sync coordinator: polling, server merge, history pagination.
 * ViewModel delegates network/merge logic here and keeps UI state only.
 */
class ChatSyncCoordinator @AssistedInject constructor(
    private val sessionProvider: SessionProvider,
    private val repository: ChatRepository,
    private val backgroundSync: ChatBackgroundSync,
    @Assisted private val scope: CoroutineScope,
) {
    @AssistedFactory
    interface Factory {
        fun create(scope: CoroutineScope): ChatSyncCoordinator
    }

    private val stateLock = Mutex()
    private val internalState = MutableStateFlow(ChatInternalState())
    private val pollingActive = MutableStateFlow(false)
    private val fastPollUntil = MutableStateFlow(0L)

    /** Visible history window size. Read/written only under [stateLock]. */
    @Volatile
    private var displayLimit = CHAT_PAGE_SIZE

    private val _snapshot = MutableStateFlow(ChatMessagesSnapshot())
    val snapshot: StateFlow<ChatMessagesSnapshot> = _snapshot.asStateFlow()

    val currentState: ChatInternalState get() = internalState.value

    fun start() {
        scope.launch {
            sessionProvider.chatConfig.collect { config ->
                handleConfigChange(config)
            }
        }
        startPollingPipeline()
    }

    fun setPollingActive(active: Boolean) {
        pollingActive.value = active
    }

    fun refresh(config: ChatConfig, showLoading: Boolean = true) {
        if (!config.isConfigured) return
        scope.launch { syncMessages(config, showLoading = showLoading, fullRefresh = true) }
    }

    fun syncIncremental(config: ChatConfig) {
        if (!config.isConfigured) return
        scope.launch { syncMessages(config, showLoading = false, fullRefresh = false) }
    }

    fun triggerFastPolling() {
        fastPollUntil.value = System.currentTimeMillis() + FAST_POLL_DURATION_MS
    }

    fun loadMoreHistory() {
        if (displayLimit >= internalState.value.rawMessages.size) return
        scope.launch {
            updateSnapshot { it.copy(isLoadingMore = true) }
            stateLock.withLock {
                val total = internalState.value.rawMessages.size
                if (displayLimit < total) {
                    displayLimit = (displayLimit + CHAT_PAGE_SIZE).coerceAtMost(total)
                }
            }
            publishSnapshot()
            updateSnapshot { it.copy(isLoadingMore = false) }
        }
    }

    suspend fun updateInternal(transform: (ChatInternalState) -> ChatInternalState) {
        stateLock.withLock {
            internalState.value = transform(internalState.value)
        }
        publishSnapshot()
    }

    suspend fun reset() {
        stateLock.withLock {
            displayLimit = CHAT_PAGE_SIZE
            internalState.value = ChatInternalState()
        }
        publishSnapshot(clearError = true)
    }

    private var lastConfig: ChatConfig? = null

    private suspend fun handleConfigChange(config: ChatConfig) {
        val previous = lastConfig ?: ChatConfig()
        when {
            !config.isConfigured -> {
                backgroundSync.cancel()
                reset()
            }
            !previous.isConfigured ||
                previous.spreadsheetId != config.spreadsheetId ||
                previous.webAppUrl != config.webAppUrl ||
                previous.sheetName != config.sheetName -> {
                backgroundSync.schedule()
                stateLock.withLock { displayLimit = CHAT_PAGE_SIZE }
                syncMessages(config, showLoading = true, fullRefresh = true)
            }
        }
        lastConfig = config
    }

    private fun startPollingPipeline() {
        scope.launch {
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

    private suspend fun syncMessages(
        config: ChatConfig,
        showLoading: Boolean,
        fullRefresh: Boolean,
    ): Boolean {
        if (!config.isConfigured) return false
        if (showLoading) updateSnapshot { it.copy(isLoading = true, error = null) }

        val since = if (fullRefresh) null else internalState.value.lastRemoteTimestamp
        val options = MessageSyncOptions(since = since, fullRefresh = fullRefresh)
        val result = repository.loadMessages(config, options)
        result.fold(
            onSuccess = { messages ->
                mergeRaw(messages, incremental = !fullRefresh)
                updateSnapshot { it.copy(isLoading = false, error = null) }
            },
            onFailure = { e ->
                if (showLoading) {
                    updateSnapshot {
                        it.copy(isLoading = false, error = UiText.Dynamic(ErrorTexts.from(e)))
                    }
                }
            },
        )
        return result.isSuccess
    }

    private suspend fun mergeRaw(incoming: List<Message>, incremental: Boolean) {
        stateLock.withLock {
            val state = internalState.value
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
            // remote is sorted ascending; latest server time is O(1) via last().
            val lastRemoteTs = remote.lastOrNull()?.timestamp
            internalState.value = state.copy(
                rawMessages = merged,
                pendingOps = newPending,
                lastRemoteTimestamp = lastRemoteTs ?: state.lastRemoteTimestamp,
            )
        }
        publishSnapshot()
    }

    private fun publishSnapshot(clearError: Boolean = false) {
        val state = internalState.value
        val all = state.rawMessages
        val visible = if (all.size <= displayLimit) all else all.takeLast(displayLimit)
        _snapshot.update { current ->
            ChatMessagesSnapshot(
                internalState = state,
                visibleMessages = visible,
                canLoadMore = all.size > displayLimit,
                isLoading = current.isLoading,
                isLoadingMore = current.isLoadingMore,
                error = if (clearError) null else current.error,
            )
        }
    }

    private fun updateSnapshot(transform: (ChatMessagesSnapshot) -> ChatMessagesSnapshot) {
        _snapshot.update(transform)
    }

    companion object {
        private const val POLL_INTERVAL_MS = 5_000L
        private const val FAST_POLL_INTERVAL_MS = 2_000L
        private const val FAST_POLL_DURATION_MS = 30_000L
        private const val MAX_BACKOFF_MS = 60_000L
    }
}
