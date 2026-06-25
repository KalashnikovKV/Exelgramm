package com.example.exelgramm.sync

/** Абстракция фоновой синхронизации (WorkManager). */
interface ChatBackgroundSync {
    fun schedule()
    fun cancel()
}
