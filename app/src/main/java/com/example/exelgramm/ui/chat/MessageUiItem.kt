package com.example.exelgramm.ui.chat

import com.example.exelgramm.domain.model.MessageType

/**
 * Презентационная модель сообщения: время уже отформатировано, тип (incoming/outgoing)
 * вычислен во ViewModel — адаптеру не нужен доступ к текущему автору.
 * [messageType] — тип содержимого сообщения, фиксируется при создании.
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
