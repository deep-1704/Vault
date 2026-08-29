package com.project.vault.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.project.vault.databinding.FragmentHomeBinding
import com.project.vault.ui.base.BaseFragment
import dagger.hilt.android.AndroidEntryPoint

/**
 * Main screen — shows the list of credential entries.
 *
 * - Observes [HomeViewModel.credentials] to populate / toggle the empty state.
 * - Observes [HomeViewModel.buttonLoadingEvent] to update individual button
 *   loading states without triggering a full list rebind.
 * - FAB navigates to the Add Credential screen (wired later via NavController).
 * - Tapping a card opens [CredentialBottomSheet].
 */
@AndroidEntryPoint
class HomeFragment : BaseFragment<FragmentHomeBinding>() {

    private val viewModel: HomeViewModel by viewModels()

    private val adapter by lazy {
        CredentialAdapter(
            onCardClick  = { credential -> showBottomSheet(credential) },
            onSyncClick  = { credential -> viewModel.onSyncClicked(credential.id) },
            onShareClick = { credential -> viewModel.onShareClicked(credential.id) }
        )
    }

    // ── BaseFragment ─────────────────────────────────────────────────────

    override fun getViewBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentHomeBinding = FragmentHomeBinding.inflate(inflater, container, false)

    // ── Lifecycle ────────────────────────────────────────────────────────

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeErrors(viewModel)
        setupRecyclerView()
        observeViewModel()
        setupFab()
    }

    // ── Setup ────────────────────────────────────────────────────────────

    private fun setupRecyclerView() {
        binding.rvCredentials.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@HomeFragment.adapter
        }
    }

    private fun setupFab() {
        binding.fab.setOnClickListener {
            // TODO: navigate to Add Credential screen
            // findNavController().navigate(R.id.action_homeFragment_to_addCredentialFragment)
            showToast("Add credential — coming soon")
        }
    }

    // ── Observation ──────────────────────────────────────────────────────

    private fun observeViewModel() {
        viewModel.credentials.observe(viewLifecycleOwner) { credentials ->
            adapter.submitList(credentials)

            val isEmpty = credentials.isEmpty()
            binding.rvCredentials.isVisible = !isEmpty
            binding.layoutEmpty.isVisible    = isEmpty
            binding.tvEntryCount.text =
                if (isEmpty) "No entries"
                else "${credentials.size} Secure Entries"
        }

        viewModel.buttonLoadingEvent.observe(viewLifecycleOwner) { event ->
            adapter.setButtonLoading(event.credentialId, event.buttonType, event.isLoading)
        }
    }

    // ── Bottom Sheet ─────────────────────────────────────────────────────

    private fun showBottomSheet(credential: Credential) {
        CredentialBottomSheet
            .newInstance(credential.title)
            .show(childFragmentManager, CredentialBottomSheet.TAG)
    }
}
