package com.example.exelgramm.ui.chat

/**
 * Презентационная модель сообщения: время уже отформатировано, тип (incoming/outgoing)
 * вычислен во ViewModel — адаптеру не нужен доступ к текущему автору.
 */
sealed class MessageUiItem(open val id: String) {

    data class Incoming(
        override val id: String,
        val author: String,
        val text: String,
        val time: String,
    ) : MessageUiItem(id)

    data class Outgoing(
        override val id: String,
        val text: String,
        val time: String,
    ) : MessageUiItem(id)
}
