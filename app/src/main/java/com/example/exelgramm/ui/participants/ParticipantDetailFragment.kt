package com.example.exelgramm.ui.participants

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.exelgramm.R
import com.example.exelgramm.databinding.FragmentParticipantDetailBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ParticipantDetailFragment : Fragment() {

    private var _binding: FragmentParticipantDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ParticipantDetailViewModel by viewModels()
    private lateinit var messagesAdapter: ParticipantMessagesAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentParticipantDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        messagesAdapter = ParticipantMessagesAdapter { item ->
            findNavController().navigate(
                R.id.action_participant_detail_to_message_detail,
                bundleOf("messageId" to item.id),
            )
        }
        binding.messagesList.layoutManager = LinearLayoutManager(requireContext())
        binding.messagesList.adapter = messagesAdapter
        binding.messagesList.addItemDecoration(
            DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL),
        )

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    render(state)
                }
            }
        }
    }

    private fun render(state: ParticipantDetailUiState) {
        val name = state.authorName
        binding.authorNameText.text = name
        binding.avatarText.text = name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        binding.totalCountText.text = state.totalMessages.toString()
        binding.textCountStat.text = state.textMessages.toString()
        binding.importantCountStat.text = state.importantMessages.toString()
        binding.firstMessageTime.text = state.firstMessageTime.ifBlank { "—" }
        binding.lastMessageTime.text = state.lastMessageTime.ifBlank { "—" }
        binding.noMessagesText.isVisible = state.messages.isEmpty()
        binding.messagesList.isVisible = state.messages.isNotEmpty()
        messagesAdapter.submitList(state.messages)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onResume() {
        super.onResume()
        viewModel.load()
    }
}

class ParticipantMessagesAdapter(
    private val onMessageClick: (ParticipantMessageItem) -> Unit,
) : ListAdapter<ParticipantMessageItem, ParticipantMessagesAdapter.Holder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_participant_message, parent, false)
        return Holder(view, onMessageClick)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(getItem(position))
    }

    class Holder(
        itemView: View,
        private val onMessageClick: (ParticipantMessageItem) -> Unit,
    ) : RecyclerView.ViewHolder(itemView) {
        private val typeBadge: TextView = itemView.findViewById(R.id.msgTypeBadge)
        private val msgText: TextView = itemView.findViewById(R.id.msgText)
        private val msgTime: TextView = itemView.findViewById(R.id.msgTime)

        fun bind(item: ParticipantMessageItem) {
            itemView.setOnClickListener { onMessageClick(item) }
            typeBadge.text = if (item.isImportant) "★" else "○"
            typeBadge.setTextColor(
                itemView.context.getColor(
                    if (item.isImportant) R.color.msg_type_important_color
                    else R.color.tg_text_secondary,
                ),
            )
            msgText.text = item.text
            msgTime.text = item.time
        }
    }

    private object Diff : DiffUtil.ItemCallback<ParticipantMessageItem>() {
        override fun areItemsTheSame(a: ParticipantMessageItem, b: ParticipantMessageItem) = a.id == b.id
        override fun areContentsTheSame(a: ParticipantMessageItem, b: ParticipantMessageItem) = a == b
    }
}
