package com.example.exelgramm.ui.chat

import com.example.exelgramm.domain.model.Message
import java.time.Instant

/** Local chat state: server messages plus optimistic operations. */
data class ChatInternalState(
    val rawMessages: List<Message> = emptyList(),
    val pendingOps: PendingOps = PendingOps(),
    /** Message IDs that failed to send/edit (kept in the list). */
    val failedIds: Set<String> = emptySet(),
    /** Timestamp of the last server-confirmed message (incremental poll cursor). */
    val lastRemoteTimestamp: Instant? = null,
)
