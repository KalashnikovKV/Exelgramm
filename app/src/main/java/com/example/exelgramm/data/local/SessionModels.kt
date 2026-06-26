package com.example.exelgramm.data.local

import com.example.exelgramm.core.DEFAULT_SHEET_NAME

/** Authentication data (username, login flags). */
data class AuthSession(
    val username: String = "",
    val isLoggedIn: Boolean = false,
    val isRegistered: Boolean = false,
    /** First registration time (ms). 0 = not stored (legacy accounts). */
    val createdAt: Long = 0L,
) {
    val displayName: String get() = username
}

/** Chat configuration (Google Sheets + Apps Script). */
data class ChatConfig(
    val spreadsheetId: String = "",
    val sheetUrl: String = "",
    val sheetName: String = DEFAULT_SHEET_NAME,
    val webAppUrl: String = "",
) {
    val isConfigured: Boolean get() = spreadsheetId.isNotBlank() && webAppUrl.isNotBlank()
}
