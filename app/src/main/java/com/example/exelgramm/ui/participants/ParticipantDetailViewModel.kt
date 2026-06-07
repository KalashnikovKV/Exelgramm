package com.example.exelgramm.ui.participants

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.exelgramm.core.TimeFormats
import com.example.exelgramm.data.local.SessionStore
import com.example.exelgramm.data.local.db.MessageDao
import com.example.exelgramm.data.local.db.MessageEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ParticipantDetailUiState(
    val authorName: String = "",
    val totalMessages: Int = 0,
    val textMessages: Int = 0,
    val importantMessages: Int = 0,
    val firstMessageTime: String = "",
    val lastMessageTime: String = "",
    val messages: List<MessageEntity> = emptyList(),
)

@HiltViewModel
class ParticipantDetailViewModel @Inject constructor(
    private val sessionStore: SessionStore,
    private val messageDao: MessageDao,
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
            val session = sessionStore.session.first()
            if (!session.isChatConfigured) return@launch
            val all = messageDao.getAll(session.spreadsheetId, session.sheetName)
            val msgs = all.filter { it.author.equals(authorName, ignoreCase = true) }
                .sortedBy { it.timestamp }
            val stats = msgs.participantStats()

            _uiState.update {
                it.copy(
                    authorName = authorName,
                    totalMessages = stats.totalMessages,
                    textMessages = stats.textMessages,
                    importantMessages = stats.importantMessages,
                    firstMessageTime = msgs.firstOrNull()?.timestamp
                        ?.let(TimeFormats::formatFullDateTime)
                        .orEmpty(),
                    lastMessageTime = msgs.lastOrNull()?.timestamp
                        ?.let(TimeFormats::formatFullDateTime)
                        .orEmpty(),
                    messages = msgs,
                )
            }
        }
    }
}
