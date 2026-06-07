package com.example.exelgramm.ui.participants

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.exelgramm.R
import com.example.exelgramm.databinding.FragmentParticipantsBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ParticipantsFragment : Fragment() {

    private var _binding: FragmentParticipantsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ParticipantsViewModel by viewModels()
    private lateinit var adapter: ParticipantsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentParticipantsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = ParticipantsAdapter { item ->
            findNavController().navigate(
                R.id.action_participants_to_detail,
                bundleOf("authorName" to item.author),
            )
        }
        binding.participantsList.layoutManager = LinearLayoutManager(requireContext())
        binding.participantsList.adapter = adapter
        binding.participantsList.addItemDecoration(
            DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL),
        )

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.participants.collect { items ->
                    adapter.submitList(items)
                    val isEmpty = items.isEmpty()
                    binding.participantsList.isVisible = !isEmpty
                    val isConfigured = viewModel.isConfigured.value
                    binding.emptyText.isVisible = isEmpty && isConfigured
                    binding.notConfiguredText.isVisible = !isConfigured
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isConfigured.collect { configured ->
                    binding.tableHeader.isVisible = configured
                    binding.notConfiguredText.isVisible = !configured
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
