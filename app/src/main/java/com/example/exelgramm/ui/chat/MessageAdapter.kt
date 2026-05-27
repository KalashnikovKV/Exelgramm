package com.example.exelgramm.ui.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.exelgramm.R

/**
 * Работает с [MessageUiItem]: тип (incoming/outgoing) и время уже вычислены во ViewModel,
 * адаптер — только отображение.
 */
class MessageAdapter : ListAdapter<MessageUiItem, MessageAdapter.Holder>(Diff) {

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is MessageUiItem.Outgoing -> VIEW_OUTGOING
        is MessageUiItem.Incoming -> VIEW_INCOMING
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val layout = if (viewType == VIEW_OUTGOING) {
            R.layout.item_message_outgoing
        } else {
            R.layout.item_message_incoming
        }
        val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(getItem(position))
    }

    class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val author: TextView = itemView.findViewById(R.id.messageAuthor)
        private val text: TextView = itemView.findViewById(R.id.messageText)
        private val time: TextView = itemView.findViewById(R.id.messageTime)

        fun bind(item: MessageUiItem) {
            when (item) {
                is MessageUiItem.Incoming -> {
                    author.visibility = View.VISIBLE
                    author.text = item.author
                    text.text = item.text
                    time.text = item.time
                }
                is MessageUiItem.Outgoing -> {
                    author.visibility = View.GONE
                    text.text = item.text
                    time.text = item.time
                }
            }
        }
    }

    private object Diff : DiffUtil.ItemCallback<MessageUiItem>() {
        override fun areItemsTheSame(oldItem: MessageUiItem, newItem: MessageUiItem): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: MessageUiItem, newItem: MessageUiItem): Boolean =
            oldItem == newItem
    }

    companion object {
        private const val VIEW_INCOMING = 0
        private const val VIEW_OUTGOING = 1
    }
}
