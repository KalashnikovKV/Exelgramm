package com.example.exelgramm.ui.participants

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.exelgramm.R

class ParticipantsAdapter(
    private val onItemClick: (ParticipantItem) -> Unit,
) : ListAdapter<ParticipantItem, ParticipantsAdapter.Holder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_participant_row, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(getItem(position), onItemClick)
    }

    class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val colAuthor: TextView = itemView.findViewById(R.id.colAuthor)
        private val colTextCount: TextView = itemView.findViewById(R.id.colTextCount)
        private val colImportantCount: TextView = itemView.findViewById(R.id.colImportantCount)
        private val colLastTime: TextView = itemView.findViewById(R.id.colLastTime)

        fun bind(item: ParticipantItem, onClick: (ParticipantItem) -> Unit) {
            colAuthor.text = item.author
            colTextCount.text = item.textMessages.toString()
            colImportantCount.text = item.importantMessages.toString()
            colLastTime.text = item.lastMessageTime
            itemView.setOnClickListener { onClick(item) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<ParticipantItem>() {
        override fun areItemsTheSame(oldItem: ParticipantItem, newItem: ParticipantItem) =
            oldItem.author == newItem.author
        override fun areContentsTheSame(oldItem: ParticipantItem, newItem: ParticipantItem) =
            oldItem == newItem
    }
}
