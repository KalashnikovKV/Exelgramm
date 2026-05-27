package com.example.exelgramm.data.local.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface MessageDao {

    @Query(
        "SELECT * FROM messages WHERE spreadsheetId = :spreadsheetId AND sheetName = :sheetName " +
            "ORDER BY timestamp ASC",
    )
    suspend fun getAll(spreadsheetId: String, sheetName: String): List<MessageEntity>

    @Upsert
    suspend fun upsertAll(messages: List<MessageEntity>)

    @Query(
        "DELETE FROM messages WHERE spreadsheetId = :spreadsheetId AND sheetName = :sheetName",
    )
    suspend fun deleteForSheet(spreadsheetId: String, sheetName: String)
}
