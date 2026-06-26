package com.example.exelgramm.domain.model

import java.time.Instant

data class Message(
    val id: String,
    val timestamp: Instant,
    val author: String,
    val text: String,
    val type: MessageType = MessageType.TEXT,
) {
    fun isMine(currentAuthor: String): Boolean =
        author.equals(currentAuthor, ignoreCase = true)
}

enum class MessageType(val apiValue: String) {
    TEXT("text"),
    IMPORTANT("important");

    companion object {
        private val byApiValue: Map<String, MessageType> = entries.associateBy { it.apiValue }

        fun fromString(value: String): MessageType = byApiValue[value] ?: TEXT
    }
}
