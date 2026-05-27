package com.example.exelgramm

import android.app.Application
import com.example.exelgramm.data.local.SessionStore

class ExelgrammApp : Application() {
    lateinit var sessionStore: SessionStore
        private set

    override fun onCreate() {
        super.onCreate()
        sessionStore = SessionStore(this)
    }
}
