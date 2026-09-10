package com.project.vault.ui.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.project.vault.R
import com.project.vault.databinding.BottomSheetCredentialBinding
import com.project.vault.ui.home.add.CredentialFormData
import com.project.vault.ui.home.showcred.CardShowView
import com.project.vault.ui.home.showcred.LoginShowView
import dagger.hilt.android.AndroidEntryPoint

/**
 * Bottom sheet shown when the user taps a credential card.
 *
 * Flow:
 *  1. Receives [ARG_CREDENTIAL_ID].
 *  2. Calls [HomeViewModel.loadCredentialDetail] to decrypt the Room entity.
 *  3. Injects [CardShowView] or [LoginShowView] into [detailContainer].
 *  4. Provides Edit and Delete actions via header buttons.
 */
@AndroidEntryPoint
class CredentialBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetCredentialBinding? = null
    private val binding get() = _binding!!

    /** Shares the same ViewModel instance as [HomeFragment]. */
    private val viewModel: HomeViewModel by viewModels(
        ownerProducer = { requireParentFragment() }
    )

    private var credentialId: Int = -1
    private var isCredentialSynced: Boolean = false
    private var isCredentialShared: Boolean = false
    private var isCredentialReceived: Boolean = false

    override fun getTheme(): Int = R.style.Theme_Vault_BottomSheet

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        credentialId = arguments?.getInt(ARG_CREDENTIAL_ID) ?: -1
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetCredentialBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val fallbackTitle = arguments?.getString(ARG_TITLE) ?: ""
        binding.tvSheetTitle.text = fallbackTitle

        setupActions()
        observeDetailState()

        if (credentialId != -1) {
            viewModel.loadCredentialDetail(credentialId)
        }
    }

    private fun setupActions() {
        binding.btnEdit.setOnClickListener {
            val id = credentialId
            dismiss()
            AddCredentialBottomSheet
                .newInstance(editCredentialId = id)
                .show(parentFragmentManager, AddCredentialBottomSheet.TAG)
        }

        binding.btnDelete.setOnClickListener {
            onDeleteClicked()
        }
    }

    private fun onDeleteClicked() {
        if (isCredentialShared && !isCredentialReceived) {
            showDeleteSharedPrompt()
        } else {
            showDeleteConfirmation(deleteForSharedUsers = false)
        }
    }

    private fun showDeleteSharedPrompt() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_shared_prompt_title)
            .setMessage(R.string.delete_shared_prompt_message)
            .setPositiveButton(R.string.btn_delete_yes) { _, _ ->
                showDeleteConfirmation(deleteForSharedUsers = true)
            }
            .setNegativeButton(R.string.btn_delete_just_for_me) { _, _ ->
                showDeleteConfirmation(deleteForSharedUsers = false)
            }
            .show()
    }

    private fun showDeleteConfirmation(deleteForSharedUsers: Boolean) {
        val messageRes = when {
            deleteForSharedUsers -> R.string.delete_credential_shared_warning_message
            isCredentialSynced -> R.string.delete_credential_synced_message
            else -> R.string.delete_credential_message
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_credential_title)
            .setMessage(messageRes)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                if (credentialId != -1) {
                    viewModel.deleteCredential(credentialId, deleteForSharedUsers = deleteForSharedUsers)
                }
                dismiss()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun observeDetailState() {
        viewModel.selectedCredentialDetail.observe(viewLifecycleOwner) { state ->
            when (state) {
                is HomeViewModel.DetailState.Idle -> {
                    binding.progressBar.isVisible = false
                }
                is HomeViewModel.DetailState.Loading -> {
                    binding.progressBar.isVisible = true
                    binding.detailContainer.removeAllViews()
                }
                is HomeViewModel.DetailState.Success -> {
                    binding.progressBar.isVisible = false
                    isCredentialSynced = state.isSynced
                    isCredentialShared = state.isShared
                    isCredentialReceived = state.isReceived
                    binding.btnEdit.isVisible = !state.isReceived
                    displayCredentialDetail(state.data)
                }
                is HomeViewModel.DetailState.Error -> {
                    binding.progressBar.isVisible = false
                    Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun displayCredentialDetail(data: CredentialFormData) {
        binding.detailContainer.removeAllViews()
        when (data) {
            is CredentialFormData.CardCredentialData -> {
                binding.tvSheetTitle.text = data.title
                binding.tvSheetType.text = getString(R.string.type_card)
                val cardView = CardShowView(requireContext()).apply {
                    bind(data) { label, text -> copyToClipboard(label, text) }
                }
                binding.detailContainer.addView(cardView)
            }
            is CredentialFormData.LoginCredentialData -> {
                binding.tvSheetTitle.text = data.title
                binding.tvSheetType.text = getString(R.string.type_login)
                val loginView = LoginShowView(requireContext()).apply {
                    bind(data) { label, text -> copyToClipboard(label, text) }
                }
                binding.detailContainer.addView(loginView)
            }
        }
    }

    private fun copyToClipboard(label: String, text: String) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewModel.resetDetailState()
        _binding = null
    }

    companion object {
        const val TAG = "CredentialBottomSheet"
        private const val ARG_CREDENTIAL_ID = "arg_credential_id"
        private const val ARG_TITLE = "arg_title"

        fun newInstance(credentialId: Int, title: String): CredentialBottomSheet {
            return CredentialBottomSheet().apply {
                arguments = Bundle().apply {
                    putInt(ARG_CREDENTIAL_ID, credentialId)
                    putString(ARG_TITLE, title)
                }
            }
        }
    }
}
