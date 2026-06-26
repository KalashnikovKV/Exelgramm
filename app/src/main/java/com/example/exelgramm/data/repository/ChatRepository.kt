package com.example.exelgramm.data.repository

import com.example.exelgramm.core.AppError
import com.example.exelgramm.core.TimeFormats
import com.example.exelgramm.data.local.ChatConfig
import com.example.exelgramm.data.local.db.MessageDao
import com.example.exelgramm.data.local.db.toEntity
import com.example.exelgramm.data.remote.CsvMessagesClient
import com.example.exelgramm.data.remote.MessagesApiClient
import com.example.exelgramm.domain.model.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

interface ChatRepository {
    suspend fun loadMessages(
        config: ChatConfig,
        options: MessageSyncOptions = MessageSyncOptions(),
    ): Result<List<Message>>

    suspend fun getCachedMessages(config: ChatConfig): Result<List<Message>>
    suspend fun getMessagesByAuthor(config: ChatConfig, author: String): Result<List<Message>>
    suspend fun getMessageById(config: ChatConfig, messageId: String): Result<Message?>
    suspend fun sendMessage(config: ChatConfig, message: Message): Result<Message>
    suspend fun updateMessage(config: ChatConfig, messageId: String, text: String): Result<Unit>
    suspend fun deleteMessage(config: ChatConfig, messageId: String): Result<Unit>
}

class DefaultChatRepository @Inject constructor(
    private val api: MessagesApiClient,
    private val csvReader: CsvMessagesClient,
    private val messageDao: MessageDao,
) : ChatRepository {

    override suspend fun loadMessages(
        config: ChatConfig,
        options: MessageSyncOptions,
    ): Result<List<Message>> = withConfiguredConfig(config) {
        api.fetchMessages(
            webAppUrl = config.webAppUrl,
            spreadsheetId = config.spreadsheetId,
            sheetName = config.sheetName,
            since = options.since?.let(TimeFormats::toIso),
        ).fold(
            onSuccess = { messages -> return@withConfiguredConfig persistAndReturn(config, messages, options) },
            onFailure = {},
        )

        if (options.since == null) {
            csvReader.fetch(config.spreadsheetId, config.sheetName).fold(
                onSuccess = { messages ->
                    return@withConfiguredConfig persistAndReturn(
                        config,
                        messages,
                        MessageSyncOptions(fullRefresh = true),
                    )
                },
                onFailure = {},
            )
        }

        getCachedMessages(config).fold(
            onSuccess = { cached ->
                if (cached.isNotEmpty()) return@withConfiguredConfig Result.success(cached)
            },
            onFailure = { return@withConfiguredConfig Result.failure(it) },
        )

        Result.failure(AppError.NoInternet)
    }

    override suspend fun getCachedMessages(config: ChatConfig): Result<List<Message>> =
        withConfiguredConfig(config) {
            val cached = messageDao.getAll(config.spreadsheetId, config.sheetName)
            Result.success(cached.map { it.toDomain() })
        }

    override suspend fun getMessagesByAuthor(config: ChatConfig, author: String): Result<List<Message>> =
        getCachedMessages(config).map { messages ->
            messages.filter { it.author.equals(author, ignoreCase = true) }
                .sortedBy { it.timestamp }
        }

    override suspend fun getMessageById(config: ChatConfig, messageId: String): Result<Message?> =
        withConfiguredConfig(config) {
            if (messageId.isBlank()) return@withConfiguredConfig Result.success(null)
            val entity = messageDao.getById(messageId, config.spreadsheetId, config.sheetName)
            Result.success(entity?.toDomain())
        }

    override suspend fun sendMessage(config: ChatConfig, message: Message): Result<Message> =
        withConfiguredConfig(config) {
            api.sendMessage(
                webAppUrl = config.webAppUrl,
                spreadsheetId = config.spreadsheetId,
                sheetName = config.sheetName,
                message = message,
            ).map { message }
        }

    override suspend fun updateMessage(config: ChatConfig, messageId: String, text: String): Result<Unit> =
        withConfiguredConfig(config) {
            api.updateMessage(
                webAppUrl = config.webAppUrl,
                spreadsheetId = config.spreadsheetId,
                sheetName = config.sheetName,
                messageId = messageId,
                text = text,
            ).also { result ->
                result.onSuccess {
                    messageDao.updateText(
                        id = messageId,
                        spreadsheetId = config.spreadsheetId,
                        sheetName = config.sheetName,
                        text = text,
                    )
                }
            }
        }

    override suspend fun deleteMessage(config: ChatConfig, messageId: String): Result<Unit> =
        withConfiguredConfig(config) {
            api.deleteMessage(
                webAppUrl = config.webAppUrl,
                spreadsheetId = config.spreadsheetId,
                sheetName = config.sheetName,
                messageId = messageId,
            ).also { result ->
                result.onSuccess {
                    messageDao.deleteById(
                        id = messageId,
                        spreadsheetId = config.spreadsheetId,
                        sheetName = config.sheetName,
                    )
                }
            }
        }

    private suspend fun persistAndReturn(
        config: ChatConfig,
        messages: List<Message>,
        options: MessageSyncOptions,
    ): Result<List<Message>> {
        val sorted = messages.sortedBy { it.timestamp }
        val entities = sorted.map { it.toEntity(config.spreadsheetId, config.sheetName) }
        if (options.fullRefresh) {
            messageDao.replaceForSheet(config.spreadsheetId, config.sheetName, entities)
            return Result.success(sorted)
        }
        if (entities.isNotEmpty()) {
            messageDao.upsertAll(entities)
        }
        val merged = messageDao.getAll(config.spreadsheetId, config.sheetName).map { it.toDomain() }
        return Result.success(merged)
    }

    private suspend fun <T> withConfiguredConfig(
        config: ChatConfig,
        block: suspend () -> Result<T>,
    ): Result<T> = withContext(Dispatchers.IO) {
        if (!config.isConfigured) Result.failure(AppError.ChatNotConfigured)
        else block()
    }
}
