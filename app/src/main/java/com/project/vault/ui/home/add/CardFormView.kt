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
     * Validates all fields with basic non-empty checks and card number Luhn validation.
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

        fun checkCardNumber(): Boolean {
            val cardNumber = binding.etCardNumber.text.toString().trim()
            return when {
                cardNumber.isBlank() -> {
                    binding.tilCardNumber.error = requiredMsg
                    false
                }
                !isValidLuhn(cardNumber) -> {
                    binding.tilCardNumber.error = context.getString(R.string.error_luhn_failed)
                    false
                }
                else -> {
                    binding.tilCardNumber.error = null
                    true
                }
            }
        }

        fun checkExpiryMonth(): Boolean {
            val monthStr = binding.etExpiryMonth.text.toString().trim()
            val month = monthStr.toIntOrNull()
            return when {
                monthStr.isBlank() -> {
                    binding.tilExpiryMonth.error = requiredMsg
                    false
                }
                month == null || month !in 1..12 -> {
                    binding.tilExpiryMonth.error = context.getString(R.string.error_invalid_month)
                    false
                }
                else -> {
                    binding.tilExpiryMonth.error = null
                    true
                }
            }
        }

        isValid = check(binding.tilTitle,        binding.etTitle.text.toString())       && isValid
        isValid = check(binding.tilHolderName,   binding.etHolderName.text.toString())  && isValid
        isValid = checkCardNumber()                                                     && isValid
        isValid = checkExpiryMonth()                                                    && isValid
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

    companion object {
        /**
         * Validates a card number using Luhn's algorithm (Mod 10):
         * 1. Start from the right: Begin with the second-to-last digit, moving left.
         * 2. Double every second digit: Multiply every alternate digit by 2.
         * 3. Adjust two-digit numbers: If doubling results in a number greater than 9,
         *    add the two digits together (or subtract 9).
         * 4. Sum all numbers: Add single digits from doubled numbers plus untouched digits in odd positions.
         * 5. Check total: If total sum is a multiple of 10, the card number is valid.
         */
        fun isValidLuhn(number: String): Boolean {
            val sanitized = number.replace(" ", "").replace("-", "")
            if (sanitized.isEmpty()) return false

            var step2Sum = 0 // Sum of modified even-position digits
            var step3Sum = 0 // Sum of odd-position digits

            for (i in sanitized.length - 1 downTo 0) {
                val digit = sanitized[i].digitToIntOrNull() ?: return false
                val positionFromRight = sanitized.length - i // 1-based position from right

                if (positionFromRight % 2 == 0) {
                    // Double every second digit from right
                    var doubled = digit * 2
                    // If doubling results in a number > 9, add the digits (or subtract 9)
                    if (doubled > 9) {
                        doubled = (doubled / 10) + (doubled % 10)
                    }
                    step2Sum += doubled
                } else {
                    // Untouched digits in odd positions from right
                    step3Sum += digit
                }
            }

            // Check if total sum is a multiple of 10
            return (step2Sum + step3Sum) % 10 == 0
        }
    }
}

