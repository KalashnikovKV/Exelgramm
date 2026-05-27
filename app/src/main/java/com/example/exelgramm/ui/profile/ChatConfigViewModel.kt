package com.example.exelgramm.ui.profile

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.exelgramm.core.DEFAULT_SHEET_NAME
import com.example.exelgramm.data.local.SessionStore
import com.example.exelgramm.data.repository.ChatConfigValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatConfigUiState(
    val sheetUrl: String = "",
    val webAppUrl: String = "",
    val sheetName: String = DEFAULT_SHEET_NAME,
)

sealed interface ChatConfigEffect {
    data object NavigateBack : ChatConfigEffect
    data class ShowError(@param:StringRes val resId: Int) : ChatConfigEffect
    data object ShowSaved : ChatConfigEffect
}

@HiltViewModel
class ChatConfigViewModel @Inject constructor(
    private val store: SessionStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatConfigUiState())
    val uiState: StateFlow<ChatConfigUiState> = _uiState.asStateFlow()

    private val _effects = Channel<ChatConfigEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        viewModelScope.launch {
            val s = store.session.first()
            _uiState.update {
                it.copy(sheetUrl = s.sheetUrl, webAppUrl = s.webAppUrl, sheetName = s.sheetName)
            }
        }
    }

    fun save(sheetUrl: String, webAppUrl: String, sheetName: String) {
        when (val result = ChatConfigValidator.validate(sheetUrl, webAppUrl)) {
            is ChatConfigValidator.Result.Failure ->
                viewModelScope.launch { _effects.send(ChatConfigEffect.ShowError(result.errorResId)) }

            is ChatConfigValidator.Result.Success ->
                viewModelScope.launch {
                    store.saveChatConfig(
                        sheetUrl = sheetUrl,
                        spreadsheetId = result.spreadsheetId,
                        sheetName = sheetName.ifBlank { DEFAULT_SHEET_NAME },
                        webAppUrl = webAppUrl,
                    )
                    _effects.send(ChatConfigEffect.ShowSaved)
                    _effects.send(ChatConfigEffect.NavigateBack)
                }
        }
    }
}
