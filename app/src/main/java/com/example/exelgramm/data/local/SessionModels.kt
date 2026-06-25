package com.example.exelgramm.data.local

import com.example.exelgramm.core.DEFAULT_SHEET_NAME

/** Данные аутентификации (username, флаги входа). */
data class AuthSession(
    val username: String = "",
    val isLoggedIn: Boolean = false,
    val isRegistered: Boolean = false,
    /** Unix-timestamp (мс) первой регистрации. 0 = не сохранён (старые аккаунты). */
    val createdAt: Long = 0L,
) {
    val displayName: String get() = username
}

/** Конфигурация чата (Google Sheets + Apps Script). */
data class ChatConfig(
    val spreadsheetId: String = "",
    val sheetUrl: String = "",
    val sheetName: String = DEFAULT_SHEET_NAME,
    val webAppUrl: String = "",
) {
    val isConfigured: Boolean get() = spreadsheetId.isNotBlank() && webAppUrl.isNotBlank()
}
