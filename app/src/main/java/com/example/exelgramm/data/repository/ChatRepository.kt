package com.example.exelgramm.data.repository

import com.example.exelgramm.data.local.UserSession
import com.example.exelgramm.data.local.db.MessageDao
import com.example.exelgramm.data.local.db.toEntity
import com.example.exelgramm.data.remote.AppsScriptApi
import com.example.exelgramm.data.remote.CsvSheetReader
import com.example.exelgramm.domain.model.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val api: AppsScriptApi,
    private val messageDao: MessageDao,
) {

    /**
     * Порядок загрузки:
     * 1. Apps Script API
     * 2. CSV (прямое чтение таблицы) — если API недоступен
     * 3. Room-кэш — если оба источника недоступны (offline)
     */
    suspend fun loadMessages(session: UserSession): Result<List<Message>> = withContext(Dispatchers.IO) {
        require(session.isChatConfigured) { "Чат не настроен" }

        val apiResult = api.fetchMessages(
            webAppUrl = session.webAppUrl,
            spreadsheetId = session.spreadsheetId,
            sheetName = session.sheetName,
        )
        if (apiResult.isSuccess) {
            val messages = apiResult.getOrThrow().sortedBy { it.timestamp }
            messageDao.upsertAll(messages.map { it.toEntity(session.spreadsheetId, session.sheetName) })
            return@withContext Result.success(messages)
        }

        val csvResult = CsvSheetReader.fetch(session.spreadsheetId, session.sheetName)
        if (csvResult.isSuccess) {
            val messages = csvResult.getOrThrow().sortedBy { it.timestamp }
            messageDao.upsertAll(messages.map { it.toEntity(session.spreadsheetId, session.sheetName) })
            return@withContext Result.success(messages)
        }

        // Оба сетевых источника недоступны — отдаём кэш
        val cached = messageDao.getAll(session.spreadsheetId, session.sheetName)
        if (cached.isNotEmpty()) {
            return@withContext Result.success(cached.map { it.toDomain() })
        }

        // Нет ни сети, ни кэша — возвращаем ошибку от API
        apiResult.map { it.sortedBy { m -> m.timestamp } }
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

    suspend fun updateMessage(session: UserSession, messageId: String, text: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            require(session.isChatConfigured) { "Чат не настроен" }
            api.updateMessage(
                webAppUrl = session.webAppUrl,
                spreadsheetId = session.spreadsheetId,
                sheetName = session.sheetName,
                messageId = messageId,
                text = text,
            ).onSuccess {
                messageDao.updateText(
                    id = messageId,
                    spreadsheetId = session.spreadsheetId,
                    sheetName = session.sheetName,
                    text = text,
                )
            }
        }

    suspend fun deleteMessage(session: UserSession, messageId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            require(session.isChatConfigured) { "Чат не настроен" }
            api.deleteMessage(
                webAppUrl = session.webAppUrl,
                spreadsheetId = session.spreadsheetId,
                sheetName = session.sheetName,
                messageId = messageId,
            ).onSuccess {
                messageDao.deleteById(
                    id = messageId,
                    spreadsheetId = session.spreadsheetId,
                    sheetName = session.sheetName,
                )
            }
        }
}
