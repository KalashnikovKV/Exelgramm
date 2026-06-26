package com.example.exelgramm.ui.participants

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.exelgramm.core.TimeFormats
import com.example.exelgramm.data.local.SessionStore
import com.example.exelgramm.data.repository.LoadParticipantDetailUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ParticipantMessageItem(
    val id: String,
    val text: String,
    val time: String,
    val isImportant: Boolean,
)

data class ParticipantDetailUiState(
    val authorName: String = "",
    val totalMessages: Int = 0,
    val textMessages: Int = 0,
    val importantMessages: Int = 0,
    val firstMessageTime: String = "",
    val lastMessageTime: String = "",
    val messages: List<ParticipantMessageItem> = emptyList(),
)

@HiltViewModel
class ParticipantDetailViewModel @Inject constructor(
    private val sessionStore: SessionStore,
    private val loadParticipantDetailUseCase: LoadParticipantDetailUseCase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val authorName: String = savedStateHandle.get<String>("authorName").orEmpty()

    private val _uiState = MutableStateFlow(ParticipantDetailUiState(authorName = authorName))
    val uiState: StateFlow<ParticipantDetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            val config = sessionStore.chatConfig.first()
            if (!config.isConfigured) return@launch
            loadParticipantDetailUseCase(config, authorName).onSuccess { detail ->
                _uiState.update {
                    it.copy(
                        authorName = detail.authorName,
                        totalMessages = detail.totalMessages,
                        textMessages = detail.textMessages,
                        importantMessages = detail.importantMessages,
                        firstMessageTime = detail.firstMessageTime
                            ?.let(TimeFormats::formatFullDateTime)
                            .orEmpty(),
                        lastMessageTime = detail.lastMessageTime
                            ?.let(TimeFormats::formatFullDateTime)
                            .orEmpty(),
                        messages = detail.messages.map { message ->
                            ParticipantMessageItem(
                                id = message.id,
                                text = message.text,
                                time = TimeFormats.formatFullDateTime(message.timestamp),
                                isImportant = message.isImportant,
                            )
                        },
                    )
                }
            }
        }
    }
}
