package com.project.vault.ui.home.showcred

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import com.project.vault.R
import com.project.vault.databinding.ViewShowLoginBinding
import com.project.vault.ui.home.add.CredentialFormData

/**
 * Custom view that renders decrypted Login credential details (Username & Password).
 *
 * Provides:
 *  - Masked and unmasked display of the password with a show/hide toggle button.
 *  - Copy-to-clipboard buttons for username and password.
 */
class LoginShowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val binding: ViewShowLoginBinding

    private var passwordRevealed = false
    private var rawPassword = ""

    init {
        orientation = VERTICAL
        binding = ViewShowLoginBinding.inflate(LayoutInflater.from(context), this, true)
    }

    /**
     * Binds the login data to the UI views and wires copy/toggle listeners.
     */
    fun bind(
        data: CredentialFormData.LoginCredentialData,
        onCopy: (label: String, text: String) -> Unit
    ) {
        rawPassword = data.password

        binding.tvUsername.text = data.username
        updatePasswordDisplay()

        binding.btnTogglePassword.setOnClickListener {
            passwordRevealed = !passwordRevealed
            updatePasswordDisplay()
        }

        binding.btnCopyUsername.setOnClickListener {
            onCopy(context.getString(R.string.label_username), data.username)
        }

        binding.btnCopyPassword.setOnClickListener {
            onCopy(context.getString(R.string.label_password), rawPassword)
        }
    }

    private fun updatePasswordDisplay() {
        if (passwordRevealed) {
            binding.tvPassword.text = rawPassword
            binding.btnTogglePassword.setImageResource(R.drawable.ic_visibility_off)
        } else {
            binding.tvPassword.text = "•".repeat(rawPassword.length.coerceAtLeast(8))
            binding.btnTogglePassword.setImageResource(R.drawable.ic_visibility)
        }
    }
}
