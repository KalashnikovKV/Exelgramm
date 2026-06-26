package com.example.exelgramm.data.local

import kotlinx.coroutines.flow.Flow

/** Read-only session flows for the UI layer (testable abstraction). */
interface SessionProvider {
    val authSession: Flow<AuthSession>
    val chatConfig: Flow<ChatConfig>
}
