package com.example.exelgramm.data.repository

import java.time.Instant

/** Параметры синхронизации сообщений с сервером. */
data class MessageSyncOptions(
    /** Загрузить только сообщения новее этого момента (инкрементальный poll). */
    val since: Instant? = null,
    /** true — полная замена кэша листа; false — upsert (инкрементальный poll). */
    val fullRefresh: Boolean = true,
)
