package com.example.exelgramm.domain.model

data class Message(
    val id: String,
    val timestamp: String,
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
        fun fromString(value: String): MessageType =
            entries.firstOrNull { it.apiValue == value } ?: TEXT
    }
}
