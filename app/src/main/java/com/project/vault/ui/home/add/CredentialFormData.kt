package com.project.vault.ui.home.add

/**
 * Sealed class hierarchy representing the data collected by each credential form.
 *
 * [CardCredentialData] — data from [CardFormView]
 * [LoginCredentialData] — data from [LoginFormView]
 *
 * When persistence is added, map these to the appropriate @Entity classes.
 */
sealed class CredentialFormData {

    data class CardCredentialData(
        val title: String,
        val holderName: String,
        val cardNumber: String,
        val expiryMonth: String,
        val expiryYear: String,
        val cvv: String
    ) : CredentialFormData()

    data class LoginCredentialData(
        val title: String,
        val username: String,
        val password: String
    ) : CredentialFormData()
}
