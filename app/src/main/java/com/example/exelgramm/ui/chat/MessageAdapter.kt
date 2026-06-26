package com.example.exelgramm.ui.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.exelgramm.R
import com.example.exelgramm.domain.model.MessageType

/**
 * Uses [MessageUiItem]: type (incoming/outgoing) and time are computed in the ViewModel;
 * the adapter only renders.
 */
class MessageAdapter(
    private val onMessageLongClick: (MessageUiItem) -> Unit,
) : ListAdapter<MessageUiItem, MessageAdapter.Holder>(Diff) {

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
        holder.bind(getItem(position), onMessageLongClick)
    }

    class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val typeBadge: TextView = itemView.findViewById(R.id.messageTypeBadge)
        private val author: TextView = itemView.findViewById(R.id.messageAuthor)
        private val text: TextView = itemView.findViewById(R.id.messageText)
        private val time: TextView = itemView.findViewById(R.id.messageTime)
        private val pendingStatus: TextView? = itemView.findViewById(R.id.messagePendingStatus)
        private val errorStatus: TextView? = itemView.findViewById(R.id.messageErrorStatus)

        fun bind(
            item: MessageUiItem,
            onMessageLongClick: (MessageUiItem) -> Unit,
        ) {
            itemView.isLongClickable = true
            itemView.setOnLongClickListener {
                onMessageLongClick(item)
                true
            }
            when (item) {
                is MessageUiItem.Incoming -> {
                    typeBadge.isVisible = item.messageType == MessageType.IMPORTANT
                    author.visibility = View.VISIBLE
                    author.text = item.author
                    text.text = item.text
                    time.text = item.time
                }
                is MessageUiItem.Outgoing -> {
                    typeBadge.isVisible = item.messageType == MessageType.IMPORTANT
                    author.visibility = View.GONE
                    text.text = item.text
                    when {
                        item.hasError -> {
                            text.alpha = 1f
                            itemView.background?.mutate()?.alpha = 255
                        }
                        item.isPending -> text.alpha = 0.7f
                        else -> text.alpha = 1f
                    }
                    time.text = item.time
                    pendingStatus?.isVisible = item.isPending && !item.hasError
                    errorStatus?.isVisible = item.hasError
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
