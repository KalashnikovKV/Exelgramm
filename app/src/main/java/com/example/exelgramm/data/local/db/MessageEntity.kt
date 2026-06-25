package com.example.exelgramm.data.local.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.exelgramm.domain.model.Message
import com.example.exelgramm.domain.model.MessageType

@Entity(
    tableName = "messages",
    indices = [Index("spreadsheetId", "sheetName")],
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val timestamp: String,
    val author: String,
    val text: String,
    val spreadsheetId: String,
    val sheetName: String,
    val type: String = MessageType.TEXT.apiValue,
) {
    fun toDomain(): Message = Message(
        id = id,
        timestamp = timestamp,
        author = author,
        text = text,
        type = MessageType.fromString(type),
    )
}

fun Message.toEntity(spreadsheetId: String, sheetName: String) = MessageEntity(
    id = id,
    timestamp = timestamp,
    author = author,
    text = text,
    spreadsheetId = spreadsheetId,
    sheetName = sheetName,
    type = type.apiValue,
)
