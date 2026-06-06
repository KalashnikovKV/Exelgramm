package com.example.exelgramm.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Хранилище чувствительных данных аутентификации.
 * Использует EncryptedSharedPreferences (AES256-GCM ключ в Android Keystore).
 */
@Singleton
class AuthStore @Inject constructor(@param:ApplicationContext private val context: Context) {

    data class AuthData(
        val username: String = "",
        /** Пустой salt = legacy SHA-256. */
        val passwordHash: String = "",
        val passwordSalt: String = "",
        val isLoggedIn: Boolean = false,
        /** Unix-timestamp (мс) первой регистрации. 0 = не сохранён (старые аккаунты). */
        val createdAt: Long = 0L,
    ) {
        val isRegistered: Boolean get() = username.isNotBlank() && passwordHash.isNotBlank()
    }

    private val prefs: SharedPreferences by lazy {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        @Suppress("DEPRECATION")
        EncryptedSharedPreferences.create(
            "auth_store_v1",
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private val _state = MutableStateFlow(loadFromPrefs())
    val state: Flow<AuthData> = _state.asStateFlow()

    private fun loadFromPrefs(): AuthData = AuthData(
        username = prefs.getString(KEY_USERNAME, "").orEmpty(),
        passwordHash = prefs.getString(KEY_PASSWORD_HASH, "").orEmpty(),
        passwordSalt = prefs.getString(KEY_PASSWORD_SALT, "").orEmpty(),
        isLoggedIn = prefs.getBoolean(KEY_IS_LOGGED_IN, false),
        createdAt = prefs.getLong(KEY_CREATED_AT, 0L),
    )

    suspend fun saveCredentials(username: String, hash: String, salt: String) =
        withContext(Dispatchers.IO) {
            val existingCreatedAt = prefs.getLong(KEY_CREATED_AT, 0L)
            prefs.edit(commit = true) {
                putString(KEY_USERNAME, username)
                putString(KEY_PASSWORD_HASH, hash)
                putString(KEY_PASSWORD_SALT, salt)
                putBoolean(KEY_IS_LOGGED_IN, true)
                if (existingCreatedAt == 0L) putLong(KEY_CREATED_AT, System.currentTimeMillis())
            }
            _state.value = loadFromPrefs()
        }

    suspend fun login() = withContext(Dispatchers.IO) {
        prefs.edit(commit = true) { putBoolean(KEY_IS_LOGGED_IN, true) }
        _state.value = loadFromPrefs()
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        prefs.edit(commit = true) { putBoolean(KEY_IS_LOGGED_IN, false) }
        _state.value = loadFromPrefs()
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        prefs.edit(commit = true) { clear() }
        _state.value = AuthData()
    }

    companion object {
        const val KEY_USERNAME = "username"
        const val KEY_PASSWORD_HASH = "password_hash"
        const val KEY_PASSWORD_SALT = "password_salt"
        const val KEY_IS_LOGGED_IN = "is_logged_in"
        const val KEY_CREATED_AT = "created_at"
    }
}
