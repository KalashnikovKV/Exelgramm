package com.example.exelgramm.ui.profile

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.exelgramm.ExelgrammApp
import com.example.exelgramm.R
import com.example.exelgramm.data.remote.SheetLinkParser
import com.example.exelgramm.databinding.FragmentProfileBinding
import com.example.exelgramm.ui.auth.LoginActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadSession()

        binding.saveProfileButton.setOnClickListener { saveProfile() }
        binding.logoutButton.setOnClickListener { logout() }
    }

    private fun loadSession() {
        viewLifecycleOwner.lifecycleScope.launch {
            val session = (requireActivity().application as ExelgrammApp)
                .sessionStore.session.first()
            binding.displayNameInput.setText(session.displayName)
            binding.profileSheetUrlInput.setText(session.sheetUrl)
            binding.profileWebAppUrlInput.setText(session.webAppUrl)
            binding.profileSheetNameInput.setText(session.sheetName)
        }
    }

    private fun saveProfile() {
        val name = binding.displayNameInput.text?.toString().orEmpty().trim()
        if (name.isBlank()) {
            Toast.makeText(requireContext(), R.string.error_name_required, Toast.LENGTH_SHORT).show()
            return
        }

        val sheetUrl = binding.profileSheetUrlInput.text?.toString().orEmpty()
        val webAppUrl = binding.profileWebAppUrlInput.text?.toString().orEmpty()
        val sheetName = binding.profileSheetNameInput.text?.toString().orEmpty().ifBlank { "Лист1" }

        viewLifecycleOwner.lifecycleScope.launch {
            val store = (requireActivity().application as ExelgrammApp).sessionStore
            store.saveDisplayName(name)
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
            Toast.makeText(requireContext(), R.string.profile_saved, Toast.LENGTH_SHORT).show()
        }
    }

    private fun logout() {
        viewLifecycleOwner.lifecycleScope.launch {
            (requireActivity().application as ExelgrammApp).sessionStore.clear()
            startActivity(Intent(requireContext(), LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            requireActivity().finish()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
