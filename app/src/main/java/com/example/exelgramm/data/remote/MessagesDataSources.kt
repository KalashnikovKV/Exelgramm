package com.example.exelgramm.data.remote

import com.example.exelgramm.domain.model.Message

/** Apps Script HTTP client contract (for DI and tests). */
interface MessagesApiClient {
    suspend fun fetchMessages(
        webAppUrl: String,
        spreadsheetId: String,
        sheetName: String,
        since: String? = null,
    ): Result<List<Message>>

    suspend fun sendMessage(
        webAppUrl: String,
        spreadsheetId: String,
        sheetName: String,
        message: Message,
    ): Result<Unit>

    suspend fun updateMessage(
        webAppUrl: String,
        spreadsheetId: String,
        sheetName: String,
        messageId: String,
        text: String,
    ): Result<Unit>

    suspend fun deleteMessage(
        webAppUrl: String,
        spreadsheetId: String,
        sheetName: String,
        messageId: String,
    ): Result<Unit>
}

/** Fallback CSV source contract (for DI and tests). */
interface CsvMessagesClient {
    suspend fun fetch(spreadsheetId: String, sheetName: String): Result<List<Message>>
}
