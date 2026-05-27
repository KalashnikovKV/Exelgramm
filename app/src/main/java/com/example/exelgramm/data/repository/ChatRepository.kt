package com.example.exelgramm.data.repository

import com.example.exelgramm.data.local.UserSession
import com.example.exelgramm.data.remote.AppsScriptApi
import com.example.exelgramm.data.remote.CsvSheetReader
import com.example.exelgramm.domain.model.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ChatRepository(
    private val api: AppsScriptApi = AppsScriptApi(),
) {

    suspend fun loadMessages(session: UserSession): Result<List<Message>> = withContext(Dispatchers.IO) {
        require(session.isChatConfigured) { "Чат не настроен" }

        val fromScript = api.fetchMessages(
            webAppUrl = session.webAppUrl,
            spreadsheetId = session.spreadsheetId,
            sheetName = session.sheetName,
        )

        if (fromScript.isSuccess) {
            return@withContext fromScript.map { it.sortedBy { m -> m.timestamp } }
        }

        CsvSheetReader.fetch(session.spreadsheetId, session.sheetName)
            .map { list -> list.sortedBy { it.timestamp } }
    }

    suspend fun sendMessage(session: UserSession, message: Message): Result<Message> =
        withContext(Dispatchers.IO) {
            require(session.isChatConfigured) { "Чат не настроен" }
            api.sendMessage(
                webAppUrl = session.webAppUrl,
                spreadsheetId = session.spreadsheetId,
                sheetName = session.sheetName,
                message = message,
            ).map { message }
        }
}
