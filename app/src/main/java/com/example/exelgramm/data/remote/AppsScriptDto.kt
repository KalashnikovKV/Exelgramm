package com.example.exelgramm.data.remote

import com.example.exelgramm.domain.model.Message
import com.example.exelgramm.domain.model.MessageType
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
    val type: String = MessageType.TEXT,
) {
    fun toDomain(): Message = Message(
        id = id,
        timestamp = timestamp,
        author = author,
        text = text,
        type = type,
    )
}

data class PostMessageRequest(
    val action: String = "send",
    @SerializedName("spreadsheetId") val spreadsheetId: String,
    val sheet: String,
    val id: String,
    val timestamp: String,
    val author: String,
    val text: String,
    val type: String = MessageType.TEXT,
)

data class UpdateMessageRequest(
    val action: String = "update",
    @SerializedName("spreadsheetId") val spreadsheetId: String,
    val sheet: String,
    val id: String,
    val text: String,
)

data class DeleteMessageRequest(
    val action: String = "delete",
    @SerializedName("spreadsheetId") val spreadsheetId: String,
    val sheet: String,
    val id: String,
)

data class SimpleResponse(
    val ok: Boolean,
    val error: String? = null,
)
