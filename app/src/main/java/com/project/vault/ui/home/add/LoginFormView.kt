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
     * Validates all fields with basic non-empty checks.
     * Sets an error on each invalid [TextInputLayout] and clears errors on valid ones.
     *
     * @return `true` if all fields pass; `false` otherwise.
     */
    fun validate(): Boolean {
        val requiredMsg = context.getString(R.string.error_field_required)
        var isValid = true

        fun check(til: com.google.android.material.textfield.TextInputLayout,
                  value: String): Boolean {
            return if (value.isBlank()) {
                til.error = requiredMsg
                false
            } else {
                til.error = null
                true
            }
        }

        isValid = check(binding.tilTitle,    binding.etTitle.text.toString())    && isValid
        isValid = check(binding.tilUsername, binding.etUsername.text.toString()) && isValid
        isValid = check(binding.tilPassword, binding.etPassword.text.toString()) && isValid

        return isValid
    }

    /**
     * Returns the form data. Call [validate] first to ensure fields are non-empty.
     */
    fun getFormData(): CredentialFormData.LoginCredentialData =
        CredentialFormData.LoginCredentialData(
            title    = binding.etTitle.text.toString().trim(),
            username = binding.etUsername.text.toString().trim(),
            password = binding.etPassword.text.toString().trim()
        )
}
