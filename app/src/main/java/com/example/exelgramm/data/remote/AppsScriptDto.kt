package com.example.exelgramm.data.remote

import com.example.exelgramm.core.TimeFormats
import com.example.exelgramm.domain.model.Message
import com.example.exelgramm.domain.model.MessageType
import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MessagesResponse(
    val ok: Boolean,
    val messages: List<MessageDto>? = null,
    val error: String? = null,
)

@Serializable
data class MessageDto(
    val id: String = "",
    val timestamp: String = "",
    val author: String = "",
    val text: String = "",
    val type: String = MessageType.TEXT.apiValue,
) {
    fun toDomain(): Message = Message(
        id = id,
        timestamp = TimeFormats.parse(timestamp) ?: Instant.EPOCH,
        author = author,
        text = text,
        type = MessageType.fromString(type),
    )
}

@Serializable
data class PostMessageRequest(
    val action: String = "send",
    @SerialName("spreadsheetId") val spreadsheetId: String,
    val sheet: String,
    val id: String,
    val timestamp: String,
    val author: String,
    val text: String,
    val type: String = MessageType.TEXT.apiValue,
)

@Serializable
data class UpdateMessageRequest(
    val action: String = "update",
    @SerialName("spreadsheetId") val spreadsheetId: String,
    val sheet: String,
    val id: String,
    val text: String,
)

@Serializable
data class DeleteMessageRequest(
    val action: String = "delete",
    @SerialName("spreadsheetId") val spreadsheetId: String,
    val sheet: String,
    val id: String,
)

@Serializable
data class SimpleResponse(
    val ok: Boolean,
    val error: String? = null,
)
