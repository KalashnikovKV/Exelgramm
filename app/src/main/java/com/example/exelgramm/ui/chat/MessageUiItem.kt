package com.example.exelgramm.ui.chat

import com.example.exelgramm.domain.model.MessageType

/**
 * Presentation model for a message: time is formatted, incoming/outgoing type
 * is resolved in the ViewModel — the adapter does not need the current author.
 * [messageType] is the content type, fixed at creation.
 */
sealed class MessageUiItem(open val id: String) {

    data class Incoming(
        override val id: String,
        val author: String,
        val text: String,
        val time: String,
        val messageType: MessageType,
    ) : MessageUiItem(id)

    data class Outgoing(
        override val id: String,
        val text: String,
        val time: String,
        val messageType: MessageType,
        val isPending: Boolean = false,
        val hasError: Boolean = false,
    ) : MessageUiItem(id)
}
