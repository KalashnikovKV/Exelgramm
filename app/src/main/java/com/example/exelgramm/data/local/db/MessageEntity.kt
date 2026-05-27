package com.example.exelgramm.data.local.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.exelgramm.domain.model.Message

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
) {
    fun toDomain(): Message = Message(id = id, timestamp = timestamp, author = author, text = text)
}

fun Message.toEntity(spreadsheetId: String, sheetName: String) = MessageEntity(
    id = id,
    timestamp = timestamp,
    author = author,
    text = text,
    spreadsheetId = spreadsheetId,
    sheetName = sheetName,
)
