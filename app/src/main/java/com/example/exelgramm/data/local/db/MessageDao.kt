package com.example.exelgramm.data.local.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert

@Dao
interface MessageDao {

    @Query(
        "SELECT * FROM messages WHERE spreadsheetId = :spreadsheetId AND sheetName = :sheetName " +
            "ORDER BY timestamp ASC",
    )
    suspend fun getAll(spreadsheetId: String, sheetName: String): List<MessageEntity>

    @Query(
        "SELECT * FROM messages WHERE id = :id AND spreadsheetId = :spreadsheetId " +
            "AND sheetName = :sheetName LIMIT 1",
    )
    suspend fun getById(id: String, spreadsheetId: String, sheetName: String): MessageEntity?

    @Upsert
    suspend fun upsertAll(messages: List<MessageEntity>)

    /** Replaces the full sheet cache — removes messages deleted on the server. */
    @Transaction
    suspend fun replaceForSheet(
        spreadsheetId: String,
        sheetName: String,
        messages: List<MessageEntity>,
    ) {
        deleteForSheet(spreadsheetId, sheetName)
        if (messages.isNotEmpty()) {
            upsertAll(messages)
        }
    }

    @Query(
        "UPDATE messages SET text = :text WHERE id = :id AND spreadsheetId = :spreadsheetId AND sheetName = :sheetName",
    )
    suspend fun updateText(id: String, spreadsheetId: String, sheetName: String, text: String)

    @Query(
        "DELETE FROM messages WHERE id = :id AND spreadsheetId = :spreadsheetId AND sheetName = :sheetName",
    )
    suspend fun deleteById(id: String, spreadsheetId: String, sheetName: String)

    @Query(
        "DELETE FROM messages WHERE spreadsheetId = :spreadsheetId AND sheetName = :sheetName",
    )
    suspend fun deleteForSheet(spreadsheetId: String, sheetName: String)
}
