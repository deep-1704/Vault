package com.project.vault.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import com.project.vault.databinding.FragmentLoginBinding
import com.project.vault.ui.base.BaseFragment
import dagger.hilt.android.AndroidEntryPoint

/**
 * Authentication screen providing login and sign-up form controls.
 *
 * Scoped under the `com.project.vault.ui.auth` package outside home.
 * Communicates successful authentication state back to the caller
 * destination via the Navigation controller's SavedStateHandle.
 */
@AndroidEntryPoint
class LoginFragment : BaseFragment<FragmentLoginBinding>() {

    override fun getViewBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentLoginBinding = FragmentLoginBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Login submission action
        binding.btnSubmitLogin.setOnClickListener {
            val username = binding.etUsername.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            if (username.isEmpty() || password.isEmpty()) {
                showToast("Please fill in all fields")
                return@setOnClickListener
            }

            showToast("Logged in successfully (Mock)")
            completeAuthentication()
        }

        // Account creation action
        binding.btnCreateAccount.setOnClickListener {
            val username = binding.etUsername.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            if (username.isEmpty() || password.isEmpty()) {
                showToast("Please fill in all fields")
                return@setOnClickListener
            }

            showToast("Account created successfully (Mock)")
            completeAuthentication()
        }
    }

    /**
     * Updates the NavBackStack savedStateHandle and navigates back to HomeFragment.
     */
    private fun completeAuthentication() {
        findNavController().previousBackStackEntry?.savedStateHandle?.set(KEY_IS_LOGGED_IN, true)
        findNavController().popBackStack()
    }

    companion object {
        const val KEY_IS_LOGGED_IN = "is_logged_in"
    }
}
