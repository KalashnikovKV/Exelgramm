package com.example.exelgramm.ui.chat

import com.example.exelgramm.domain.model.Message

/**
 * Оптимистичные операции, ожидающие подтверждения от сервера.
 *
 * @param sends  ID сообщений, отправленных локально, но ещё не полученных с сервера.
 * @param deletes ID сообщений, удалённых локально, но ещё не удалённых на сервере.
 * @param edits  ID → новый текст для сообщений, отредактированных локально.
 */
data class PendingOps(
    val sends: Set<String> = emptySet(),
    val deletes: Set<String> = emptySet(),
    val edits: Map<String, String> = emptyMap(),
) {
    val isEmpty: Boolean get() = sends.isEmpty() && deletes.isEmpty() && edits.isEmpty()
}

/**
 * Чистая функция слияния серверного списка сообщений с локальным оптимистичным состоянием.
 *
 * Алгоритм:
 * 1. Снимает флаги pending с операций, которые уже отразились на сервере.
 * 2. Применяет оставшиеся pending-операции поверх серверного списка.
 * 3. Добавляет unsyncedSends — сообщения, ещё не дошедшие до сервера.
 *
 * Предусловие: [remote] и [unsyncedSends] отсортированы по timestamp по возрастанию
 * (инвариант поддерживается на всех путях записи). Благодаря этому финальное слияние
 * выполняется за O(n) вместо повторной сортировки O(n log n) на каждый poll.
 *
 * Функция не имеет побочных эффектов: входные данные не изменяются.
 */
fun mergeMessages(
    remote: List<Message>,
    pending: PendingOps,
    unsyncedSends: List<Message>,
): Pair<List<Message>, PendingOps> {
    val remoteById = remote.associateBy { it.id }

    val newSends = pending.sends.filterTo(mutableSetOf()) { it !in remoteById }
    val newDeletes = pending.deletes.filterTo(mutableSetOf()) { it in remoteById }
    val newEdits = pending.edits.filterTo(mutableMapOf()) { (id, text) ->
        remoteById[id]?.text != text
    }

    val synced = remote
        .filter { it.id !in newDeletes }
        .map { m -> newEdits[m.id]?.let { m.copy(text = it) } ?: m }

    val stillUnsynced = unsyncedSends.filter { it.id in newSends }
    val merged = mergeSortedByTimestamp(synced, stillUnsynced)

    return merged to PendingOps(
        sends = newSends,
        deletes = newDeletes,
        edits = newEdits,
    )
}

/**
 * Сливает инкрементальную дельту с сервером (без optimistic sends), сохраняя дедуп по id.
 *
 * Предусловие: [existing] и [delta] отсортированы по timestamp. Обновлённые записи
 * (тот же id, новое содержимое) берутся из [delta]. Слияние — O(n + m).
 */
fun mergeRemoteDelta(existing: List<Message>, delta: List<Message>): List<Message> {
    if (delta.isEmpty()) return existing
    val deltaIds = delta.mapTo(HashSet()) { it.id }
    val kept = existing.filter { it.id !in deltaIds }
    return mergeSortedByTimestamp(kept, delta)
}

/**
 * Слияние двух отсортированных по timestamp списков за O(n + m) (merge-шаг из merge sort).
 * При равных timestamp элементы из [a] идут первыми (стабильность).
 */
internal fun mergeSortedByTimestamp(a: List<Message>, b: List<Message>): List<Message> {
    if (a.isEmpty()) return b
    if (b.isEmpty()) return a
    val result = ArrayList<Message>(a.size + b.size)
    var i = 0
    var j = 0
    while (i < a.size && j < b.size) {
        if (a[i].timestamp <= b[j].timestamp) {
            result.add(a[i++])
        } else {
            result.add(b[j++])
        }
    }
    while (i < a.size) result.add(a[i++])
    while (j < b.size) result.add(b[j++])
    return result
}
