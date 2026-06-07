package com.example.exelgramm.domain.model

data class Message(
    val id: String,
    val timestamp: String,
    val author: String,
    val text: String,
    val type: String = MessageType.TEXT,
) {
    fun isMine(currentAuthor: String): Boolean =
        author.equals(currentAuthor, ignoreCase = true)
}

object MessageType {
    const val TEXT = "text"
    const val IMPORTANT = "important"
}
