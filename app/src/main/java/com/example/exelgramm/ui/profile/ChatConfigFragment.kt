package com.example.exelgramm.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.exelgramm.R
import com.example.exelgramm.databinding.FragmentChatConfigBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ChatConfigFragment : Fragment() {

    private var _binding: FragmentChatConfigBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ChatConfigViewModel by viewModels()

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

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (binding.sheetUrlInput.text.isNullOrEmpty() && state.sheetUrl.isNotEmpty()) {
                        binding.sheetUrlInput.setText(state.sheetUrl)
                    }
                    if (binding.webAppUrlInput.text.isNullOrEmpty() && state.webAppUrl.isNotEmpty()) {
                        binding.webAppUrlInput.setText(state.webAppUrl)
                    }
                    if (binding.sheetNameInput.text.isNullOrEmpty()) {
                        binding.sheetNameInput.setText(state.sheetName)
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.effects.collect { effect ->
                    when (effect) {
                        is ChatConfigEffect.ShowError ->
                            Toast.makeText(requireContext(), effect.resId, Toast.LENGTH_LONG).show()
                        is ChatConfigEffect.ShowSaved ->
                            Toast.makeText(requireContext(), R.string.config_saved, Toast.LENGTH_SHORT).show()
                        is ChatConfigEffect.NavigateBack ->
                            findNavController().popBackStack()
                    }
                }
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
