package com.project.vault.ui.home.add

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import com.project.vault.R
import com.project.vault.databinding.FormCardBinding

/**
 * Custom view that renders the Credit/Debit Card credential form.
 *
 * Inflates [R.layout.form_card] using a <merge> root, so this LinearLayout
 * becomes the effective root. The parent [AddCredentialBottomSheet] adds this
 * view into its [formContainer] FrameLayout when the user picks CARD type.
 *
 * Call [validate] before reading [getFormData] to ensure all fields are filled.
 */
class CardFormView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val binding: FormCardBinding

    init {
        orientation = VERTICAL
        binding = FormCardBinding.inflate(
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

        isValid = check(binding.tilTitle,        binding.etTitle.text.toString())       && isValid
        isValid = check(binding.tilHolderName,   binding.etHolderName.text.toString())  && isValid
        isValid = check(binding.tilCardNumber,   binding.etCardNumber.text.toString())  && isValid
        isValid = check(binding.tilExpiryMonth,  binding.etExpiryMonth.text.toString()) && isValid
        isValid = check(binding.tilExpiryYear,   binding.etExpiryYear.text.toString())  && isValid
        isValid = check(binding.tilCvv,          binding.etCvv.text.toString())         && isValid

        return isValid
    }

    /**
     * Pre-populates the form with existing credential data for edit mode.
     */
    fun populate(data: CredentialFormData.CardCredentialData) {
        binding.etTitle.setText(data.title)
        binding.etHolderName.setText(data.holderName)
        binding.etCardNumber.setText(data.cardNumber)
        binding.etExpiryMonth.setText(data.expiryMonth)
        binding.etExpiryYear.setText(data.expiryYear)
        binding.etCvv.setText(data.cvv)
    }

    /**
     * Returns the form data. Call [validate] first to ensure fields are non-empty.
     */
    fun getFormData(): CredentialFormData.CardCredentialData =
        CredentialFormData.CardCredentialData(
            title       = binding.etTitle.text.toString().trim(),
            holderName  = binding.etHolderName.text.toString().trim(),
            cardNumber  = binding.etCardNumber.text.toString().trim(),
            expiryMonth = binding.etExpiryMonth.text.toString().trim(),
            expiryYear  = binding.etExpiryYear.text.toString().trim(),
            cvv         = binding.etCvv.text.toString().trim()
        )
}

