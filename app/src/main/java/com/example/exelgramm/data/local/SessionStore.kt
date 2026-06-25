package com.example.exelgramm.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.exelgramm.core.DEFAULT_SHEET_NAME
import com.example.exelgramm.data.remote.SheetLinkParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Конфигурация чата хранится в DataStore (открытый текст — не чувствительные данные).
 * Credentials хранятся в [AuthStore] (EncryptedSharedPreferences).
 */
private val Context.chatConfigDataStore: DataStore<Preferences> by preferencesDataStore(name = "chat_config")

@Singleton
class SessionStore @Inject constructor(
    val authStore: AuthStore,
    @param:ApplicationContext private val context: Context,
) : SessionProvider {
    /** Только auth-данные — не пересчитывается при изменении конфига чата. */
    override val authSession: Flow<AuthSession> = authStore.state.map { auth ->
        AuthSession(
            username = auth.username,
            isLoggedIn = auth.isLoggedIn,
            isRegistered = auth.isRegistered,
            createdAt = auth.createdAt,
        )
    }

    /** Только конфиг чата — не пересчитывается при изменении auth. */
    override val chatConfig: Flow<ChatConfig> = context.chatConfigDataStore.data.map { prefs ->
        ChatConfig(
            spreadsheetId = prefs[KEY_SPREADSHEET_ID].orEmpty(),
            sheetUrl = prefs[KEY_SHEET_URL].orEmpty(),
            sheetName = prefs[KEY_SHEET_NAME].orEmpty().ifBlank { DEFAULT_SHEET_NAME },
            webAppUrl = prefs[KEY_WEB_APP_URL].orEmpty(),
        )
    }

    suspend fun saveCredentials(username: String, passwordHash: String, passwordSalt: String) =
        authStore.saveCredentials(username, passwordHash, passwordSalt)

    suspend fun login() = authStore.login()

    suspend fun logout() {
        authStore.logout()
        context.chatConfigDataStore.edit { prefs ->
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
        context.chatConfigDataStore.edit { prefs ->
            prefs[KEY_SHEET_URL] = sheetUrl.trim()
            prefs[KEY_SPREADSHEET_ID] = spreadsheetId
            prefs[KEY_SHEET_NAME] = sheetName.ifBlank { DEFAULT_SHEET_NAME }
            prefs[KEY_WEB_APP_URL] = SheetLinkParser.canonicalExecUrl(webAppUrl)
        }
    }

    suspend fun saveWebAppUrl(url: String) {
        context.chatConfigDataStore.edit {
            it[KEY_WEB_APP_URL] = SheetLinkParser.normalizeWebAppUrl(url)
        }
    }

    suspend fun clear() {
        authStore.clear()
        context.chatConfigDataStore.edit { it.clear() }
    }

    /** Быстрая проверка состояния входа (для первого экрана). */
    suspend fun isLoggedIn(): Boolean = authStore.state.first().isLoggedIn

    private companion object {
        val KEY_SPREADSHEET_ID = stringPreferencesKey("spreadsheet_id")
        val KEY_SHEET_URL = stringPreferencesKey("sheet_url")
        val KEY_SHEET_NAME = stringPreferencesKey("sheet_name")
        val KEY_WEB_APP_URL = stringPreferencesKey("web_app_url")
    }
}
