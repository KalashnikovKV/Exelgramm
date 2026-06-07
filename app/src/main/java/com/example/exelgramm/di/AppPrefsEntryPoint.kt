package com.example.exelgramm.di

import com.example.exelgramm.data.local.AppPrefsStore
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppPrefsEntryPoint {
    fun appPrefsStore(): AppPrefsStore
}
