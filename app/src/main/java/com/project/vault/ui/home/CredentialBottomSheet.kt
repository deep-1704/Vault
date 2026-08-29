package com.project.vault.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.project.vault.R
import com.project.vault.databinding.BottomSheetCredentialBinding

/**
 * Stub bottom sheet shown when the user taps a credential card.
 *
 * Displays the entry title and a placeholder body.
 * Replace the placeholder layout with full credential detail UI later.
 */
class CredentialBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetCredentialBinding? = null
    private val binding get() = _binding!!

    override fun getTheme(): Int = R.style.Theme_Vault_BottomSheet

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
        binding.tvSheetTitle.text = arguments?.getString(ARG_TITLE) ?: ""
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "CredentialBottomSheet"
        private const val ARG_TITLE = "arg_title"

        fun newInstance(title: String): CredentialBottomSheet {
            return CredentialBottomSheet().apply {
                arguments = Bundle().apply { putString(ARG_TITLE, title) }
            }
        }
    }
}
