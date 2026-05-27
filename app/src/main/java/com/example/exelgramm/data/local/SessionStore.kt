package com.example.exelgramm.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.exelgramm.data.remote.SheetLinkParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "session")

data class UserSession(
    val displayName: String = "",
    val spreadsheetId: String = "",
    val sheetUrl: String = "",
    val sheetName: String = "Лист1",
    val webAppUrl: String = "",
) {
    val isLoggedIn: Boolean get() = displayName.isNotBlank()
    val isChatConfigured: Boolean get() =
        spreadsheetId.isNotBlank() && webAppUrl.isNotBlank()
}

class SessionStore(private val context: Context) {

    val session: Flow<UserSession> = context.dataStore.data.map { prefs ->
        UserSession(
            displayName = prefs[KEY_DISPLAY_NAME].orEmpty(),
            spreadsheetId = prefs[KEY_SPREADSHEET_ID].orEmpty(),
            sheetUrl = prefs[KEY_SHEET_URL].orEmpty(),
            sheetName = prefs[KEY_SHEET_NAME].orEmpty().ifBlank { "Лист1" },
            webAppUrl = prefs[KEY_WEB_APP_URL].orEmpty(),
        )
    }

    suspend fun saveDisplayName(name: String) {
        context.dataStore.edit { it[KEY_DISPLAY_NAME] = name.trim() }
    }

    suspend fun saveChatConfig(
        sheetUrl: String,
        spreadsheetId: String,
        sheetName: String,
        webAppUrl: String,
    ) {
        context.dataStore.edit {
            it[KEY_SHEET_URL] = sheetUrl.trim()
            it[KEY_SPREADSHEET_ID] = spreadsheetId
            it[KEY_SHEET_NAME] = sheetName.ifBlank { "Лист1" }
            it[KEY_WEB_APP_URL] = SheetLinkParser.canonicalExecUrl(webAppUrl)
        }
    }

    suspend fun saveWebAppUrl(url: String) {
        context.dataStore.edit {
            it[KEY_WEB_APP_URL] = SheetLinkParser.normalizeWebAppUrl(url)
        }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }

    private companion object {
        val KEY_DISPLAY_NAME = stringPreferencesKey("display_name")
        val KEY_SPREADSHEET_ID = stringPreferencesKey("spreadsheet_id")
        val KEY_SHEET_URL = stringPreferencesKey("sheet_url")
        val KEY_SHEET_NAME = stringPreferencesKey("sheet_name")
        val KEY_WEB_APP_URL = stringPreferencesKey("web_app_url")
    }
}
