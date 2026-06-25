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
 * 3. Добавляет в конец unsyncedSends — сообщения, ещё не дошедшие до сервера.
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
    val merged = (synced + stillUnsynced).sortedBy { it.timestamp }

    return merged to PendingOps(
        sends = newSends,
        deletes = newDeletes,
        edits = newEdits,
    )
}
