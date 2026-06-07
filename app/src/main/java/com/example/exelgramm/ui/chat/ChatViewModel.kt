package com.example.exelgramm.ui.chat

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.exelgramm.core.ErrorTexts
import com.example.exelgramm.core.TimeFormats
import com.example.exelgramm.data.local.SessionStore
import com.example.exelgramm.data.local.UserSession
import com.example.exelgramm.data.repository.ChatRepository
import com.example.exelgramm.data.repository.SaveChatConfigUseCase
import com.example.exelgramm.domain.model.Message
import com.example.exelgramm.domain.model.MessageType
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class ChatUiState(
    val session: UserSession = UserSession(),
    val messages: List<MessageUiItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val inputText: String = "",
    val inputType: String = MessageType.TEXT,
    val sheetUrlDraft: String = "",
    val webAppUrlDraft: String = "",
    val sheetNameDraft: String = "",
)

sealed interface ChatEffect {
    data class ShowError(@param:StringRes val resId: Int) : ChatEffect
}

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val sessionStore: SessionStore,
    private val repository: ChatRepository,
    private val saveChatConfigUseCase: SaveChatConfigUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _effects = Channel<ChatEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private var rawMessages: List<Message> = emptyList()
    private var pollJob: Job? = null

    init {
        viewModelScope.launch {
            sessionStore.session.collect { session ->
                val previousDisplayName = _uiState.value.session.displayName
                _uiState.update { state ->
                    state.copy(
                        session = session,
                        sheetUrlDraft = if (state.sheetUrlDraft.isEmpty()) session.sheetUrl else state.sheetUrlDraft,
                        webAppUrlDraft = if (state.webAppUrlDraft.isEmpty()) session.webAppUrl else state.webAppUrlDraft,
                        sheetNameDraft = if (state.sheetNameDraft.isEmpty()) session.sheetName else state.sheetNameDraft,
                    )
                }

                if (session.displayName != previousDisplayName) {
                    publishMessages(session)
                }

                if (session.isChatConfigured) {
                    refresh(showLoading = true)
                    startPolling()
                } else {
                    stopPolling()
                    rawMessages = emptyList()
                    _uiState.update { it.copy(messages = emptyList()) }
                }
            }
        }
    }

    fun refresh(showLoading: Boolean = true) {
        if (!_uiState.value.session.isChatConfigured) return
        viewModelScope.launch { syncMessages(showLoading) }
    }

    fun onInputChanged(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun onSheetUrlChanged(text: String) { _uiState.update { it.copy(sheetUrlDraft = text) } }
    fun onWebAppUrlChanged(text: String) { _uiState.update { it.copy(webAppUrlDraft = text) } }
    fun onSheetNameChanged(text: String) { _uiState.update { it.copy(sheetNameDraft = text) } }

    fun toggleInputType() {
        val current = _uiState.value.inputType
        _uiState.update {
            it.copy(inputType = if (current == MessageType.TEXT) MessageType.IMPORTANT else MessageType.TEXT)
        }
    }

    fun sendMessage() {
        val session = _uiState.value.session
        val text = _uiState.value.inputText.trim()
        val type = _uiState.value.inputType
        if (text.isEmpty() || !session.isChatConfigured) return

        val optimistic = Message(
            id = UUID.randomUUID().toString(),
            timestamp = TimeFormats.nowIsoUtc(),
            author = session.displayName,
            text = text,
            type = type,
        )

        optimisticUpdate(
            apply = {
                rawMessages = (rawMessages + optimistic).sortedBy { it.timestamp }
                _uiState.update { it.copy(inputText = "", inputType = MessageType.TEXT, error = null) }
            },
            rollback = {
                rawMessages = rawMessages.filterNot { it.id == optimistic.id }
                _uiState.update { it.copy(inputText = text) }
            },
            request = {
                repository.sendMessage(session, optimistic).onSuccess { refresh(showLoading = false) }
            },
        )
    }

    fun editMessage(messageId: String, updatedText: String) {
        val session = _uiState.value.session
        val text = updatedText.trim()
        if (!session.isChatConfigured || text.isEmpty()) return
        val currentMessage = rawMessages.firstOrNull { it.id == messageId } ?: return
        if (!currentMessage.isMine(session.displayName)) return
        if (currentMessage.text == text) return

        optimisticUpdate(
            apply = {
                rawMessages = rawMessages.map { message ->
                    if (message.id == messageId) message.copy(text = text) else message
                }
            },
            rollback = {
                rawMessages = rawMessages.map { message ->
                    if (message.id == messageId) message.copy(text = currentMessage.text) else message
                }
            },
            request = { repository.updateMessage(session, messageId, text) },
        )
    }

    fun deleteMessage(messageId: String) {
        val session = _uiState.value.session
        if (!session.isChatConfigured) return
        val currentMessage = rawMessages.firstOrNull { it.id == messageId } ?: return
        if (!currentMessage.isMine(session.displayName)) return

        optimisticUpdate(
            apply = { rawMessages = rawMessages.filterNot { it.id == messageId } },
            rollback = {
                rawMessages = (rawMessages + currentMessage).sortedBy { it.timestamp }
            },
            request = { repository.deleteMessage(session, messageId) },
        )
    }

    fun saveChatConfig(sheetUrl: String, webAppUrl: String, sheetName: String) {
        viewModelScope.launch {
            when (val result = saveChatConfigUseCase(sheetUrl, webAppUrl, sheetName)) {
                is SaveChatConfigUseCase.Result.ValidationError ->
                    _effects.send(ChatEffect.ShowError(result.errorResId))
                is SaveChatConfigUseCase.Result.Success -> Unit
            }
        }
    }

    private fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            var backoffMs = POLL_INTERVAL_MS
            while (isActive) {
                delay(backoffMs)
                if (!_uiState.value.session.isChatConfigured) continue
                if (syncMessages(showLoading = false)) {
                    backoffMs = POLL_INTERVAL_MS
                } else {
                    backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
                }
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    private suspend fun syncMessages(showLoading: Boolean): Boolean {
        val session = _uiState.value.session
        if (!session.isChatConfigured) return false
        if (showLoading) _uiState.update { it.copy(isLoading = true, error = null) }

        val result = repository.loadMessages(session)
        result.onSuccess { messages ->
            mergeRaw(messages)
            publishMessages(_uiState.value.session)
            if (showLoading) {
                _uiState.update { it.copy(isLoading = false, error = null) }
            } else {
                _uiState.update { it.copy(error = null) }
            }
        }
        result.onFailure { e ->
            if (showLoading) {
                _uiState.update { it.copy(isLoading = false, error = ErrorTexts.from(e)) }
            }
        }
        return result.isSuccess
    }

    private fun optimisticUpdate(
        apply: () -> Unit,
        rollback: () -> Unit,
        request: suspend () -> Result<*>,
    ) {
        apply()
        publishMessages(_uiState.value.session)
        viewModelScope.launch {
            request().onFailure { e ->
                rollback()
                publishMessages(_uiState.value.session)
                _uiState.update { it.copy(error = ErrorTexts.from(e)) }
            }
        }
    }

    private fun mergeRaw(remote: List<Message>) {
        // Сервер считается источником истины: так корректно отражаются удаления.
        rawMessages = remote.sortedBy { it.timestamp }
    }

    private fun publishMessages(session: UserSession) {
        val displayName = session.displayName
        _uiState.update { it.copy(messages = rawMessages.map { m -> m.toUiItem(displayName) }) }
    }

    private fun Message.toUiItem(displayName: String): MessageUiItem =
        if (isMine(displayName)) {
            MessageUiItem.Outgoing(id, text, TimeFormats.formatChatTime(timestamp), type)
        } else {
            MessageUiItem.Incoming(id, author, text, TimeFormats.formatChatTime(timestamp), type)
        }

    override fun onCleared() {
        stopPolling()
        super.onCleared()
    }

    companion object {
        private const val POLL_INTERVAL_MS = 5_000L
        private const val MAX_BACKOFF_MS = 60_000L
    }
}
