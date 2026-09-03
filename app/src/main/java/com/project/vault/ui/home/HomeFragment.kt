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
import com.google.android.material.snackbar.Snackbar
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
 * - [btnRefresh] triggers biometric auth, then runs the global refresh pipeline:
 *   re-syncs existing synced credentials and pulls new ones from the server.
 */
@AndroidEntryPoint
class HomeFragment : BaseFragment<FragmentHomeBinding>() {

    @Inject
    lateinit var biometricAuthManager: BiometricAuthManager

    private val viewModel: HomeViewModel by viewModels()

    private val adapter by lazy {
        CredentialAdapter(
            onCardClick  = { credential -> onCredentialClicked(credential) },
            onSyncClick  = { credential -> onSyncClicked(credential) },
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
        // Navigate to the LoginFragment
        binding.btnLogin.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_loginFragment)
        }

        // Clicking profile opens a popup menu with Logout option
        binding.btnProfile.setOnClickListener { anchorView ->
            showProfilePopupMenu(anchorView)
        }

        // Refresh button — gate behind biometric auth, then run global sync
        binding.btnRefresh.setOnClickListener {
            onRefreshClicked()
        }
    }

    private fun showProfilePopupMenu(anchorView: View) {
        val popup = androidx.appcompat.widget.PopupMenu(requireContext(), anchorView)
        val username = viewModel.currentUsername.value
        if (!username.isNullOrBlank()) {
            popup.menu.add(0, 1, 0, username).apply {
                isEnabled = false
            }
        }
        popup.menu.add(0, 2, 1, getString(R.string.action_logout))

        popup.setOnMenuItemClickListener { menuItem ->
            if (menuItem.itemId == 2) {
                showLogoutConfirmationDialog()
                true
            } else {
                false
            }
        }
        popup.show()
    }

    private fun showLogoutConfirmationDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.logout_confirmation_title)
            .setMessage(R.string.logout_confirmation_message)
            .setPositiveButton(R.string.action_logout) { _, _ ->
                viewModel.logout()
                showToast(getString(R.string.logged_out_success))
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun updateLoginUiState(isLoggedIn: Boolean) {
        binding.btnLogin.visibility        = if (isLoggedIn) View.GONE else View.VISIBLE
        binding.btnProfile.visibility      = if (isLoggedIn) View.VISIBLE else View.GONE
        binding.btnRefresh.visibility      = if (isLoggedIn) View.VISIBLE else View.GONE
        if (!isLoggedIn) {
            binding.progressRefresh.visibility = View.GONE
        }
    }

    // ── Observation ──────────────────────────────────────────────────────

    private fun observeViewModel() {
        viewModel.isLoggedIn.observe(viewLifecycleOwner) { isLoggedIn ->
            updateLoginUiState(isLoggedIn)
        }
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

        // Sync errors are shown as a Snackbar; successes are reflected automatically
        // via Room's reactive Flow flipping the credential chip OFFLINE → SYNCED.
        viewModel.syncState.observe(viewLifecycleOwner) { state ->
            if (state is HomeViewModel.SyncState.Error) {
                Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                viewModel.resetSyncState()
            }
        }

        // Drive the spinner inside btnRefresh while global refresh is running.
        viewModel.isRefreshing.observe(viewLifecycleOwner) { isRefreshing ->
            binding.btnRefresh.isEnabled = !isRefreshing
            binding.btnRefresh.icon = if (isRefreshing) null else
                androidx.core.content.ContextCompat.getDrawable(requireContext(), R.drawable.ic_sync)
            binding.progressRefresh.isVisible = isRefreshing
        }

        // Show a Snackbar with the global refresh summary.
        viewModel.refreshResultState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is HomeViewModel.RefreshResultState.Success -> {
                    val result = state.result
                    val message = if (result.failed == 0) {
                        getString(
                            R.string.refresh_success,
                            result.resynced,
                            result.newImported
                        )
                    } else {
                        getString(
                            R.string.refresh_success_with_failures,
                            result.resynced,
                            result.newImported,
                            result.failed
                        )
                    }
                    Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
                    viewModel.resetRefreshResultState()
                }
                is HomeViewModel.RefreshResultState.Error -> {
                    Snackbar.make(
                        binding.root,
                        getString(R.string.refresh_error, state.message),
                        Snackbar.LENGTH_LONG
                    ).show()
                    viewModel.resetRefreshResultState()
                }
                else -> { /* Idle — no action */ }
            }
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

    /**
     * Gates the sync action behind biometric / device-credential authentication.
     * On success, delegates to [HomeViewModel.onSyncClicked] which runs the full
     * sync pipeline: decrypt → fetch devices → re-encrypt per device → POST /sync →
     * update Room.
     */
    private fun onSyncClicked(credential: Credential) {
        when (biometricAuthManager.canAuthenticate(requireContext())) {
            BiometricAuthManager.BiometricStatus.Ready -> {
                biometricAuthManager.authenticate(
                    fragment = this,
                    title = getString(R.string.biometric_prompt_title),
                    subtitle = getString(
                        R.string.biometric_sync_subtitle,
                        credential.title
                    ),
                    onSuccess = { viewModel.onSyncClicked(credential.id) },
                    onError   = { errorMsg -> showToast(errorMsg) }
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

    /**
     * Gates the global refresh behind biometric auth (once for the whole batch).
     * On success, delegates to [HomeViewModel.onGlobalRefreshClicked].
     */
    private fun onRefreshClicked() {
        when (biometricAuthManager.canAuthenticate(requireContext())) {
            BiometricAuthManager.BiometricStatus.Ready -> {
                biometricAuthManager.authenticate(
                    fragment  = this,
                    title     = getString(R.string.biometric_prompt_title),
                    subtitle  = getString(R.string.biometric_refresh_subtitle),
                    onSuccess = { viewModel.onGlobalRefreshClicked() },
                    onError   = { errorMsg -> showToast(errorMsg) }
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
}
