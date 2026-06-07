package com.example.exelgramm.ui.chat

import android.os.Bundle
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.exelgramm.R
import com.example.exelgramm.databinding.FragmentChatBinding
import com.example.exelgramm.domain.model.MessageType
import com.example.exelgramm.ui.common.collectOnStarted
import com.example.exelgramm.ui.common.syncFromState
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ChatFragment : Fragment() {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ChatViewModel by viewModels()

    private lateinit var adapter: MessageAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = MessageAdapter { message -> showMessageActions(message) }
        binding.messagesList.layoutManager = LinearLayoutManager(requireContext()).apply {
            stackFromEnd = true
        }
        binding.messagesList.adapter = adapter

        binding.chatRefresh.setOnRefreshListener { viewModel.refresh() }
        binding.connectButton.setOnClickListener { connectChat() }
        binding.sendButton.setOnClickListener { sendFromInput() }
        binding.btnTypeToggle.setOnClickListener { viewModel.toggleInputType() }

        binding.messageInput.doAfterTextChanged { text ->
            viewModel.onInputChanged(text?.toString().orEmpty())
        }
        binding.messageInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendFromInput()
                true
            } else {
                false
            }
        }

        binding.sheetUrlInput.doAfterTextChanged { viewModel.onSheetUrlChanged(it?.toString().orEmpty()) }
        binding.webAppUrlInput.doAfterTextChanged { viewModel.onWebAppUrlChanged(it?.toString().orEmpty()) }
        binding.sheetNameInput.doAfterTextChanged { viewModel.onSheetNameChanged(it?.toString().orEmpty()) }

        collectOnStarted(viewModel.uiState) { state ->
            binding.sheetUrlInput.syncFromState(state.sheetUrlDraft)
            binding.webAppUrlInput.syncFromState(state.webAppUrlDraft)
            binding.sheetNameInput.syncFromState(state.sheetNameDraft)
            render(state)
        }

        collectOnStarted(viewModel.effects) { effect ->
            when (effect) {
                is ChatEffect.ShowError -> Toast.makeText(
                    requireContext(),
                    effect.resId,
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    private fun render(state: ChatUiState) {
        val configured = state.session.isChatConfigured
        binding.connectPanel.isVisible = !configured
        binding.chatPanel.isVisible = configured

        binding.chatRefresh.isRefreshing = state.isLoading && configured
        binding.sendButton.isEnabled = !state.isLoading && configured

        val hasMessages = state.messages.isNotEmpty()
        binding.emptyChatText.isVisible = configured && !hasMessages
        binding.messagesList.isVisible = hasMessages

        adapter.submitList(state.messages) {
            if (state.messages.isNotEmpty()) {
                binding.messagesList.scrollToPosition(state.messages.lastIndex)
            }
        }

        syncMessageInput(state.inputText)
        renderTypeToggle(state.inputType)

        state.error?.let { msg ->
            binding.errorText.isVisible = configured
            binding.errorText.text = msg
        } ?: run {
            binding.errorText.isVisible = false
        }
    }

    private fun renderTypeToggle(inputType: String) {
        val isImportant = inputType == MessageType.IMPORTANT
        binding.btnTypeToggle.text = if (isImportant) "★" else "☆"
        binding.btnTypeToggle.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                if (isImportant) R.color.msg_type_important_color else R.color.tg_text_secondary,
            ),
        )
    }

    private fun syncMessageInput(text: String) {
        val current = binding.messageInput.text?.toString().orEmpty()
        if (current == text) return
        when {
            text.isEmpty() -> binding.messageInput.text?.clear()
            !binding.messageInput.isFocused || current.isEmpty() -> {
                binding.messageInput.setText(text)
                binding.messageInput.setSelection(text.length)
            }
        }
    }

    private fun sendFromInput() {
        viewModel.onInputChanged(binding.messageInput.text?.toString().orEmpty())
        viewModel.sendMessage()
    }

    private fun showMessageActions(message: MessageUiItem) {
        val options = if (message is MessageUiItem.Outgoing) {
            arrayOf(
                getString(R.string.chat_action_details),
                getString(R.string.chat_action_edit),
                getString(R.string.chat_action_delete),
            )
        } else {
            arrayOf(getString(R.string.chat_action_details))
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.chat_message_actions_title)
            .setItems(options) { _, which ->
                when (message) {
                    is MessageUiItem.Outgoing -> when (which) {
                        0 -> openMessageDetail(message.id)
                        1 -> showEditDialog(message)
                        2 -> showDeleteConfirmation(message.id)
                    }
                    is MessageUiItem.Incoming -> if (which == 0) {
                        openMessageDetail(message.id)
                    }
                }
            }
            .show()
    }

    private fun openMessageDetail(messageId: String) {
        findNavController().navigate(
            R.id.action_chat_to_message_detail,
            bundleOf("messageId" to messageId),
        )
    }

    private fun showEditDialog(message: MessageUiItem.Outgoing) {
        val input = EditText(requireContext()).apply {
            setText(message.text)
            setSelection(message.text.length)
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.chat_edit_title)
            .setView(input)
            .setPositiveButton(R.string.chat_action_save) { _, _ ->
                val updatedText = input.text?.toString().orEmpty()
                viewModel.editMessage(message.id, updatedText)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showDeleteConfirmation(messageId: String) {
        AlertDialog.Builder(requireContext())
            .setMessage(R.string.chat_delete_confirm)
            .setPositiveButton(R.string.chat_action_delete) { _, _ ->
                viewModel.deleteMessage(messageId)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun connectChat() {
        val state = viewModel.uiState.value
        viewModel.saveChatConfig(
            sheetUrl = state.sheetUrlDraft,
            webAppUrl = state.webAppUrlDraft,
            sheetName = state.sheetNameDraft.ifBlank { getString(R.string.default_sheet_name) },
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
