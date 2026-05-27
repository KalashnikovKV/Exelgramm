package com.example.exelgramm.data.remote

import com.example.exelgramm.domain.model.Message
import com.google.gson.annotations.SerializedName

data class MessagesResponse(
    val ok: Boolean,
    val messages: List<MessageDto>? = null,
    val error: String? = null,
)

data class MessageDto(
    val id: String = "",
    val timestamp: String = "",
    val author: String = "",
    val text: String = "",
) {
    fun toDomain(): Message = Message(
        id = id,
        timestamp = timestamp,
        author = author,
        text = text,
    )
}

data class FetchMessagesRequest(
    val action: String = "fetch",
    @SerializedName("spreadsheetId") val spreadsheetId: String,
    val sheet: String,
)

data class PostMessageRequest(
    val action: String = "send",
    @SerializedName("spreadsheetId") val spreadsheetId: String,
    val sheet: String,
    val id: String,
    val timestamp: String,
    val author: String,
    val text: String,
)

data class SimpleResponse(
    val ok: Boolean,
    val error: String? = null,
)
