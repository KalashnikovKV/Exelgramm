package com.example.exelgramm.core

import kotlinx.coroutines.CancellationException

/**
 * Like [runCatching], but rethrows [CancellationException] instead of catching it.
 * Standard [runCatching] breaks cooperative coroutine cancellation by turning
 * cancellation into Result.failure.
 */
inline fun <T> runCatchingCancellable(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (c: CancellationException) {
        throw c
    } catch (t: Throwable) {
        Result.failure(t)
    }

/** Suspend variant of [runCatchingCancellable] for async work (HTTP, etc.). */
suspend inline fun <T> runSuspendCatchingCancellable(crossinline block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (c: CancellationException) {
        throw c
    } catch (t: Throwable) {
        Result.failure(t)
    }
