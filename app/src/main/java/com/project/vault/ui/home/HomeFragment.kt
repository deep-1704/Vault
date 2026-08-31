package com.project.vault.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.project.vault.R
import com.project.vault.databinding.FragmentHomeBinding
import com.project.vault.security.BiometricAuthManager
import com.project.vault.ui.auth.LoginFragment
import com.project.vault.ui.base.BaseFragment
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Main screen — shows the list of credential entries.
 *
 * - Observes [HomeViewModel.credentials] to populate / toggle the empty state.
 * - Observes [HomeViewModel.buttonLoadingEvent] to update individual button
 *   loading states without triggering a full list rebind.
 * - FAB navigates to the Add Credential bottom sheet.
 * - Tapping a card authenticates via [BiometricAuthManager] before opening [CredentialBottomSheet].
 */
@AndroidEntryPoint
class HomeFragment : BaseFragment<FragmentHomeBinding>() {

    @Inject
    lateinit var biometricAuthManager: BiometricAuthManager

    private val viewModel: HomeViewModel by viewModels()

    private val adapter by lazy {
        CredentialAdapter(
            onCardClick  = { credential -> onCredentialClicked(credential) },
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

    // ── Biometric & Bottom Sheet ─────────────────────────────────────────

    private fun onCredentialClicked(credential: Credential) {
        when (biometricAuthManager.canAuthenticate(requireContext())) {
            BiometricAuthManager.BiometricStatus.Ready -> {
                biometricAuthManager.authenticate(
                    fragment = this,
                    title = getString(R.string.biometric_prompt_title),
                    subtitle = getString(R.string.biometric_prompt_subtitle),
                    onSuccess = {
                        showBottomSheet(credential)
                    },
                    onError = { errorMsg ->
                        showToast(errorMsg)
                    }
                )
            }
            BiometricAuthManager.BiometricStatus.NoneEnrolled -> {
                showUnenrolledDialog()
            }
            BiometricAuthManager.BiometricStatus.HardwareUnavailable,
            BiometricAuthManager.BiometricStatus.Unsupported -> {
                showToast(getString(R.string.biometric_not_available))
            }
        }
    }

    private fun showUnenrolledDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.biometric_unenrolled_title)
            .setMessage(R.string.biometric_unenrolled_message)
            .setPositiveButton(R.string.btn_open_settings) { _, _ ->
                biometricAuthManager.openEnrollmentSettings(requireContext())
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun showBottomSheet(credential: Credential) {
        CredentialBottomSheet
            .newInstance(credential.id, credential.title)
            .show(childFragmentManager, CredentialBottomSheet.TAG)
    }
}
