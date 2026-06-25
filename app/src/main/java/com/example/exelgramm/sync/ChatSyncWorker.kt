package com.example.exelgramm.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.exelgramm.data.local.SessionStore
import com.example.exelgramm.data.repository.ChatRepository
import com.example.exelgramm.data.repository.MessageSyncOptions
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class ChatSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val sessionStore: SessionStore,
    private val chatRepository: ChatRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val config = sessionStore.chatConfig.first()
        if (!config.isConfigured) return Result.success()
        return chatRepository.loadMessages(
            config,
            MessageSyncOptions(fullRefresh = true),
        ).fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() },
        )
    }
}
