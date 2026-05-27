package com.example.exelgramm.ui.profile

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.exelgramm.ExelgrammApp
import com.example.exelgramm.R
import com.example.exelgramm.databinding.FragmentProfileBinding
import com.example.exelgramm.ui.auth.LoginActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val store get() = (requireActivity().application as ExelgrammApp).sessionStore

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
        loadUsername()
        binding.configureChatItem.setOnClickListener {
            findNavController().navigate(R.id.action_nav_profile_to_chatConfig)
        }
        binding.logoutButton.setOnClickListener { logout() }
    }

    private fun loadUsername() {
        viewLifecycleOwner.lifecycleScope.launch {
            binding.usernameValue.text = store.session.first().username
        }
    }

    private fun logout() {
        viewLifecycleOwner.lifecycleScope.launch {
            store.logout()
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
