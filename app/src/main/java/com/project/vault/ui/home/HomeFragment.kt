package com.project.vault.ui.home

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.project.vault.R
import com.project.vault.databinding.FragmentHomeBinding
import com.project.vault.security.BiometricAuthManager
import com.project.vault.ui.auth.LoginFragment
import com.project.vault.ui.base.BaseFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
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

    private var shareLoadingDialog: androidx.appcompat.app.AlertDialog? = null

    private val adapter by lazy {
        CredentialAdapter(
            onCardClick  = { credential -> onCredentialClicked(credential) },
            onSyncClick  = { credential -> onSyncClicked(credential) },
            onShareClick = { credential -> onShareClicked(credential) }
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
        setupSearchBar()
        observeViewModel()
        setupFab()
        setupHeaderButtons()
    }

    // ── Setup ────────────────────────────────────────────────────────────

    private fun setupRecyclerView() {
        binding.rvCredentials.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@HomeFragment.adapter
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                        hideKeyboard()
                        binding.etSearch.clearFocus()
                    }
                }
            })
        }
    }

    private fun setupSearchBar() {
        binding.layoutSearch.setOnClickListener {
            binding.etSearch.requestFocus()
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(binding.etSearch, InputMethodManager.SHOW_IMPLICIT)
        }

        binding.etSearch.setOnFocusChangeListener { _, hasFocus ->
            binding.layoutSearch.isActivated = hasFocus
        }

        binding.etSearch.doOnTextChanged { text, _, _, _ ->
            val query = text?.toString().orEmpty()
            viewModel.setSearchQuery(query)
            binding.btnClearSearch.isVisible = query.isNotEmpty()
        }

        binding.btnClearSearch.setOnClickListener {
            binding.etSearch.text?.clear()
            hideKeyboard()
            binding.etSearch.clearFocus()
        }

        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                hideKeyboard()
                binding.etSearch.clearFocus()
                true
            } else {
                false
            }
        }
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        val view = activity?.currentFocus ?: binding.etSearch
        imm?.hideSoftInputFromWindow(view.windowToken, 0)
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

        viewModel.totalCredentialsCount.observe(viewLifecycleOwner) { totalCount ->
            binding.tvEntryCount.text =
                if (totalCount == 0) getString(R.string.home_no_entries)
                else getString(R.string.home_entry_count_placeholder, totalCount)

            val hasEntries = totalCount > 0
            binding.layoutSearch.alpha = if (hasEntries) 1.0f else 0.5f
            binding.etSearch.isEnabled = hasEntries
            binding.btnClearSearch.isEnabled = hasEntries
        }

        viewModel.credentials.observe(viewLifecycleOwner) { credentials ->
            adapter.submitList(credentials)

            val isEmpty = credentials.isEmpty()
            val totalCount = viewModel.totalCredentialsCount.value ?: 0
            val isSearching = binding.etSearch.text?.isNotBlank() == true

            binding.rvCredentials.isVisible = !isEmpty
            binding.layoutEmpty.isVisible    = isEmpty

            if (isEmpty) {
                if (isSearching && totalCount > 0) {
                    binding.ivEmptyIcon.setImageResource(R.drawable.ic_search)
                    binding.tvEmptyTitle.text = getString(R.string.search_empty_title)
                    binding.tvEmptySubtitle.text = getString(R.string.search_empty_subtitle)
                } else {
                    binding.ivEmptyIcon.setImageResource(R.drawable.ic_offline)
                    binding.tvEmptyTitle.text = getString(R.string.home_empty_title)
                    binding.tvEmptySubtitle.text = getString(R.string.home_empty_subtitle)
                }
            }
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
                    val message = when {
                        result.failed > 0 -> getString(
                            R.string.refresh_success_with_failures,
                            result.resynced,
                            result.newImported,
                            result.failed
                        )
                        result.updatedShared > 0 -> getString(
                            R.string.refresh_success_with_shared,
                            result.resynced,
                            result.updatedShared,
                            result.newImported
                        )
                        else -> getString(
                            R.string.refresh_success,
                            result.resynced,
                            result.newImported
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
                is HomeViewModel.RefreshResultState.Idle -> Unit
            }
        }

        // Observe individual received credential refresh results
        viewModel.refreshReceivedState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is HomeViewModel.RefreshReceivedState.Updated -> {
                    Snackbar.make(
                        binding.root,
                        getString(R.string.refresh_received_success, state.title),
                        Snackbar.LENGTH_SHORT
                    ).show()
                    viewModel.resetRefreshReceivedState()
                }
                is HomeViewModel.RefreshReceivedState.Revoked -> {
                    Snackbar.make(
                        binding.root,
                        getString(R.string.refresh_received_revoked, state.title),
                        Snackbar.LENGTH_LONG
                    ).show()
                    viewModel.resetRefreshReceivedState()
                }
                is HomeViewModel.RefreshReceivedState.Error -> {
                    Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                    viewModel.resetRefreshReceivedState()
                }
                is HomeViewModel.RefreshReceivedState.Idle -> Unit
            }
        }

        // Observe delete operation loading state and results
        viewModel.deleteState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is HomeViewModel.DeleteState.Idle -> {
                    binding.layoutDeleteLoading.isVisible = false
                }
                is HomeViewModel.DeleteState.Deleting -> {
                    binding.layoutDeleteLoading.isVisible = true
                }
                is HomeViewModel.DeleteState.Success -> {
                    binding.layoutDeleteLoading.isVisible = false
                    Snackbar.make(binding.root, R.string.delete_credential_success, Snackbar.LENGTH_SHORT).show()
                    viewModel.resetDeleteState()
                }
                is HomeViewModel.DeleteState.Error -> {
                    binding.layoutDeleteLoading.isVisible = false
                    Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                    viewModel.resetDeleteState()
                }
            }
        }

        // Observe credential sharing operation state
        viewModel.shareExecutionState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is HomeViewModel.ShareExecutionState.Idle -> {
                    shareLoadingDialog?.dismiss()
                    shareLoadingDialog = null
                }
                is HomeViewModel.ShareExecutionState.Loading -> {
                    if (shareLoadingDialog == null) {
                        shareLoadingDialog = MaterialAlertDialogBuilder(requireContext())
                            .setView(R.layout.dialog_loading_share)
                            .setCancelable(false)
                            .show()
                    }
                }
                is HomeViewModel.ShareExecutionState.Success -> {
                    shareLoadingDialog?.dismiss()
                    shareLoadingDialog = null
                    Snackbar.make(
                        binding.root,
                        getString(R.string.share_success, state.credentialTitle, state.recipientUsername),
                        Snackbar.LENGTH_LONG
                    ).show()
                    viewModel.resetShareExecutionState()
                }
                is HomeViewModel.ShareExecutionState.Error -> {
                    shareLoadingDialog?.dismiss()
                    shareLoadingDialog = null
                    Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                    viewModel.resetShareExecutionState()
                }
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
        val promptSubtitle = if (credential.isReceived) {
            getString(R.string.biometric_refresh_received_subtitle, credential.title)
        } else {
            getString(R.string.biometric_sync_subtitle, credential.title)
        }

        when (biometricAuthManager.canAuthenticate(requireContext())) {
            BiometricAuthManager.BiometricStatus.Ready -> {
                biometricAuthManager.authenticate(
                    fragment  = this,
                    title     = getString(R.string.biometric_prompt_title),
                    subtitle  = promptSubtitle,
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

    // ── Share Flow ───────────────────────────────────────────────────────

    private fun onShareClicked(credential: Credential) {
        if (viewModel.isLoggedIn.value != true) {
            Snackbar.make(binding.root, R.string.share_login_required, Snackbar.LENGTH_LONG).show()
            return
        }
        showShareInputDialog(credential)
    }

    private fun showShareInputDialog(credential: Credential, prefillUsername: String = "") {
        val dialogBinding = com.project.vault.databinding.DialogShareCredentialBinding.inflate(layoutInflater)
        dialogBinding.tvShareSubtitle.text = getString(R.string.share_credential_subtitle) + " for \"${credential.title}\""
        if (prefillUsername.isNotEmpty()) {
            dialogBinding.etShareUsername.setText(prefillUsername)
            dialogBinding.etShareUsername.setSelection(prefillUsername.length)
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .setCancelable(true)
            .create()

        dialogBinding.btnCancelShare.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnSendShare.setOnClickListener {
            val rawUsername = dialogBinding.etShareUsername.text?.toString()?.trim().orEmpty()
            if (rawUsername.isBlank()) {
                dialogBinding.inputLayoutShareUsername.error = getString(R.string.error_share_empty_username)
                return@setOnClickListener
            }
            val currentUsername = viewModel.currentUsername.value
            if (!currentUsername.isNullOrBlank() && rawUsername.equals(currentUsername, ignoreCase = true)) {
                dialogBinding.inputLayoutShareUsername.error = getString(R.string.error_share_self)
                return@setOnClickListener
            }
            dialogBinding.inputLayoutShareUsername.error = null

            // Disable send button and show progress spinner inside button replacing send text
            dialogBinding.btnSendShare.isEnabled = false
            dialogBinding.btnSendShare.text = ""
            dialogBinding.progressShareSend.visibility = View.VISIBLE
            dialogBinding.btnCancelShare.isEnabled = false
            dialogBinding.etShareUsername.isEnabled = false

            viewLifecycleOwner.lifecycleScope.launch {
                val result = viewModel.fetchDevicesForUser(rawUsername)
                if (!isAdded) return@launch

                val devices = result.getOrNull()
                if (devices.isNullOrEmpty()) {
                    dialogBinding.btnSendShare.isEnabled = true
                    dialogBinding.btnSendShare.text = getString(R.string.btn_send)
                    dialogBinding.progressShareSend.visibility = View.GONE
                    dialogBinding.btnCancelShare.isEnabled = true
                    dialogBinding.etShareUsername.isEnabled = true
                    dialogBinding.inputLayoutShareUsername.error = getString(R.string.error_share_user_not_found)
                } else {
                    dialog.dismiss()
                    showShareConfirmationDialog(credential, rawUsername, devices)
                }
            }
        }

        dialog.show()
    }

    private fun showShareConfirmationDialog(
        credential: Credential,
        recipientUsername: String,
        devices: List<com.project.vault.api.dto.DeviceDto>
    ) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.share_confirm_title)
            .setMessage(getString(R.string.share_confirm_message, credential.title, recipientUsername))
            .setNegativeButton(R.string.btn_cancel) { _, _ ->
                showShareInputDialog(credential, prefillUsername = recipientUsername)
            }
            .setPositiveButton(R.string.btn_confirm) { _, _ ->
                authenticateAndExecuteShare(credential, recipientUsername, devices)
            }
            .setOnCancelListener {
                showShareInputDialog(credential, prefillUsername = recipientUsername)
            }
            .show()
    }

    private fun authenticateAndExecuteShare(
        credential: Credential,
        recipientUsername: String,
        devices: List<com.project.vault.api.dto.DeviceDto>
    ) {
        when (biometricAuthManager.canAuthenticate(requireContext())) {
            BiometricAuthManager.BiometricStatus.Ready -> {
                biometricAuthManager.authenticate(
                    fragment = this,
                    title = getString(R.string.biometric_prompt_title),
                    subtitle = getString(R.string.biometric_share_subtitle, credential.title),
                    onSuccess = {
                        viewModel.executeShare(credential.id, credential.title, recipientUsername, devices)
                    },
                    onError = { errorMsg ->
                        showToast(errorMsg)
                        showShareInputDialog(credential, prefillUsername = recipientUsername)
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

    override fun onDestroyView() {
        shareLoadingDialog?.dismiss()
        shareLoadingDialog = null
        super.onDestroyView()
    }
}
