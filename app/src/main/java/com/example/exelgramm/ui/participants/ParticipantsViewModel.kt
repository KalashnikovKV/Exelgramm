package com.example.exelgramm.ui.participants

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.exelgramm.data.local.SessionStore
import com.example.exelgramm.data.repository.LoadParticipantsUseCase
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
    private val loadParticipantsUseCase: LoadParticipantsUseCase,
) : ViewModel() {

    private val _participants = MutableStateFlow<List<ParticipantItem>>(emptyList())
    val participants: StateFlow<List<ParticipantItem>> = _participants.asStateFlow()

    private val _isConfigured = MutableStateFlow(true)
    val isConfigured: StateFlow<Boolean> = _isConfigured.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /**
     * @param syncFromRemote when true, fetch from server first (updates Room cache)
     * @param showLoading pull-to-refresh indicator
     */
    fun refresh(syncFromRemote: Boolean = false, showLoading: Boolean = false) {
        viewModelScope.launch {
            val config = sessionStore.chatConfig.first()
            if (!config.isConfigured) {
                _isConfigured.value = false
                _participants.value = emptyList()
                _isLoading.value = false
                return@launch
            }
            _isConfigured.value = true
            if (showLoading) _isLoading.value = true
            loadParticipantsUseCase(config, syncFromRemote).fold(
                onSuccess = { summaries ->
                    _participants.value = summaries.map { summary ->
                        ParticipantItem(
                            author = summary.author,
                            totalMessages = summary.totalMessages,
                            textMessages = summary.textMessages,
                            importantMessages = summary.importantMessages,
                            lastMessageTime = summary.lastMessageTime,
                        )
                    }
                },
                onFailure = {
                    _participants.value = emptyList()
                },
            )
            _isLoading.value = false
        }
    }
}
