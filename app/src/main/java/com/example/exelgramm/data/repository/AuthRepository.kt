package com.example.exelgramm.data.repository

import com.example.exelgramm.core.PasswordUtils
import com.example.exelgramm.data.local.SessionStore
import com.example.exelgramm.domain.model.AuthState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Контракт для аутентификации. Изолирует UI/ViewModel от SessionStore
 * и упрощает замену механизма авторизации (например, на Google OAuth).
 */
interface AuthRepository {
    val authState: Flow<AuthState>
    /** @return true, если credentials совпали */
    suspend fun login(username: String, password: String): Boolean
    suspend fun register(username: String, password: String)
    suspend fun logout()
}

class SessionAuthRepository(private val store: SessionStore) : AuthRepository {

    override val authState: Flow<AuthState> = store.session.map { s ->
        AuthState(
            username = s.username,
            isLoggedIn = s.isLoggedIn,
            isRegistered = s.isRegistered,
        )
    }

    override suspend fun login(username: String, password: String): Boolean {
        val session = store.session.first()
        val valid = if (session.passwordSalt.isBlank()) {
            // Backward compat: аккаунт создан с SHA-256 без соли
            session.username == username && session.passwordHash == PasswordUtils.sha256(password)
        } else {
            session.username == username && PasswordUtils.verify(password, session.passwordHash, session.passwordSalt)
        }
        if (valid) {
            // Мигрируем legacy-хэш на PBKDF2 при первом успешном входе
            if (session.passwordSalt.isBlank()) {
                val salt = PasswordUtils.generateSalt()
                store.saveCredentials(username, PasswordUtils.hash(password, salt), salt)
            } else {
                store.login()
            }
        }
        return valid
    }

    override suspend fun register(username: String, password: String) {
        val salt = PasswordUtils.generateSalt()
        store.saveCredentials(username, PasswordUtils.hash(password, salt), salt)
    }

    override suspend fun logout() = store.logout()
}
