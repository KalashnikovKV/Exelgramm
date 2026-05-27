package com.example.exelgramm.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.exelgramm.core.PasswordUtils
import com.example.exelgramm.data.local.SessionStore
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

sealed interface LoginEffect {
    data object NavigateToMain : LoginEffect
    data class ShowError(val message: String) : LoginEffect
}

class LoginViewModel(private val sessionStore: SessionStore) : ViewModel() {

    private val _effects = Channel<LoginEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    fun submit(username: String, password: String) {
        val trimmedUsername = username.trim()
        if (trimmedUsername.isBlank() || password.isBlank()) {
            sendEffect(LoginEffect.ShowError("Заполните все поля"))
            return
        }

        viewModelScope.launch {
            val session = sessionStore.session.first()

            if (!session.isRegistered) {
                if (password.length < 4) {
                    _effects.send(LoginEffect.ShowError("Пароль должен содержать не менее 4 символов"))
                    return@launch
                }
                sessionStore.saveCredentials(trimmedUsername, PasswordUtils.hash(password))
                _effects.send(LoginEffect.NavigateToMain)
            } else {
                val valid = session.username == trimmedUsername &&
                    session.passwordHash == PasswordUtils.hash(password)
                if (valid) {
                    sessionStore.login()
                    _effects.send(LoginEffect.NavigateToMain)
                } else {
                    _effects.send(LoginEffect.ShowError("Неверный логин или пароль"))
                }
            }
        }
    }

    private fun sendEffect(effect: LoginEffect) {
        viewModelScope.launch { _effects.send(effect) }
    }

    class Factory(private val sessionStore: SessionStore) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            LoginViewModel(sessionStore) as T
    }
}
