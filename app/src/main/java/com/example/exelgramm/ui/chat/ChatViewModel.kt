package com.example.exelgramm.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.exelgramm.data.local.SessionStore
import com.example.exelgramm.data.local.UserSession
import com.example.exelgramm.core.ErrorTexts
import com.example.exelgramm.core.TimeFormats
import com.example.exelgramm.data.repository.ChatRepository
import com.example.exelgramm.domain.model.Message
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class ChatUiState(
    val session: UserSession = UserSession(),
    val messages: List<Message> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val inputText: String = "",
)

class ChatViewModel(
    private val sessionStore: SessionStore,
    private val repository: ChatRepository = ChatRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var pollJob: Job? = null

    init {
        viewModelScope.launch {
            sessionStore.session.collect { session ->
                _uiState.update { it.copy(session = session) }
                if (session.isChatConfigured) {
                    refresh(showLoading = true)
                    startPolling()
                } else {
                    stopPolling()
                }
            }
        }
    }

    fun refresh(showLoading: Boolean = true) {
        val session = _uiState.value.session
        if (!session.isChatConfigured) return
        viewModelScope.launch {
            if (showLoading) {
                _uiState.update { it.copy(isLoading = true, error = null) }
            }
            repository.loadMessages(session)
                .onSuccess { messages ->
                    _uiState.update { state ->
                        state.copy(
                            messages = mergeMessages(state.messages, messages),
                            isLoading = false,
                            error = null,
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isLoading = false, error = ErrorTexts.from(e))
                    }
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

        _uiState.update { state ->
            state.copy(
                inputText = "",
                error = null,
                messages = mergeMessages(state.messages, listOf(optimistic)),
            )
        }

        viewModelScope.launch {
            repository.sendMessage(session, optimistic)
                .onSuccess {
                    refresh(showLoading = false)
                }
                .onFailure { e ->
                    _uiState.update { state ->
                        state.copy(
                            error = ErrorTexts.from(e),
                            messages = state.messages.filterNot { it.id == optimistic.id },
                            inputText = text,
                        )
                    }
                }
        }
    }

    private fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                val session = _uiState.value.session
                if (!session.isChatConfigured) continue
                repository.loadMessages(session)
                    .onSuccess { messages ->
                        _uiState.update { state ->
                            state.copy(
                                messages = mergeMessages(state.messages, messages),
                                error = null,
                            )
                        }
                    }
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    private fun mergeMessages(local: List<Message>, remote: List<Message>): List<Message> {
        if (remote.isEmpty()) return local
        return (remote + local)
            .distinctBy { it.id }
            .sortedBy { it.timestamp }
    }

    override fun onCleared() {
        stopPolling()
        super.onCleared()
    }

    class Factory(private val sessionStore: SessionStore) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ChatViewModel(sessionStore) as T
        }
    }

    companion object {
        private const val POLL_INTERVAL_MS = 5_000L
    }
}
