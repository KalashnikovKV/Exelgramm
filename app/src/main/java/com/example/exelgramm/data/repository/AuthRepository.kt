package com.example.exelgramm.data.repository

import com.example.exelgramm.core.PasswordUtils
import com.example.exelgramm.data.local.AuthStore
import com.example.exelgramm.data.local.SessionStore
import com.example.exelgramm.domain.model.AuthState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

interface AuthRepository {
    val authState: Flow<AuthState>
    /** @return true if credentials matched */
    suspend fun login(username: String, password: String): Boolean
    suspend fun register(username: String, password: String)
    suspend fun logout()
}

@Singleton
class SessionAuthRepository @Inject constructor(
    private val authStore: AuthStore,
    private val sessionStore: SessionStore,
) : AuthRepository {

    override val authState: Flow<AuthState> = authStore.state.map { auth ->
        AuthState(
            username = auth.username,
            isLoggedIn = auth.isLoggedIn,
            isRegistered = auth.isRegistered,
        )
    }

    override suspend fun login(username: String, password: String): Boolean {
        val auth = authStore.state.first()
        val valid = if (auth.passwordSalt.isBlank()) {
            // Backward compat: legacy unsalted SHA-256
            auth.username == username && auth.passwordHash == PasswordUtils.sha256(password)
        } else {
            auth.username == username && PasswordUtils.verify(password, auth.passwordHash, auth.passwordSalt)
        }
        if (valid) {
            // Rehash on login if: (a) legacy unsalted SHA-256, or
            // (b) PBKDF2 with outdated iteration count — upgrade without locking users out.
            val needsUpgrade = auth.passwordSalt.isBlank() ||
                PasswordUtils.needsRehash(auth.passwordHash)
            if (needsUpgrade) {
                val salt = PasswordUtils.generateSalt()
                authStore.saveCredentials(username, PasswordUtils.hash(password, salt), salt)
            } else {
                authStore.login()
            }
        }
        return valid
    }

    override suspend fun register(username: String, password: String) {
        val salt = PasswordUtils.generateSalt()
        authStore.saveCredentials(username, PasswordUtils.hash(password, salt), salt)
    }

    override suspend fun logout() = sessionStore.logout()
}
