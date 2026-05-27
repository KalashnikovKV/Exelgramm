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
import com.example.exelgramm.ExelgrammApp
import com.example.exelgramm.R
import com.example.exelgramm.core.ErrorTexts
import com.example.exelgramm.data.local.UserSession
import com.example.exelgramm.data.remote.SheetLinkParser
import com.example.exelgramm.data.repository.ChatRepository
import com.example.exelgramm.databinding.FragmentChatBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ChatFragment : Fragment() {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!

    private val app get() = requireActivity().application as ExelgrammApp

    private val viewModel: ChatViewModel by viewModels {
        ChatViewModel.Factory(app.sessionStore)
    }

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

        adapter = MessageAdapter { viewModel.uiState.value.session.displayName }
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
            val session = app.sessionStore.session.first()
            binding.sheetUrlInput.setText(session.sheetUrl)
            binding.webAppUrlInput.setText(session.webAppUrl)
            prefillConnectFields(session)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    render(state)
                }
            }
        }
    }

    private fun prefillConnectFields(session: UserSession) {
        binding.sheetNameInput.setText(session.sheetName.ifBlank { getString(R.string.default_sheet_name) })
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
        val sheetName = binding.sheetNameInput.text?.toString().orEmpty().ifBlank { "Лист1" }

        val spreadsheetId = SheetLinkParser.parseSpreadsheetId(sheetUrl)
        if (spreadsheetId == null) {
            Toast.makeText(requireContext(), R.string.error_invalid_sheet_url, Toast.LENGTH_LONG).show()
            return
        }
        if (webAppUrl.isBlank()) {
            Toast.makeText(requireContext(), R.string.error_web_app_url_required, Toast.LENGTH_LONG).show()
            return
        }
        if (!webAppUrl.contains("script.google.com/macros/s/")) {
            Toast.makeText(requireContext(), R.string.error_web_app_must_be_exec, Toast.LENGTH_LONG).show()
            return
        }

        binding.connectButton.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            val normalizedWebApp = SheetLinkParser.canonicalExecUrl(webAppUrl)
            app.sessionStore.saveChatConfig(
                sheetUrl = sheetUrl,
                spreadsheetId = spreadsheetId,
                sheetName = sheetName,
                webAppUrl = normalizedWebApp,
            )
            val session = app.sessionStore.session.first()
            ChatRepository().loadMessages(session)
                .onSuccess { messages ->
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.chat_connected_ok, messages.size),
                        Toast.LENGTH_LONG,
                    ).show()
                    viewModel.refresh()
                }
                .onFailure { e ->
                    Toast.makeText(
                        requireContext(),
                        ErrorTexts.from(e),
                        Toast.LENGTH_LONG,
                    ).show()
                }
            binding.connectButton.isEnabled = true
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
