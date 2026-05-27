package com.example.exelgramm.domain.model

data class Message(
    val id: String,
    val timestamp: String,
    val author: String,
    val text: String,
) {
    fun isMine(currentAuthor: String): Boolean =
        author.equals(currentAuthor, ignoreCase = true)
}
