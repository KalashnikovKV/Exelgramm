package com.example.exelgramm.data.local

import kotlinx.coroutines.flow.Flow

/** Read-only доступ к session-flow для UI-слоя (тестируемая абстракция). */
interface SessionProvider {
    val authSession: Flow<AuthSession>
    val chatConfig: Flow<ChatConfig>
}
