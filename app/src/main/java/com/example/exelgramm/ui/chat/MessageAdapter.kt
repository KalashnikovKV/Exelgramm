package com.example.exelgramm.ui.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.exelgramm.R
import com.example.exelgramm.core.TimeFormats
import com.example.exelgramm.domain.model.Message

class MessageAdapter(
    private val currentAuthor: () -> String,
) : ListAdapter<Message, MessageAdapter.Holder>(Diff) {

    override fun getItemViewType(position: Int): Int {
        val message = getItem(position)
        return if (message.isMine(currentAuthor())) VIEW_OUTGOING else VIEW_INCOMING
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
        holder.bind(getItem(position), getItemViewType(position) == VIEW_INCOMING)
    }

    class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val author: TextView = itemView.findViewById(R.id.messageAuthor)
        private val text: TextView = itemView.findViewById(R.id.messageText)
        private val time: TextView = itemView.findViewById(R.id.messageTime)

        fun bind(message: Message, incoming: Boolean) {
            if (incoming) {
                author.visibility = View.VISIBLE
                author.text = message.author
            } else {
                author.visibility = View.GONE
            }
            text.text = message.text
            time.text = TimeFormats.formatChatTime(message.timestamp)
        }
    }

    private object Diff : DiffUtil.ItemCallback<Message>() {
        override fun areItemsTheSame(oldItem: Message, newItem: Message): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Message, newItem: Message): Boolean =
            oldItem == newItem
    }

    companion object {
        private const val VIEW_INCOMING = 0
        private const val VIEW_OUTGOING = 1
    }
}
