package com.example.exelgramm.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private val Context.authDataStore: DataStore<Preferences> by preferencesDataStore(name = "auth_encrypted_v2")

/**
 * Auth data in Encrypted DataStore (AES-GCM via Android Keystore).
 * One-time migration from legacy EncryptedSharedPreferences (auth_store_v1).
 */
@Singleton
class AuthStore @Inject constructor(@param:ApplicationContext private val context: Context) {

    data class AuthData(
        val username: String = "",
        val passwordHash: String = "",
        val passwordSalt: String = "",
        val isLoggedIn: Boolean = false,
        val createdAt: Long = 0L,
    ) {
        val isRegistered: Boolean get() = username.isNotBlank() && passwordHash.isNotBlank()
    }

    private val initScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(AuthData())

    init {
        initScope.launch {
            migrateFromLegacyIfNeeded()
            _state.value = readAuthData()
        }
    }

    val state: Flow<AuthData> = _state.asStateFlow()

    suspend fun saveCredentials(username: String, hash: String, salt: String) = withContext(Dispatchers.IO) {
        context.authDataStore.edit { prefs ->
            val existingCreatedAt = prefs[KEY_CREATED_AT_ENC]?.let { AuthCrypto.decrypt(it).toLongOrNull() } ?: 0L
            prefs[KEY_USERNAME_ENC] = AuthCrypto.encrypt(username)
            prefs[KEY_PASSWORD_HASH_ENC] = AuthCrypto.encrypt(hash)
            prefs[KEY_PASSWORD_SALT_ENC] = AuthCrypto.encrypt(salt)
            prefs[KEY_IS_LOGGED_IN] = true
            if (existingCreatedAt == 0L) {
                prefs[KEY_CREATED_AT_ENC] = AuthCrypto.encrypt(System.currentTimeMillis().toString())
            }
        }
        _state.value = readAuthData()
    }

    suspend fun login() = withContext(Dispatchers.IO) {
        context.authDataStore.edit { prefs -> prefs[KEY_IS_LOGGED_IN] = true }
        _state.value = readAuthData()
    }

    suspend fun logout() = clear()

    suspend fun clear() = withContext(Dispatchers.IO) {
        context.authDataStore.edit { it.clear() }
        _state.value = AuthData()
    }

    private suspend fun readAuthData(): AuthData {
        val prefs = context.authDataStore.data.first()
        return AuthData(
            username = prefs[KEY_USERNAME_ENC]?.let(AuthCrypto::decrypt).orEmpty(),
            passwordHash = prefs[KEY_PASSWORD_HASH_ENC]?.let(AuthCrypto::decrypt).orEmpty(),
            passwordSalt = prefs[KEY_PASSWORD_SALT_ENC]?.let(AuthCrypto::decrypt).orEmpty(),
            isLoggedIn = prefs[KEY_IS_LOGGED_IN] ?: false,
            createdAt = prefs[KEY_CREATED_AT_ENC]?.let { AuthCrypto.decrypt(it).toLongOrNull() } ?: 0L,
        )
    }

    private suspend fun migrateFromLegacyIfNeeded() {
        val prefs = context.authDataStore.data.first()
        if (prefs[KEY_MIGRATED_FROM_LEGACY] == true) return
        val legacy = runCatching { openLegacyPrefs() }.getOrNull() ?: return
        if (!legacy.contains(LEGACY_KEY_USERNAME)) {
            context.authDataStore.edit { it[KEY_MIGRATED_FROM_LEGACY] = true }
            return
        }
        val username = legacy.getString(LEGACY_KEY_USERNAME, "").orEmpty()
        val hash = legacy.getString(LEGACY_KEY_PASSWORD_HASH, "").orEmpty()
        val salt = legacy.getString(LEGACY_KEY_PASSWORD_SALT, "").orEmpty()
        val isLoggedIn = legacy.getBoolean(LEGACY_KEY_IS_LOGGED_IN, false)
        val createdAt = legacy.getLong(LEGACY_KEY_CREATED_AT, 0L)
        context.authDataStore.edit { store ->
            if (username.isNotBlank()) store[KEY_USERNAME_ENC] = AuthCrypto.encrypt(username)
            if (hash.isNotBlank()) store[KEY_PASSWORD_HASH_ENC] = AuthCrypto.encrypt(hash)
            if (salt.isNotBlank()) store[KEY_PASSWORD_SALT_ENC] = AuthCrypto.encrypt(salt)
            store[KEY_IS_LOGGED_IN] = isLoggedIn
            if (createdAt > 0L) {
                store[KEY_CREATED_AT_ENC] = AuthCrypto.encrypt(createdAt.toString())
            }
            store[KEY_MIGRATED_FROM_LEGACY] = true
        }
        legacy.edit().clear().apply()
        context.deleteSharedPreferences(LEGACY_PREFS_NAME)
    }

    private fun openLegacyPrefs() =
        EncryptedSharedPreferences.create(
            context,
            LEGACY_PREFS_NAME,
            MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )

    companion object {
        private const val LEGACY_PREFS_NAME = "auth_store_v1"
        private const val LEGACY_KEY_USERNAME = "username"
        private const val LEGACY_KEY_PASSWORD_HASH = "password_hash"
        private const val LEGACY_KEY_PASSWORD_SALT = "password_salt"
        private const val LEGACY_KEY_IS_LOGGED_IN = "is_logged_in"
        private const val LEGACY_KEY_CREATED_AT = "created_at"

        val KEY_USERNAME_ENC = stringPreferencesKey("username_enc")
        val KEY_PASSWORD_HASH_ENC = stringPreferencesKey("password_hash_enc")
        val KEY_PASSWORD_SALT_ENC = stringPreferencesKey("password_salt_enc")
        val KEY_CREATED_AT_ENC = stringPreferencesKey("created_at_enc")
        val KEY_IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")
        val KEY_MIGRATED_FROM_LEGACY = booleanPreferencesKey("migrated_from_legacy")
    }
}
