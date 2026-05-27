package com.example.exelgramm.ui.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.exelgramm.R
import com.example.exelgramm.data.repository.ChatConfigValidator
import com.example.exelgramm.databinding.FragmentChatBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

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

        adapter = MessageAdapter()
        binding.messagesList.layoutManager = LinearLayoutManager(requireContext()).apply {
            stackFromEnd = true
        }
        binding.messagesList.adapter = adapter

        binding.chatRefresh.setOnRefreshListener { viewModel.refresh() }
        binding.connectButton.setOnClickListener { connectChat() }
        binding.sendButton.setOnClickListener { sendFromInput() }

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

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    // Заполнить поля при первой загрузке сессии
                    if (binding.sheetUrlInput.text.isNullOrEmpty() && state.session.sheetUrl.isNotEmpty()) {
                        binding.sheetUrlInput.setText(state.session.sheetUrl)
                    }
                    if (binding.webAppUrlInput.text.isNullOrEmpty() && state.session.webAppUrl.isNotEmpty()) {
                        binding.webAppUrlInput.setText(state.session.webAppUrl)
                    }
                    if (binding.sheetNameInput.text.isNullOrEmpty()) {
                        binding.sheetNameInput.setText(
                            state.session.sheetName.ifBlank { getString(R.string.default_sheet_name) },
                        )
                    }
                    render(state)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.effects.collect { effect ->
                    when (effect) {
                        is ChatEffect.ShowToast -> Toast.makeText(
                            requireContext(),
                            effect.message,
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
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

        state.error?.let { msg ->
            binding.errorText.isVisible = configured
            binding.errorText.text = msg
        } ?: run {
            binding.errorText.isVisible = false
        }
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

    private fun connectChat() {
        val sheetUrl = binding.sheetUrlInput.text?.toString().orEmpty()
        val webAppUrl = binding.webAppUrlInput.text?.toString().orEmpty()
        val sheetName = binding.sheetNameInput.text?.toString()
            .orEmpty().ifBlank { getString(R.string.default_sheet_name) }

        when (val result = ChatConfigValidator.validate(sheetUrl, webAppUrl)) {
            is ChatConfigValidator.Result.Failure ->
                Toast.makeText(requireContext(), result.errorResId, Toast.LENGTH_LONG).show()
            is ChatConfigValidator.Result.Success ->
                viewModel.saveChatConfig(sheetUrl, result.spreadsheetId, webAppUrl, sheetName)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
