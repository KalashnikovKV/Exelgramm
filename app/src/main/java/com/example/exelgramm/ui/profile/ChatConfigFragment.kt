package com.example.exelgramm.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.exelgramm.ExelgrammApp
import com.example.exelgramm.R
import com.example.exelgramm.data.remote.SheetLinkParser
import com.example.exelgramm.databinding.FragmentChatConfigBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ChatConfigFragment : Fragment() {

    private var _binding: FragmentChatConfigBinding? = null
    private val binding get() = _binding!!

    private val store get() = (requireActivity().application as ExelgrammApp).sessionStore

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentChatConfigBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadConfig()
        binding.saveConfigButton.setOnClickListener { saveConfig() }
    }

    private fun loadConfig() {
        viewLifecycleOwner.lifecycleScope.launch {
            val session = store.session.first()
            binding.sheetUrlInput.setText(session.sheetUrl)
            binding.webAppUrlInput.setText(session.webAppUrl)
            binding.sheetNameInput.setText(session.sheetName)
        }
    }

    private fun saveConfig() {
        val sheetUrl = binding.sheetUrlInput.text?.toString().orEmpty()
        val webAppUrl = binding.webAppUrlInput.text?.toString().orEmpty()
        val sheetName = binding.sheetNameInput.text?.toString()
            .orEmpty().ifBlank { getString(R.string.default_sheet_name) }

        viewLifecycleOwner.lifecycleScope.launch {
            if (webAppUrl.isNotBlank() && !webAppUrl.contains("script.google.com/macros/s/")) {
                Toast.makeText(requireContext(), R.string.error_web_app_must_be_exec, Toast.LENGTH_LONG).show()
                return@launch
            }
            if (sheetUrl.isNotBlank() && webAppUrl.isNotBlank()) {
                val id = SheetLinkParser.parseSpreadsheetId(sheetUrl)
                if (id == null) {
                    Toast.makeText(requireContext(), R.string.error_invalid_sheet_url, Toast.LENGTH_SHORT).show()
                    return@launch
                }
                store.saveChatConfig(sheetUrl, id, sheetName, webAppUrl)
            } else if (webAppUrl.isNotBlank()) {
                store.saveWebAppUrl(webAppUrl)
            }
            Toast.makeText(requireContext(), R.string.config_saved, Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
