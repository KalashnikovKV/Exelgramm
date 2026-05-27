package com.example.exelgramm.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.exelgramm.data.remote.SheetLinkParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "session")

data class UserSession(
    val username: String = "",
    val passwordHash: String = "",
    val isLoggedIn: Boolean = false,
    val spreadsheetId: String = "",
    val sheetUrl: String = "",
    val sheetName: String = "Лист1",
    val webAppUrl: String = "",
) {
    /** Логин для подписи сообщений в чате */
    val displayName: String get() = username

    /** Credentials существуют (пользователь уже регистрировался) */
    val isRegistered: Boolean get() = username.isNotBlank() && passwordHash.isNotBlank()

    val isChatConfigured: Boolean get() = spreadsheetId.isNotBlank() && webAppUrl.isNotBlank()
}

class SessionStore(private val context: Context) {

    val session: Flow<UserSession> = context.dataStore.data.map { prefs ->
        UserSession(
            username = prefs[KEY_USERNAME].orEmpty(),
            passwordHash = prefs[KEY_PASSWORD_HASH].orEmpty(),
            isLoggedIn = prefs[KEY_IS_LOGGED_IN] ?: false,
            spreadsheetId = prefs[KEY_SPREADSHEET_ID].orEmpty(),
            sheetUrl = prefs[KEY_SHEET_URL].orEmpty(),
            sheetName = prefs[KEY_SHEET_NAME].orEmpty().ifBlank { "Лист1" },
            webAppUrl = prefs[KEY_WEB_APP_URL].orEmpty(),
        )
    }

    /** Первичная регистрация: сохранить credentials и выставить флаг входа. */
    suspend fun saveCredentials(username: String, passwordHash: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_USERNAME] = username
            prefs[KEY_PASSWORD_HASH] = passwordHash
            prefs[KEY_IS_LOGGED_IN] = true
        }
    }

    /** Вход существующего пользователя (credentials уже проверены). */
    suspend fun login() {
        context.dataStore.edit { it[KEY_IS_LOGGED_IN] = true }
    }

    /**
     * Выход: сбрасывает флаг сессии и конфигурацию чата.
     * Credentials (username/passwordHash) остаются для следующего входа.
     * TODO: заменить на инвалидацию Google OAuth токена.
     */
    suspend fun logout() {
        context.dataStore.edit { prefs ->
            prefs[KEY_IS_LOGGED_IN] = false
            prefs.remove(KEY_SPREADSHEET_ID)
            prefs.remove(KEY_SHEET_URL)
            prefs.remove(KEY_SHEET_NAME)
            prefs.remove(KEY_WEB_APP_URL)
        }
    }

    suspend fun saveChatConfig(
        sheetUrl: String,
        spreadsheetId: String,
        sheetName: String,
        webAppUrl: String,
    ) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SHEET_URL] = sheetUrl.trim()
            prefs[KEY_SPREADSHEET_ID] = spreadsheetId
            prefs[KEY_SHEET_NAME] = sheetName.ifBlank { "Лист1" }
            prefs[KEY_WEB_APP_URL] = SheetLinkParser.canonicalExecUrl(webAppUrl)
        }
    }

    suspend fun saveWebAppUrl(url: String) {
        context.dataStore.edit {
            it[KEY_WEB_APP_URL] = SheetLinkParser.normalizeWebAppUrl(url)
        }
    }

    /** Полный сброс (удаление аккаунта). */
    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }

    private companion object {
        val KEY_USERNAME = stringPreferencesKey("username")
        val KEY_PASSWORD_HASH = stringPreferencesKey("password_hash")
        val KEY_IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")
        val KEY_SPREADSHEET_ID = stringPreferencesKey("spreadsheet_id")
        val KEY_SHEET_URL = stringPreferencesKey("sheet_url")
        val KEY_SHEET_NAME = stringPreferencesKey("sheet_name")
        val KEY_WEB_APP_URL = stringPreferencesKey("web_app_url")
    }
}
