package com.example.exelgramm.data.remote

import com.example.exelgramm.domain.model.Message

/** Контракт HTTP-клиента Apps Script (для DI и тестов). */
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

/** Контракт запасного CSV-источника (для DI и тестов). */
interface CsvMessagesClient {
    suspend fun fetch(spreadsheetId: String, sheetName: String): Result<List<Message>>
}
