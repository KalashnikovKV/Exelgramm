package com.example.exelgramm.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.exelgramm.core.ErrorTexts
import com.example.exelgramm.core.TimeFormats
import com.example.exelgramm.core.UiText
import com.example.exelgramm.data.local.AuthSession
import com.example.exelgramm.data.local.ChatConfig
import com.example.exelgramm.data.local.SessionProvider
import com.example.exelgramm.data.repository.DeleteMessageUseCase
import com.example.exelgramm.data.repository.SendMessageUseCase
import com.example.exelgramm.data.repository.UpdateMessageUseCase
import com.example.exelgramm.domain.model.Message
import com.example.exelgramm.domain.model.MessageType
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

@HiltViewModel
class ChatViewModel @Inject constructor(
    sessionProvider: SessionProvider,
    coordinatorFactory: ChatSyncCoordinator.Factory,
    private val sendMessageUseCase: SendMessageUseCase,
    private val updateMessageUseCase: UpdateMessageUseCase,
    private val deleteMessageUseCase: DeleteMessageUseCase,
) : ViewModel() {

    private val syncCoordinator = coordinatorFactory.create(viewModelScope)

    private val inputText = MutableStateFlow("")
    private val inputType = MutableStateFlow(MessageType.TEXT)
    private val localError = MutableStateFlow<UiText?>(null)

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    /**
     * Маппинг сообщений в UI зависит ТОЛЬКО от снимка синхронизации и имени пользователя.
     * Вынесен из общего combine, чтобы ввод текста (каждое нажатие клавиши) не вызывал
     * повторный маппинг всего окна и форматирование времени. Считается вне main-потока.
     */
    private val messagesUi: Flow<List<MessageUiItem>> =
        combine(syncCoordinator.snapshot, sessionProvider.authSession) { snap, auth ->
            snap to auth.displayName
        }
            .distinctUntilChanged()
            .map { (snap, displayName) -> mapMessagesToUi(snap, displayName) }
            .flowOn(Dispatchers.Default)

    init {
        syncCoordinator.start()
        viewModelScope.launch {
            combine(
                combine(
                    sessionProvider.authSession,
                    sessionProvider.chatConfig,
                    syncCoordinator.snapshot,
                ) { auth, config, snap -> Triple(auth, config, snap) },
                combine(inputText, inputType, localError) { text, type, err ->
                    Triple(text, type, err)
                },
                messagesUi,
            ) { (auth, config, snap), (text, type, err), messages ->
                ChatUiState(
                    auth = auth,
                    chatConfig = config,
                    messages = messages,
                    isLoading = snap.isLoading,
                    isLoadingMore = snap.isLoadingMore,
                    canLoadMore = snap.canLoadMore,
                    error = err ?: snap.error,
                    inputText = text,
                    inputType = type,
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun setPollingActive(active: Boolean) {
        syncCoordinator.setPollingActive(active)
    }

    fun refresh(showLoading: Boolean = true) {
        syncCoordinator.refresh(_uiState.value.chatConfig, showLoading = showLoading)
    }

    fun loadMoreHistory() {
        syncCoordinator.loadMoreHistory()
    }

    fun onInputChanged(text: String) {
        inputText.value = text
    }

    fun toggleInputType() {
        inputType.update {
            when (it) {
                MessageType.TEXT -> MessageType.IMPORTANT
                MessageType.IMPORTANT -> MessageType.TEXT
            }
        }
    }

    fun sendMessage() {
        val auth = _uiState.value.auth
        val config = _uiState.value.chatConfig
        val text = inputText.value.trim()
        val type = inputType.value
        if (text.isEmpty() || !config.isConfigured) return

        val optimistic = Message(
            id = UUID.randomUUID().toString(),
            timestamp = TimeFormats.now(),
            author = auth.displayName,
            text = text,
            type = type,
        )

        viewModelScope.launch {
            syncCoordinator.updateInternal { state ->
                state.copy(
                    pendingOps = state.pendingOps.copy(sends = state.pendingOps.sends + optimistic.id),
                    failedIds = state.failedIds - optimistic.id,
                    rawMessages = (state.rawMessages + optimistic).sortedBy { it.timestamp },
                )
            }
            localError.value = null
            inputText.value = ""
            inputType.value = MessageType.TEXT

            sendMessageUseCase(config, optimistic).fold(
                onSuccess = {
                    syncCoordinator.triggerFastPolling()
                    syncCoordinator.syncIncremental(config)
                },
                onFailure = { e ->
                    syncCoordinator.updateInternal { state ->
                        state.copy(
                            pendingOps = state.pendingOps.copy(sends = state.pendingOps.sends - optimistic.id),
                            failedIds = state.failedIds + optimistic.id,
                        )
                    }
                    localError.value = UiText.Dynamic(ErrorTexts.from(e))
                    inputText.value = text
                },
            )
        }
    }

    fun editMessage(messageId: String, updatedText: String) {
        val auth = _uiState.value.auth
        val config = _uiState.value.chatConfig
        val text = updatedText.trim()
        if (!config.isConfigured || text.isEmpty()) return
        val currentMessage = syncCoordinator.currentState.rawMessages
            .firstOrNull { it.id == messageId } ?: return
        if (!currentMessage.isMine(auth.displayName)) return
        if (currentMessage.text == text) return

        optimisticUpdate(
            messageId = messageId,
            apply = {
                syncCoordinator.updateInternal { state ->
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
                syncCoordinator.updateInternal { state ->
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
        val currentMessage = syncCoordinator.currentState.rawMessages
            .firstOrNull { it.id == messageId } ?: return
        if (!currentMessage.isMine(auth.displayName)) return

        optimisticUpdate(
            messageId = messageId,
            apply = {
                syncCoordinator.updateInternal { state ->
                    state.copy(
                        pendingOps = state.pendingOps.copy(deletes = state.pendingOps.deletes + messageId),
                        failedIds = state.failedIds - messageId,
                        rawMessages = state.rawMessages.filterNot { it.id == messageId },
                    )
                }
            },
            rollback = {
                syncCoordinator.updateInternal { state ->
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

    private fun optimisticUpdate(
        messageId: String,
        apply: suspend () -> Unit,
        rollback: suspend () -> Unit,
        request: suspend () -> Result<*>,
    ) {
        viewModelScope.launch {
            apply()
            request().fold(
                onSuccess = {
                    syncCoordinator.updateInternal { state ->
                        state.copy(failedIds = state.failedIds - messageId)
                    }
                },
                onFailure = { e ->
                    rollback()
                    localError.value = UiText.Dynamic(ErrorTexts.from(e))
                },
            )
        }
    }

    private fun mapMessagesToUi(snap: ChatMessagesSnapshot, displayName: String): List<MessageUiItem> {
        val state = snap.internalState
        val pendingSends = state.pendingOps.sends
        val failedIds = state.failedIds
        return snap.visibleMessages.map { m ->
            m.toUiItem(
                displayName = displayName,
                isPending = m.id in pendingSends,
                hasError = m.id in failedIds,
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
}
