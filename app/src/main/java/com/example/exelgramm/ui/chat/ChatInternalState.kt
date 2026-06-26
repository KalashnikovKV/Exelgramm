package com.example.exelgramm.ui.chat

import com.example.exelgramm.domain.model.Message
import java.time.Instant

/** Локальное состояние чата: серверные сообщения + оптимистичные операции. */
data class ChatInternalState(
    val rawMessages: List<Message> = emptyList(),
    val pendingOps: PendingOps = PendingOps(),
    /** ID сообщений с ошибкой отправки/редактирования (остаются в списке). */
    val failedIds: Set<String> = emptySet(),
    /** Время последнего подтверждённого сервером сообщения (для инкрементального poll). */
    val lastRemoteTimestamp: Instant? = null,
)
