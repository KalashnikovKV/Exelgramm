package com.example.exelgramm.ui.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.exelgramm.R
import com.example.exelgramm.domain.model.MessageType

class MessageTableAdapter(
    private val currentAuthor: () -> String,
    private val onMessageClick: (MessageUiItem) -> Unit,
) : ListAdapter<MessageUiItem, MessageTableAdapter.Holder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_message_table_row, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(getItem(position), currentAuthor(), onMessageClick)
    }

    class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val colAuthor: TextView = itemView.findViewById(R.id.colAuthor)
        private val colText: TextView = itemView.findViewById(R.id.colText)
        private val colType: TextView = itemView.findViewById(R.id.colType)
        private val colTime: TextView = itemView.findViewById(R.id.colTime)

        fun bind(
            item: MessageUiItem,
            currentAuthor: String,
            onMessageClick: (MessageUiItem) -> Unit,
        ) {
            val author = when (item) {
                is MessageUiItem.Incoming -> item.author
                is MessageUiItem.Outgoing -> currentAuthor
            }
            val text = when (item) {
                is MessageUiItem.Incoming -> item.text
                is MessageUiItem.Outgoing -> item.text
            }
            val time = when (item) {
                is MessageUiItem.Incoming -> item.time
                is MessageUiItem.Outgoing -> item.time
            }
            val messageType = when (item) {
                is MessageUiItem.Incoming -> item.messageType
                is MessageUiItem.Outgoing -> item.messageType
            }

            colAuthor.text = author
            colText.text = text
            colTime.text = time

            val isImportant = messageType == MessageType.IMPORTANT
            colType.text = if (isImportant) "★" else "○"
            colType.setTextColor(
                ContextCompat.getColor(
                    itemView.context,
                    if (isImportant) R.color.msg_type_important_color else R.color.tg_text_secondary,
                ),
            )

            itemView.setOnClickListener { onMessageClick(item) }
            itemView.setOnLongClickListener {
                onMessageClick(item)
                true
            }
        }
    }

    private object Diff : DiffUtil.ItemCallback<MessageUiItem>() {
        override fun areItemsTheSame(oldItem: MessageUiItem, newItem: MessageUiItem) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: MessageUiItem, newItem: MessageUiItem) =
            oldItem == newItem
    }
}
