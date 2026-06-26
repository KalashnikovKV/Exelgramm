package com.example.exelgramm.data.repository

import com.example.exelgramm.core.TimeFormats
import com.example.exelgramm.domain.model.Message
import com.example.exelgramm.domain.model.MessageType

internal fun List<Message>.toParticipantSummaries(): List<ParticipantSummary> =
    groupBy { it.author }
        .map { (author, messages) ->
            val stats = messages.participantStats()
            ParticipantSummary(
                author = author,
                totalMessages = stats.totalMessages,
                textMessages = stats.textMessages,
                importantMessages = stats.importantMessages,
                lastMessageTime = messages.maxByOrNull { it.timestamp }?.timestamp
                    ?.let(TimeFormats::formatChatTime)
                    .orEmpty(),
            )
        }
        .sortedByDescending { it.totalMessages }

internal fun List<Message>.toParticipantDetail(authorName: String): ParticipantDetail {
    val sorted = sortedBy { it.timestamp }
    val stats = sorted.participantStats()
    return ParticipantDetail(
        authorName = authorName,
        totalMessages = stats.totalMessages,
        textMessages = stats.textMessages,
        importantMessages = stats.importantMessages,
        firstMessageTime = sorted.firstOrNull()?.timestamp,
        lastMessageTime = sorted.lastOrNull()?.timestamp,
        messages = sorted.map { message ->
            ParticipantMessageSummary(
                id = message.id,
                text = message.text,
                timestamp = message.timestamp,
                isImportant = message.type == MessageType.IMPORTANT,
            )
        },
    )
}

private data class ParticipantStats(
    val totalMessages: Int,
    val textMessages: Int,
    val importantMessages: Int,
)

private fun List<Message>.participantStats(): ParticipantStats = ParticipantStats(
    totalMessages = size,
    textMessages = count { it.type == MessageType.TEXT },
    importantMessages = count { it.type == MessageType.IMPORTANT },
)
