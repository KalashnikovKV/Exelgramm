package com.example.exelgramm.ui.participants

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.exelgramm.core.TimeFormats
import com.example.exelgramm.data.local.SessionStore
import com.example.exelgramm.data.local.db.MessageDao
import com.example.exelgramm.domain.model.MessageType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ParticipantItem(
    val author: String,
    val totalMessages: Int,
    val textMessages: Int,
    val importantMessages: Int,
    val lastMessageTime: String,
)

@HiltViewModel
class ParticipantsViewModel @Inject constructor(
    private val sessionStore: SessionStore,
    private val messageDao: MessageDao,
) : ViewModel() {

    private val _participants = MutableStateFlow<List<ParticipantItem>>(emptyList())
    val participants: StateFlow<List<ParticipantItem>> = _participants.asStateFlow()

    private val _isConfigured = MutableStateFlow(true)
    val isConfigured: StateFlow<Boolean> = _isConfigured.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val session = sessionStore.session.first()
            if (!session.isChatConfigured) {
                _isConfigured.value = false
                _participants.value = emptyList()
                return@launch
            }
            _isConfigured.value = true
            val messages = messageDao.getAll(session.spreadsheetId, session.sheetName)
            val grouped = messages.groupBy { it.author }
            val items = grouped.map { (author, msgs) ->
                val textCount = msgs.count { it.type == MessageType.TEXT }
                val importantCount = msgs.count { it.type == MessageType.IMPORTANT }
                val lastTime = msgs.maxByOrNull { it.timestamp }?.timestamp
                    ?.let { TimeFormats.formatChatTime(it) }.orEmpty()
                ParticipantItem(
                    author = author,
                    totalMessages = msgs.size,
                    textMessages = textCount,
                    importantMessages = importantCount,
                    lastMessageTime = lastTime,
                )
            }.sortedByDescending { it.totalMessages }
            _participants.value = items
        }
    }
}
