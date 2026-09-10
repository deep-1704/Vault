package com.project.vault.ui.home.add

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import com.project.vault.R
import com.project.vault.databinding.FormLoginBinding

/**
 * Custom view that renders the Username & Password credential form.
 *
 * Inflates [R.layout.form_login] using a <merge> root, so this LinearLayout
 * becomes the effective root. The parent [AddCredentialBottomSheet] adds this
 * view into its [formContainer] FrameLayout when the user picks LOGIN type.
 *
 * Call [validate] before reading [getFormData] to ensure all fields are filled.
 */
class LoginFormView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val binding: FormLoginBinding

    init {
        orientation = VERTICAL
        binding = FormLoginBinding.inflate(
            android.view.LayoutInflater.from(context),
            this
        )
    }

    /**
     * Validates all fields:
     * - Title is required.
     * - At least one of Email, Username, or Password must be provided.
     * - If Email is provided, its format must be valid.
     *
     * Sets errors on invalid [TextInputLayout]s and clears errors on valid ones.
     *
     * @return `true` if all fields pass; `false` otherwise.
     */
    fun validate(): Boolean {
        val requiredMsg = context.getString(R.string.error_field_required)
        var isValid = true

        val title = binding.etTitle.text.toString().trim()
        if (title.isBlank()) {
            binding.tilTitle.error = requiredMsg
            isValid = false
        } else {
            binding.tilTitle.error = null
        }

        val email = binding.etEmail.text.toString().trim()
        val username = binding.etUsername.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        val hasAtLeastOne = email.isNotBlank() || username.isNotBlank() || password.isNotBlank()

        if (!hasAtLeastOne) {
            val atLeastOneMsg = context.getString(R.string.error_login_at_least_one)
            binding.tilEmail.error = atLeastOneMsg
            binding.tilUsername.error = atLeastOneMsg
            binding.tilPassword.error = atLeastOneMsg
            isValid = false
        } else {
            binding.tilUsername.error = null
            binding.tilPassword.error = null

            if (email.isNotBlank() && !isValidEmail(email)) {
                binding.tilEmail.error = context.getString(R.string.error_invalid_email)
                isValid = false
            } else {
                binding.tilEmail.error = null
            }
        }

        return isValid
    }

    /**
     * Pre-populates the form with existing credential data for edit mode.
     */
    fun populate(data: CredentialFormData.LoginCredentialData) {
        binding.etTitle.setText(data.title)
        binding.etEmail.setText(data.email)
        binding.etUsername.setText(data.username)
        binding.etPassword.setText(data.password)
    }

    /**
     * Returns the form data. Call [validate] first to ensure requirements are met.
     */
    fun getFormData(): CredentialFormData.LoginCredentialData =
        CredentialFormData.LoginCredentialData(
            title    = binding.etTitle.text.toString().trim(),
            email    = binding.etEmail.text.toString().trim(),
            username = binding.etUsername.text.toString().trim(),
            password = binding.etPassword.text.toString().trim()
        )

    companion object {
        /**
         * Validates email format using Android's standard email pattern.
         */
        fun isValidEmail(email: String): Boolean {
            return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
        }
    }
}

