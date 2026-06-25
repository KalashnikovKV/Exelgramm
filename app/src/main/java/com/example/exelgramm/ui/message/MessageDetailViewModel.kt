package com.example.exelgramm.ui.message

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.exelgramm.core.ErrorTexts
import com.example.exelgramm.core.UiText
import com.example.exelgramm.data.local.SessionStore
import com.example.exelgramm.data.repository.ChatRepository
import com.example.exelgramm.domain.model.Message
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
    val message: Message? = null,
    val isMine: Boolean = false,
    val notFound: Boolean = false,
    val error: UiText? = null,
)

@HiltViewModel
class MessageDetailViewModel @Inject constructor(
    private val sessionStore: SessionStore,
    private val chatRepository: ChatRepository,
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
            _uiState.update { it.copy(isLoading = true, notFound = false, error = null) }
            val config = sessionStore.chatConfig.first()
            val auth = sessionStore.authSession.first()
            if (!config.isConfigured || messageId.isBlank()) {
                _uiState.update { it.copy(isLoading = false, notFound = true) }
                return@launch
            }
            chatRepository.getMessageById(config, messageId).fold(
                onSuccess = { message ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            message = message,
                            isMine = message?.isMine(auth.displayName) == true,
                            notFound = message == null,
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            notFound = true,
                            error = UiText.Dynamic(ErrorTexts.from(e)),
                        )
                    }
                },
            )
        }
    }
}
