package com.example.exelgramm.data.repository

import com.example.exelgramm.data.local.ChatConfig
import com.example.exelgramm.domain.model.Message
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SendMessageUseCase @Inject constructor(
    private val repository: ChatRepository,
) {
    suspend operator fun invoke(config: ChatConfig, message: Message): Result<Message> =
        repository.sendMessage(config, message)
}

@Singleton
class UpdateMessageUseCase @Inject constructor(
    private val repository: ChatRepository,
) {
    suspend operator fun invoke(config: ChatConfig, messageId: String, text: String): Result<Unit> =
        repository.updateMessage(config, messageId, text)
}

@Singleton
class DeleteMessageUseCase @Inject constructor(
    private val repository: ChatRepository,
) {
    suspend operator fun invoke(config: ChatConfig, messageId: String): Result<Unit> =
        repository.deleteMessage(config, messageId)
}

@Singleton
class LoadParticipantsUseCase @Inject constructor(
    private val repository: ChatRepository,
) {
    /**
     * @param syncFromRemote when true, fetch from server first (updates Room cache)
     */
    suspend operator fun invoke(config: ChatConfig, syncFromRemote: Boolean): Result<List<ParticipantSummary>> {
        if (syncFromRemote) {
            repository.loadMessages(config).onFailure { return Result.failure(it) }
        }
        return repository.getCachedMessages(config).map { it.toParticipantSummaries() }
    }
}

@Singleton
class LoadParticipantDetailUseCase @Inject constructor(
    private val repository: ChatRepository,
) {
    suspend operator fun invoke(config: ChatConfig, authorName: String): Result<ParticipantDetail> {
        if (authorName.isBlank()) {
            return Result.success(ParticipantDetail(authorName = authorName))
        }
        return repository.getMessagesByAuthor(config, authorName).map { messages ->
            messages.toParticipantDetail(authorName)
        }
    }
}

/** Aggregated participant stats for the list screen. */
data class ParticipantSummary(
    val author: String,
    val totalMessages: Int,
    val textMessages: Int,
    val importantMessages: Int,
    val lastMessageTime: String,
)

/** Participant detail and their messages. */
data class ParticipantDetail(
    val authorName: String,
    val totalMessages: Int = 0,
    val textMessages: Int = 0,
    val importantMessages: Int = 0,
    val firstMessageTime: Instant? = null,
    val lastMessageTime: Instant? = null,
    val messages: List<ParticipantMessageSummary> = emptyList(),
)

data class ParticipantMessageSummary(
    val id: String,
    val text: String,
    val timestamp: Instant,
    val isImportant: Boolean,
)
