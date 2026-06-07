package com.example.exelgramm.ui.participants

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.exelgramm.R
import com.example.exelgramm.databinding.FragmentParticipantsBinding
import com.example.exelgramm.ui.common.collectOnStarted
import dagger.hilt.android.AndroidEntryPoint

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

        binding.participantsRefresh.setOnRefreshListener {
            viewModel.refresh(syncFromRemote = true, showLoading = true)
        }

        collectOnStarted(viewModel.participants) { items ->
            adapter.submitList(items)
            val isEmpty = items.isEmpty()
            binding.participantsList.isVisible = !isEmpty
            val isConfigured = viewModel.isConfigured.value
            binding.emptyText.isVisible = isEmpty && isConfigured
            binding.notConfiguredText.isVisible = !isConfigured
        }

        collectOnStarted(viewModel.isConfigured) { configured ->
            binding.tableHeader.isVisible = configured
            binding.notConfiguredText.isVisible = !configured
        }

        collectOnStarted(viewModel.isLoading) { loading ->
            binding.participantsRefresh.isRefreshing = loading && viewModel.isConfigured.value
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh(syncFromRemote = true)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
