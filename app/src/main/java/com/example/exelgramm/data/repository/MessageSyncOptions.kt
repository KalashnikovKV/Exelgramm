package com.example.exelgramm.data.repository

/** Параметры синхронизации сообщений с сервером. */
data class MessageSyncOptions(
    /** ISO-timestamp: загрузить только сообщения новее этого значения. */
    val since: String? = null,
    /** true — полная замена кэша листа; false — upsert (инкрементальный poll). */
    val fullRefresh: Boolean = true,
)
