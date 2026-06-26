package com.example.exelgramm.domain.model

/**
 * Authentication state snapshot. No hash/salt — only what UI and logic need.
 */
data class AuthState(
    val username: String = "",
    val isLoggedIn: Boolean = false,
    val isRegistered: Boolean = false,
)
