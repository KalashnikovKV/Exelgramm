package com.example.exelgramm.ui.message

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.exelgramm.R
import com.example.exelgramm.core.TimeFormats
import com.example.exelgramm.databinding.FragmentMessageDetailBinding
import com.example.exelgramm.domain.model.MessageType
import com.example.exelgramm.ui.common.collectOnStarted
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MessageDetailFragment : Fragment() {

    private var _binding: FragmentMessageDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MessageDetailViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentMessageDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        collectOnStarted(viewModel.uiState) { state ->
            render(state)
        }
    }

    private fun render(state: MessageDetailUiState) {
        val message = state.message
        binding.notFoundText.isVisible = state.notFound && !state.isLoading
        binding.contentPanel.isVisible = message != null
        binding.messageTextLabel.isVisible = message != null
        binding.messageText.isVisible = message != null
        binding.openAuthorButton.isVisible = message != null

        if (message == null) return

        val author = message.author
        binding.authorNameText.text = author
        binding.avatarText.text = author.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        binding.mineLabel.isVisible = state.isMine

        val isImportant = message.type == MessageType.IMPORTANT
        binding.typeText.text = getString(
            if (isImportant) R.string.msg_type_important else R.string.msg_type_text,
        )
        binding.typeText.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                if (isImportant) R.color.msg_type_important_color else R.color.tg_text_primary,
            ),
        )

        binding.timeText.text = TimeFormats.formatFullDateTime(message.timestamp)
        binding.idText.text = message.id
        binding.messageText.text = message.text

        binding.openAuthorButton.setOnClickListener {
            findNavController().navigate(
                R.id.action_message_detail_to_participant,
                bundleOf("authorName" to author),
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
