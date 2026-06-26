package com.example.exelgramm.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [MessageEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
}
