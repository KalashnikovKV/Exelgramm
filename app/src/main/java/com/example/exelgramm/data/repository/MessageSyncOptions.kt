package com.example.exelgramm.data.repository

import java.time.Instant

/** Message sync options for the server. */
data class MessageSyncOptions(
    /** Fetch only messages newer than this instant (incremental poll). */
    val since: Instant? = null,
    /** true — replace sheet cache; false — upsert (incremental poll). */
    val fullRefresh: Boolean = true,
)
