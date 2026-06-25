package com.example.exelgramm.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.exelgramm.R
import com.example.exelgramm.databinding.FragmentChatConfigBinding
import com.example.exelgramm.ui.common.collectOnStarted
import com.example.exelgramm.ui.common.syncFromState
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ChatConfigFragment : Fragment() {

    private var _binding: FragmentChatConfigBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ChatConfigViewModel by activityViewModels()

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

        binding.saveConfigButton.setOnClickListener { saveConfig() }

        collectOnStarted(viewModel.uiState) { state ->
            if (binding.sheetUrlInput.text.isNullOrEmpty() && state.sheetUrl.isNotEmpty()) {
                binding.sheetUrlInput.syncFromState(state.sheetUrl)
            }
            if (binding.webAppUrlInput.text.isNullOrEmpty() && state.webAppUrl.isNotEmpty()) {
                binding.webAppUrlInput.syncFromState(state.webAppUrl)
            }
            if (binding.sheetNameInput.text.isNullOrEmpty()) {
                binding.sheetNameInput.syncFromState(state.sheetName)
            }
        }

        collectOnStarted(viewModel.effects) { effect ->
            when (effect) {
                is ChatConfigEffect.ShowError ->
                    Toast.makeText(requireContext(), effect.resId, Toast.LENGTH_LONG).show()
                is ChatConfigEffect.ShowSaved ->
                    Toast.makeText(requireContext(), R.string.config_saved, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveConfig() {
        viewModel.save(
            sheetUrl = binding.sheetUrlInput.text?.toString().orEmpty(),
            webAppUrl = binding.webAppUrlInput.text?.toString().orEmpty(),
            sheetName = binding.sheetNameInput.text?.toString()
                .orEmpty().ifBlank { getString(R.string.default_sheet_name) },
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
