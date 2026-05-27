package com.example.exelgramm.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.exelgramm.R
import com.example.exelgramm.core.UiText
import com.example.exelgramm.data.repository.AuthRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

sealed interface LoginEffect {
    data object NavigateToMain : LoginEffect
    data class ShowError(val message: UiText) : LoginEffect
}

class LoginViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _effects = Channel<LoginEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

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

    class Factory(private val repo: AuthRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            LoginViewModel(repo) as T
    }
}
