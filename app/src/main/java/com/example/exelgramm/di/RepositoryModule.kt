package com.example.exelgramm.di

import com.example.exelgramm.data.repository.AuthRepository
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
}
