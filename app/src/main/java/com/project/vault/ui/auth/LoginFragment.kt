package com.project.vault.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
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

    private val viewModel: AuthViewModel by viewModels()

    override fun getViewBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentLoginBinding = FragmentLoginBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeErrors(viewModel)
        setupActions()
        observeViewModel()
    }

    private fun setupActions() {
        // Login submission action
        binding.btnSubmitLogin.setOnClickListener {
            val username = binding.etUsername.text.toString().trim()
            val password = binding.etPassword.text.toString()
            viewModel.login(username, password)
        }

        // Account creation action
        binding.btnCreateAccount.setOnClickListener {
            val username = binding.etUsername.text.toString().trim()
            val password = binding.etPassword.text.toString()
            viewModel.signup(username, password)
        }
    }

    private fun observeViewModel() {
        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is AuthUiState.Idle -> {
                    setLoading(false)
                }
                is AuthUiState.Loading -> {
                    setLoading(true)
                }
                is AuthUiState.Success -> {
                    setLoading(false)
                    showToast(state.message)
                    completeAuthentication()
                }
                is AuthUiState.Error -> {
                    setLoading(false)
                    showToast(state.message)
                }
            }
        }
    }

    private fun setLoading(isLoading: Boolean) {
        binding.progressBarAuth.isVisible = isLoading
        binding.btnSubmitLogin.isEnabled = !isLoading
        binding.btnCreateAccount.isEnabled = !isLoading
        binding.etUsername.isEnabled = !isLoading
        binding.etPassword.isEnabled = !isLoading
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

