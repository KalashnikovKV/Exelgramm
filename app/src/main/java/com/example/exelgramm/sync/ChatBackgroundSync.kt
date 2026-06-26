package com.example.exelgramm.sync

/** Background sync abstraction (WorkManager). */
interface ChatBackgroundSync {
    fun schedule()
    fun cancel()
}
