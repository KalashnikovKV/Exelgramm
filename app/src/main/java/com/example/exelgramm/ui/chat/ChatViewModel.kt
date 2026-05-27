package com.example.exelgramm.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.exelgramm.core.DEFAULT_SHEET_NAME
import com.example.exelgramm.core.ErrorTexts
import com.example.exelgramm.core.TimeFormats
import com.example.exelgramm.data.local.SessionStore
import com.example.exelgramm.data.local.UserSession
import com.example.exelgramm.data.repository.ChatRepository
import com.example.exelgramm.domain.model.Message
import java.util.UUID
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
)

sealed interface ChatEffect {
    data class ShowToast(val message: String) : ChatEffect
}

class ChatViewModel(
    private val sessionStore: SessionStore,
    private val repository: ChatRepository = ChatRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _effects = Channel<ChatEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    /** Хранит «сырые» доменные сообщения для корректного merge и ремаппинга при смене имени. */
    private var rawMessages: List<Message> = emptyList()

    private var pollJob: Job? = null

    init {
        viewModelScope.launch {
            sessionStore.session.collect { session ->
                val previousDisplayName = _uiState.value.session.displayName
                _uiState.update { it.copy(session = session) }

                // Перемаппируем если изменился displayName (incoming/outgoing могут поменяться)
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
        val session = _uiState.value.session
        if (!session.isChatConfigured) return
        viewModelScope.launch {
            if (showLoading) _uiState.update { it.copy(isLoading = true, error = null) }
            repository.loadMessages(session)
                .onSuccess { messages ->
                    mergeRaw(messages)
                    publishMessages(_uiState.value.session)
                    _uiState.update { it.copy(isLoading = false, error = null) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = ErrorTexts.from(e)) }
                }
        }
    }

    fun onInputChanged(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun sendMessage() {
        val session = _uiState.value.session
        val text = _uiState.value.inputText.trim()
        if (text.isEmpty() || !session.isChatConfigured) return

        val optimistic = Message(
            id = UUID.randomUUID().toString(),
            timestamp = TimeFormats.nowIsoUtc(),
            author = session.displayName,
            text = text,
        )

        rawMessages = (rawMessages + optimistic).sortedBy { it.timestamp }
        _uiState.update { it.copy(inputText = "", error = null) }
        publishMessages(session)

        viewModelScope.launch {
            repository.sendMessage(session, optimistic)
                .onSuccess { refresh(showLoading = false) }
                .onFailure { e ->
                    rawMessages = rawMessages.filterNot { it.id == optimistic.id }
                    publishMessages(_uiState.value.session)
                    _uiState.update { it.copy(error = ErrorTexts.from(e), inputText = text) }
                }
        }
    }

    /**
     * Сохраняет конфиг чата. [spreadsheetId] уже распарсен во Fragment через SheetLinkParser
     * (или ChatConfigValidator) — ViewModel не дублирует парсинг URL.
     */
    fun saveChatConfig(sheetUrl: String, spreadsheetId: String, webAppUrl: String, sheetName: String) {
        viewModelScope.launch {
            sessionStore.saveChatConfig(
                sheetUrl = sheetUrl,
                spreadsheetId = spreadsheetId,
                sheetName = sheetName.ifBlank { DEFAULT_SHEET_NAME },
                webAppUrl = webAppUrl,
            )
        }
    }

    private fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            var backoffMs = POLL_INTERVAL_MS
            while (isActive) {
                delay(backoffMs)
                val session = _uiState.value.session
                if (!session.isChatConfigured) continue
                repository.loadMessages(session)
                    .onSuccess { messages ->
                        backoffMs = POLL_INTERVAL_MS
                        mergeRaw(messages)
                        publishMessages(_uiState.value.session)
                        _uiState.update { it.copy(error = null) }
                    }
                    .onFailure {
                        // Экспоненциальная задержка при ошибках поллинга
                        backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
                    }
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    private fun mergeRaw(remote: List<Message>) {
        if (remote.isEmpty()) return
        rawMessages = (remote + rawMessages)
            .distinctBy { it.id }
            .sortedBy { it.timestamp }
    }

    private fun publishMessages(session: UserSession) {
        val displayName = session.displayName
        _uiState.update { it.copy(messages = rawMessages.map { m -> m.toUiItem(displayName) }) }
    }

    private fun Message.toUiItem(displayName: String): MessageUiItem =
        if (isMine(displayName)) {
            MessageUiItem.Outgoing(id, text, TimeFormats.formatChatTime(timestamp))
        } else {
            MessageUiItem.Incoming(id, author, text, TimeFormats.formatChatTime(timestamp))
        }

    override fun onCleared() {
        stopPolling()
        super.onCleared()
    }

    class Factory(private val sessionStore: SessionStore) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ChatViewModel(sessionStore) as T
    }

    companion object {
        private const val POLL_INTERVAL_MS = 5_000L
        private const val MAX_BACKOFF_MS = 60_000L
    }
}
