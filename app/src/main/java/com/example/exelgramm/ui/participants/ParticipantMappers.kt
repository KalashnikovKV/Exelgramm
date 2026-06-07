package com.example.exelgramm.ui.participants

import com.example.exelgramm.core.TimeFormats
import com.example.exelgramm.data.local.db.MessageEntity
import com.example.exelgramm.domain.model.MessageType

data class ParticipantStats(
    val totalMessages: Int,
    val textMessages: Int,
    val importantMessages: Int,
)

fun List<MessageEntity>.participantStats(): ParticipantStats = ParticipantStats(
    totalMessages = size,
    textMessages = count { it.type == MessageType.TEXT },
    importantMessages = count { it.type == MessageType.IMPORTANT },
)

fun List<MessageEntity>.toParticipantItems(): List<ParticipantItem> =
    groupBy { it.author }
        .map { (author, messages) ->
            val stats = messages.participantStats()
            ParticipantItem(
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
