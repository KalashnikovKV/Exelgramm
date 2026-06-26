package com.example.exelgramm.ui.chat

import com.example.exelgramm.domain.model.Message

/**
 * Optimistic operations awaiting server confirmation.
 *
 * @param sends IDs of messages sent locally but not yet received from the server.
 * @param deletes IDs of messages deleted locally but not yet deleted on the server.
 * @param edits ID to new text for messages edited locally.
 */
data class PendingOps(
    val sends: Set<String> = emptySet(),
    val deletes: Set<String> = emptySet(),
    val edits: Map<String, String> = emptyMap(),
) {
    val isEmpty: Boolean get() = sends.isEmpty() && deletes.isEmpty() && edits.isEmpty()
}

/**
 * Pure function merging the server message list with local optimistic state.
 *
 * Algorithm:
 * 1. Clears pending flags for operations already reflected on the server.
 * 2. Applies remaining pending operations on top of the server list.
 * 3. Appends unsyncedSends — messages not yet on the server.
 *
 * Precondition: [remote] and [unsyncedSends] are sorted by timestamp ascending
 * (invariant maintained on all write paths). Final merge is O(n) instead of
 * re-sorting O(n log n) on every poll.
 *
 * Side-effect free: input data is not mutated.
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
 * Merges an incremental server delta (no optimistic sends), deduplicating by id.
 *
 * Precondition: [existing] and [delta] are sorted by timestamp. Updated rows
 * (same id, new content) come from [delta]. Merge cost is O(n + m).
 */
fun mergeRemoteDelta(existing: List<Message>, delta: List<Message>): List<Message> {
    if (delta.isEmpty()) return existing
    val deltaIds = delta.mapTo(HashSet()) { it.id }
    val kept = existing.filter { it.id !in deltaIds }
    return mergeSortedByTimestamp(kept, delta)
}

/**
 * Merges two timestamp-sorted lists in O(n + m) (merge step from merge sort).
 * On equal timestamps, elements from [a] come first (stable).
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
