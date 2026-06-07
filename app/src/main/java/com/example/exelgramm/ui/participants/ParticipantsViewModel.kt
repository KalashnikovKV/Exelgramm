package com.example.exelgramm.ui.participants

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.exelgramm.data.local.SessionStore
import com.example.exelgramm.data.local.db.MessageDao
import com.example.exelgramm.data.repository.ChatRepository
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
    private val chatRepository: ChatRepository,
) : ViewModel() {

    private val _participants = MutableStateFlow<List<ParticipantItem>>(emptyList())
    val participants: StateFlow<List<ParticipantItem>> = _participants.asStateFlow()

    private val _isConfigured = MutableStateFlow(true)
    val isConfigured: StateFlow<Boolean> = _isConfigured.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /**
     * @param syncFromRemote при true — сначала загрузка с сервера (обновляет Room-кэш)
     * @param showLoading индикатор pull-to-refresh
     */
    fun refresh(syncFromRemote: Boolean = false, showLoading: Boolean = false) {
        viewModelScope.launch {
            val session = sessionStore.session.first()
            if (!session.isChatConfigured) {
                _isConfigured.value = false
                _participants.value = emptyList()
                _isLoading.value = false
                return@launch
            }
            _isConfigured.value = true
            if (showLoading) _isLoading.value = true
            if (syncFromRemote) {
                chatRepository.loadMessages(session)
            }
            val messages = messageDao.getAll(session.spreadsheetId, session.sheetName)
            _participants.value = messages.toParticipantItems()
            _isLoading.value = false
        }
    }
}
