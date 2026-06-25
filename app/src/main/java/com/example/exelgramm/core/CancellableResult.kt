package com.example.exelgramm.core

import kotlinx.coroutines.CancellationException

/**
 * Аналог [runCatching], но пробрасывает [CancellationException] вместо того чтобы её ловить.
 * Стандартный [runCatching] нарушает кооперативную отмену корутин: отмена превращается
 * в Result.failure вместо того чтобы отменить всю цепочку корутин.
 */
inline fun <T> runCatchingCancellable(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (c: CancellationException) {
        throw c
    } catch (t: Throwable) {
        Result.failure(t)
    }

/** Suspend-версия [runCatchingCancellable] для асинхронных операций (HTTP и т.д.). */
suspend inline fun <T> runSuspendCatchingCancellable(crossinline block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (c: CancellationException) {
        throw c
    } catch (t: Throwable) {
        Result.failure(t)
    }
