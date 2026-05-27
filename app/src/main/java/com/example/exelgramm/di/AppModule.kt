package com.example.exelgramm.di

import android.content.Context
import androidx.room.Room
import com.example.exelgramm.data.local.db.AppDatabase
import com.example.exelgramm.data.local.db.MessageDao
import com.example.exelgramm.data.remote.AppsScriptApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Singleton
    @Provides
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "exelgramm.db")
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideMessageDao(db: AppDatabase): MessageDao = db.messageDao()

    @Singleton
    @Provides
    fun provideAppsScriptApi(): AppsScriptApi = AppsScriptApi()
}
