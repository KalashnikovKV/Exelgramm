package com.example.exelgramm.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.exelgramm.R
import com.example.exelgramm.core.UiText
import com.example.exelgramm.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val isRegistered: Boolean = false,
    val isLoggedIn: Boolean = false,
)

sealed interface LoginEffect {
    data object NavigateToMain : LoginEffect
    data class ShowError(val message: UiText) : LoginEffect
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    val uiState = authRepository.authState
        .map { LoginUiState(isRegistered = it.isRegistered, isLoggedIn = it.isLoggedIn) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, LoginUiState())

    private val _effects = Channel<LoginEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        viewModelScope.launch {
            val state = authRepository.authState.first()
            if (state.isLoggedIn) {
                _effects.send(LoginEffect.NavigateToMain)
            }
        }
    }

    fun submit(username: String, password: String) {
        val trimmedUsername = username.trim()
        if (trimmedUsername.isBlank() || password.isBlank()) {
            sendError(R.string.error_fields_required)
            return
        }

        viewModelScope.launch {
            val state = authRepository.authState.first()
            if (!state.isRegistered) {
                if (password.length < 4) {
                    _effects.send(LoginEffect.ShowError(UiText.StringResource(R.string.error_password_too_short)))
                    return@launch
                }
                authRepository.register(trimmedUsername, password)
                _effects.send(LoginEffect.NavigateToMain)
            } else {
                val success = authRepository.login(trimmedUsername, password)
                if (success) {
                    _effects.send(LoginEffect.NavigateToMain)
                } else {
                    _effects.send(LoginEffect.ShowError(UiText.StringResource(R.string.error_invalid_credentials)))
                }
            }
        }
    }

    private fun sendError(@androidx.annotation.StringRes resId: Int) {
        viewModelScope.launch {
            _effects.send(LoginEffect.ShowError(UiText.StringResource(resId)))
        }
    }
}
