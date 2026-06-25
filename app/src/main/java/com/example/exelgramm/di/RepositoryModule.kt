package com.example.exelgramm.di

import com.example.exelgramm.data.local.SessionProvider
import com.example.exelgramm.data.local.SessionStore
import com.example.exelgramm.sync.ChatBackgroundSync
import com.example.exelgramm.sync.ChatSyncScheduler
import com.example.exelgramm.data.remote.AppsScriptApi
import com.example.exelgramm.data.remote.CsvMessagesClient
import com.example.exelgramm.data.remote.CsvSheetReader
import com.example.exelgramm.data.remote.MessagesApiClient
import com.example.exelgramm.data.repository.AuthRepository
import com.example.exelgramm.data.repository.ChatRepository
import com.example.exelgramm.data.repository.DefaultChatRepository
import com.example.exelgramm.data.repository.SessionAuthRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: SessionAuthRepository): AuthRepository

    @Binds
    @Singleton
    abstract fun bindChatRepository(impl: DefaultChatRepository): ChatRepository

    @Binds
    @Singleton
    abstract fun bindMessagesApiClient(impl: AppsScriptApi): MessagesApiClient

    @Binds
    @Singleton
    abstract fun bindCsvMessagesClient(impl: CsvSheetReader): CsvMessagesClient

    @Binds
    @Singleton
    abstract fun bindChatBackgroundSync(impl: ChatSyncScheduler): ChatBackgroundSync

    @Binds
    @Singleton
    abstract fun bindSessionProvider(impl: SessionStore): SessionProvider
}
