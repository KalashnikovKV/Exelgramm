package com.example.exelgramm.ui.message

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

data class MessageDetailUiState(
    val isLoading: Boolean = true,
    val message: MessageEntity? = null,
    val isMine: Boolean = false,
    val notFound: Boolean = false,
)

@HiltViewModel
class MessageDetailViewModel @Inject constructor(
    private val sessionStore: SessionStore,
    private val messageDao: MessageDao,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val messageId: String = savedStateHandle.get<String>("messageId").orEmpty()

    private val _uiState = MutableStateFlow(MessageDetailUiState())
    val uiState: StateFlow<MessageDetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, notFound = false) }
            val session = sessionStore.session.first()
            if (!session.isChatConfigured || messageId.isBlank()) {
                _uiState.update { it.copy(isLoading = false, notFound = true) }
                return@launch
            }
            val entity = messageDao.getById(messageId, session.spreadsheetId, session.sheetName)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    message = entity,
                    isMine = entity?.author.equals(session.displayName, ignoreCase = true),
                    notFound = entity == null,
                )
            }
        }
    }
}
