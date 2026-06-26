package com.example.exelgramm.data.repository

import com.example.exelgramm.core.AppError
import com.example.exelgramm.data.local.ChatConfig
import com.example.exelgramm.data.local.db.MessageDao
import com.example.exelgramm.data.local.db.MessageEntity
import com.example.exelgramm.data.remote.CsvMessagesClient
import com.example.exelgramm.data.remote.MessagesApiClient
import com.example.exelgramm.domain.model.Message
import com.example.exelgramm.domain.model.MessageType
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatRepositoryTest {

    private val config = ChatConfig(
        spreadsheetId = "sheet123",
        webAppUrl = "https://script.google.com/macros/s/test/exec",
        sheetName = "Sheet1",
    )

    private val remoteMessage = Message(
        id = "1",
        timestamp = Instant.parse("2024-01-01T00:00:01Z"),
        author = "alice",
        text = "hello",
        type = MessageType.TEXT,
    )

    @Test
    fun `loadMessages returns API result and caches in DAO`() = runTest {
        val dao = FakeMessageDao()
        val api = FakeMessagesApiClient(apiResult = Result.success(listOf(remoteMessage)))
        val csv = FakeCsvMessagesClient(Result.success(emptyList()))
        val repo = DefaultChatRepository(api, csv, dao)

        val result = repo.loadMessages(config)

        assertTrue(result.isSuccess)
        assertEquals(listOf(remoteMessage), result.getOrNull())
        assertEquals(1, dao.entities.size)
        assertEquals("1", dao.entities.first().id)
    }

    @Test
    fun `loadMessages falls back to CSV when API fails`() = runTest {
        val csvMessage = remoteMessage.copy(id = "csv_1")
        val dao = FakeMessageDao()
        val api = FakeMessagesApiClient(apiResult = Result.failure(AppError.NoInternet))
        val csv = FakeCsvMessagesClient(Result.success(listOf(csvMessage)))
        val repo = DefaultChatRepository(api, csv, dao)

        val result = repo.loadMessages(config)

        assertEquals(listOf(csvMessage), result.getOrNull())
        assertEquals(1, dao.entities.size)
    }

    @Test
    fun `loadMessages falls back to cache when API and CSV fail`() = runTest {
        val cached = remoteMessage.toEntity(config)
        val dao = FakeMessageDao(initial = listOf(cached))
        val api = FakeMessagesApiClient(apiResult = Result.failure(AppError.NoInternet))
        val csv = FakeCsvMessagesClient(Result.failure(AppError.NoInternet))
        val repo = DefaultChatRepository(api, csv, dao)

        val result = repo.loadMessages(config)

        assertEquals(listOf(remoteMessage), result.getOrNull())
    }

    @Test
    fun `loadMessages returns NoInternet when all sources empty`() = runTest {
        val dao = FakeMessageDao()
        val api = FakeMessagesApiClient(apiResult = Result.failure(AppError.NoInternet))
        val csv = FakeCsvMessagesClient(Result.failure(AppError.NoInternet))
        val repo = DefaultChatRepository(api, csv, dao)

        val result = repo.loadMessages(config)

        assertTrue(result.exceptionOrNull() is AppError.NoInternet)
    }

    @Test
    fun `getMessagesByAuthor filters cached messages`() = runTest {
        val alice = remoteMessage
        val bob = remoteMessage.copy(id = "2", author = "bob")
        val dao = FakeMessageDao(
            initial = listOf(
                alice.toEntity(config),
                bob.toEntity(config),
            ),
        )
        val repo = DefaultChatRepository(
            FakeMessagesApiClient(),
            FakeCsvMessagesClient(),
            dao,
        )

        val result = repo.getMessagesByAuthor(config, "alice")

        assertEquals(listOf(alice), result.getOrNull())
    }

    private fun Message.toEntity(config: ChatConfig) = MessageEntity(
        id = id,
        timestamp = timestamp.toEpochMilli(),
        author = author,
        text = text,
        spreadsheetId = config.spreadsheetId,
        sheetName = config.sheetName,
        type = type.apiValue,
    )

    private class FakeMessagesApiClient(
        private val apiResult: Result<List<Message>> = Result.failure(AppError.NoInternet),
        private val mutationResult: Result<Unit> = Result.success(Unit),
    ) : MessagesApiClient {
        override suspend fun fetchMessages(
            webAppUrl: String,
            spreadsheetId: String,
            sheetName: String,
            since: String?,
        ): Result<List<Message>> = apiResult

        override suspend fun sendMessage(
            webAppUrl: String,
            spreadsheetId: String,
            sheetName: String,
            message: Message,
        ): Result<Unit> = mutationResult

        override suspend fun updateMessage(
            webAppUrl: String,
            spreadsheetId: String,
            sheetName: String,
            messageId: String,
            text: String,
        ): Result<Unit> = mutationResult

        override suspend fun deleteMessage(
            webAppUrl: String,
            spreadsheetId: String,
            sheetName: String,
            messageId: String,
        ): Result<Unit> = mutationResult
    }

    private class FakeCsvMessagesClient(
        private val result: Result<List<Message>> = Result.failure(AppError.NoInternet),
    ) : CsvMessagesClient {
        override suspend fun fetch(spreadsheetId: String, sheetName: String): Result<List<Message>> = result
    }

    private class FakeMessageDao(
        initial: List<MessageEntity> = emptyList(),
    ) : MessageDao {
        val entities = initial.toMutableList()

        override suspend fun getAll(spreadsheetId: String, sheetName: String): List<MessageEntity> =
            entities.filter { it.spreadsheetId == spreadsheetId && it.sheetName == sheetName }
                .sortedBy { it.timestamp }

        override suspend fun getById(
            id: String,
            spreadsheetId: String,
            sheetName: String,
        ): MessageEntity? = entities.find {
            it.id == id && it.spreadsheetId == spreadsheetId && it.sheetName == sheetName
        }

        override suspend fun upsertAll(messages: List<MessageEntity>) {
            messages.forEach { msg ->
                entities.removeAll { it.id == msg.id }
                entities.add(msg)
            }
        }

        override suspend fun replaceForSheet(
            spreadsheetId: String,
            sheetName: String,
            messages: List<MessageEntity>,
        ) {
            entities.removeAll { it.spreadsheetId == spreadsheetId && it.sheetName == sheetName }
            entities.addAll(messages)
        }

        override suspend fun updateText(
            id: String,
            spreadsheetId: String,
            sheetName: String,
            text: String,
        ) {
            val index = entities.indexOfFirst {
                it.id == id && it.spreadsheetId == spreadsheetId && it.sheetName == sheetName
            }
            if (index >= 0) entities[index] = entities[index].copy(text = text)
        }

        override suspend fun deleteById(id: String, spreadsheetId: String, sheetName: String) {
            entities.removeAll {
                it.id == id && it.spreadsheetId == spreadsheetId && it.sheetName == sheetName
            }
        }

        override suspend fun deleteForSheet(spreadsheetId: String, sheetName: String) {
            entities.removeAll { it.spreadsheetId == spreadsheetId && it.sheetName == sheetName }
        }
    }
}
