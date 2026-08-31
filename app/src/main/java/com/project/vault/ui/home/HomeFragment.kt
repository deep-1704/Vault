package com.project.vault.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.navigation.fragment.findNavController
import com.project.vault.R
import com.project.vault.databinding.FragmentHomeBinding
import com.project.vault.ui.auth.LoginFragment
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
        setupHeaderButtons()
        observeNavigationResults()
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
            AddCredentialBottomSheet()
                .show(childFragmentManager, AddCredentialBottomSheet.TAG)
        }
    }


    private fun setupHeaderButtons() {
        // Navigate to the new LoginFragment
        binding.btnLogin.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_loginFragment)
        }

        // Clicking profile resets the login state in SavedStateHandle and local UI
        binding.btnProfile.setOnClickListener {
            findNavController().currentBackStackEntry?.savedStateHandle?.set(
                LoginFragment.KEY_IS_LOGGED_IN,
                false
            )
            updateLoginUiState(isLoggedIn = false)
            showToast("Logged out (Mock)")
        }
    }

    private fun observeNavigationResults() {
        // Observe login state results passed back from LoginFragment
        findNavController().currentBackStackEntry?.savedStateHandle
            ?.getLiveData<Boolean>(LoginFragment.KEY_IS_LOGGED_IN)
            ?.observe(viewLifecycleOwner) { isLoggedIn ->
                updateLoginUiState(isLoggedIn)
            }
    }

    private fun updateLoginUiState(isLoggedIn: Boolean) {
        binding.btnLogin.visibility = if (isLoggedIn) View.GONE else View.VISIBLE
        binding.btnProfile.visibility = if (isLoggedIn) View.VISIBLE else View.GONE
    }

    // ── Observation ──────────────────────────────────────────────────────

    private fun observeViewModel() {
        viewModel.credentials.observe(viewLifecycleOwner) { credentials ->
            adapter.submitList(credentials)

            val isEmpty = credentials.isEmpty()
            binding.rvCredentials.isVisible = !isEmpty
            binding.layoutEmpty.isVisible    = isEmpty
            binding.tvEntryCount.text =
                if (isEmpty) getString(R.string.home_no_entries)
                else getString(R.string.home_entry_count_placeholder, credentials.size)
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
