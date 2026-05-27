package com.example.exelgramm

import android.app.Application
import com.example.exelgramm.data.local.SessionStore
import com.example.exelgramm.data.repository.AuthRepository
import com.example.exelgramm.data.repository.SessionAuthRepository

class ExelgrammApp : Application() {

    lateinit var sessionStore: SessionStore
        private set

    lateinit var authRepository: AuthRepository
        private set

    override fun onCreate() {
        super.onCreate()
        sessionStore = SessionStore(this)
        authRepository = SessionAuthRepository(sessionStore)
    }
}
