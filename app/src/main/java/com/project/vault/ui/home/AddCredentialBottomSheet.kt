package com.project.vault.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.project.vault.R
import com.project.vault.databinding.BottomSheetAddCredentialBinding
import com.project.vault.ui.home.add.CardFormView
import com.project.vault.ui.home.add.CredentialFormData
import com.project.vault.ui.home.add.LoginFormView
import dagger.hilt.android.AndroidEntryPoint

/**
 * Bottom sheet shown when the user taps the FAB (Add Mode) or Edit in CredentialBottomSheet (Edit Mode).
 *
 * Flow:
 *  1. In Add Mode, user picks a credential type from the Material Exposed Dropdown.
 *     In Edit Mode, type is pre-selected and locked, and the form is populated with existing values.
 *  2. The matching form view ([CardFormView] or [LoginFormView]) is swapped into [formContainer].
 *  3. On Save:
 *     - The active form is validated (non-empty checks).
 *     - The Save button is disabled and shows "Saving…".
 *     - In Add Mode: [HomeViewModel.addCredential] encrypts and inserts into Room.
 *     - In Edit Mode: [HomeViewModel.updateCredential] encrypts and updates Room.
 *     - On success the sheet auto-dismisses; on failure a toast is shown and the button is re-enabled.
 */
@AndroidEntryPoint
class AddCredentialBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetAddCredentialBinding? = null
    private val binding get() = _binding!!

    /** Shares the same ViewModel instance as [HomeFragment]. */
    private val viewModel: HomeViewModel by viewModels(
        ownerProducer = { requireParentFragment() }
    )

    /** Currently active form view — null until the user picks a type. */
    private var activeFormView: View? = null

    private var editCredentialId: Int = -1
    private val isEditMode get() = editCredentialId != -1

    override fun getTheme(): Int = R.style.Theme_Vault_BottomSheet

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        editCredentialId = arguments?.getInt(ARG_EDIT_CREDENTIAL_ID, -1) ?: -1
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetAddCredentialBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.resetSaveState()
        setupTypeDropdown()
        setupSaveButton()
        observeSaveState()

        if (isEditMode) {
            setupEditMode()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ── Setup ──────────────────────────────────────────────────────────────────

    private fun setupTypeDropdown() {
        val types = listOf(
            getString(R.string.type_card),
            getString(R.string.type_login)
        )
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, types)
        binding.actvCredentialType.setAdapter(adapter)

        binding.actvCredentialType.setOnItemClickListener { _, _, position, _ ->
            swapFormView(if (position == 0) CredentialType.CARD else CredentialType.LOGIN)
        }
    }

    private fun setupEditMode() {
        binding.tvSheetTitle.text = getString(R.string.edit_credential_title)
        binding.tilCredentialType.isEnabled = false

        viewModel.loadCredentialDetail(editCredentialId)
        viewModel.selectedCredentialDetail.observe(viewLifecycleOwner) { state ->
            if (state is HomeViewModel.DetailState.Success && state.id == editCredentialId) {
                when (val data = state.data) {
                    is CredentialFormData.CardCredentialData -> {
                        binding.actvCredentialType.setText(getString(R.string.type_card), false)
                        swapFormView(CredentialType.CARD)
                        (activeFormView as? CardFormView)?.populate(data)
                    }
                    is CredentialFormData.LoginCredentialData -> {
                        binding.actvCredentialType.setText(getString(R.string.type_login), false)
                        swapFormView(CredentialType.LOGIN)
                        (activeFormView as? LoginFormView)?.populate(data)
                    }
                }

                if (state.isSynced) {
                    binding.btnSaveAndSync.isVisible = false
                    binding.btnSave.isVisible = true
                    binding.btnSave.backgroundTintList =
                        androidx.core.content.ContextCompat.getColorStateList(requireContext(), R.color.vault_action_primary)
                    binding.btnSave.setTextColor(
                        androidx.core.content.ContextCompat.getColor(requireContext(), R.color.vault_on_primary_container)
                    )
                    binding.btnSave.strokeWidth = 0
                } else {
                    binding.btnSaveAndSync.isVisible = true
                    binding.btnSave.isVisible = true
                }
            }
        }
    }

    private fun extractFormData(): CredentialFormData? {
        val form = activeFormView
        if (form == null) {
            binding.tilCredentialType.error = getString(R.string.error_field_required)
            return null
        }
        binding.tilCredentialType.error = null

        return when (form) {
            is CardFormView  -> if (form.validate()) form.getFormData() else null
            is LoginFormView -> if (form.validate()) form.getFormData() else null
            else             -> null
        }
    }

    private fun setupSaveButton() {
        binding.btnSaveAndSync.setOnClickListener {
            val formData = extractFormData() ?: return@setOnClickListener
            viewModel.saveAndSyncCredential(
                data = formData,
                editId = if (isEditMode) editCredentialId else null
            )
        }

        binding.btnSave.setOnClickListener {
            val formData = extractFormData() ?: return@setOnClickListener
            if (isEditMode) {
                viewModel.updateCredential(editCredentialId, formData)
            } else {
                viewModel.addCredential(formData)
            }
        }
    }

    // ── SaveState observation ──────────────────────────────────────────────────

    private fun observeSaveState() {
        viewModel.saveState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is HomeViewModel.SaveState.Idle -> {
                    binding.btnSave.isEnabled = true
                    binding.btnSaveAndSync.isEnabled = true
                    binding.btnSave.text = getString(R.string.btn_save)
                    binding.btnSaveAndSync.text = getString(R.string.btn_save_sync)
                }
                is HomeViewModel.SaveState.Saving -> {
                    binding.btnSave.isEnabled = false
                    binding.btnSaveAndSync.isEnabled = false
                    binding.btnSave.text = getString(R.string.btn_saving)
                    binding.btnSaveAndSync.text = getString(R.string.btn_saving)
                }
                is HomeViewModel.SaveState.Success -> dismiss()
                is HomeViewModel.SaveState.Error -> {
                    binding.btnSave.isEnabled = true
                    binding.btnSaveAndSync.isEnabled = true
                    binding.btnSave.text = getString(R.string.btn_save)
                    binding.btnSaveAndSync.text = getString(R.string.btn_save_sync)
                    Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ── Form swap ──────────────────────────────────────────────────────────────

    private fun swapFormView(type: CredentialType) {
        binding.formContainer.removeAllViews()
        val newForm: View = when (type) {
            CredentialType.CARD  -> CardFormView(requireContext())
            CredentialType.LOGIN -> LoginFormView(requireContext())
        }
        binding.formContainer.addView(newForm)
        activeFormView = newForm

        if (!isEditMode) {
            binding.btnSaveAndSync.isVisible = true
            binding.btnSave.isVisible = true
        }
    }

    // ── Companion ──────────────────────────────────────────────────────────────

    companion object {
        const val TAG = "AddCredentialBottomSheet"
        private const val ARG_EDIT_CREDENTIAL_ID = "arg_edit_credential_id"

        fun newInstance(editCredentialId: Int = -1): AddCredentialBottomSheet {
            return AddCredentialBottomSheet().apply {
                arguments = Bundle().apply {
                    putInt(ARG_EDIT_CREDENTIAL_ID, editCredentialId)
                }
            }
        }
    }
}
