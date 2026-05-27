package com.example.exelgramm.domain.model

/**
 * Снимок состояния аутентификации. Не содержит хэш/соль — только то,
 * что нужно UI и бизнес-логике.
 */
data class AuthState(
    val username: String = "",
    val isLoggedIn: Boolean = false,
    val isRegistered: Boolean = false,
)
