package com.project.vault.ui.home.add

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import com.google.android.material.textfield.TextInputLayout
import com.project.vault.R
import com.project.vault.databinding.FormAadhaarBinding

/**
 * Custom view that renders the Aadhaar Card credential form.
 *
 * Inflates [R.layout.form_aadhaar] using a <merge> root, so this LinearLayout
 * becomes the effective root. The parent [AddCredentialBottomSheet] adds this
 * view into its [formContainer] FrameLayout when the user picks AADHAAR type.
 *
 * Fields: Title, Name on Card, Aadhaar Number (12 digits), Registered Mobile Number.
 * All fields are mandatory.
 *
 * Call [validate] before reading [getFormData] to ensure all fields are valid.
 */
class AadhaarFormView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val binding: FormAadhaarBinding

    init {
        orientation = VERTICAL
        binding = FormAadhaarBinding.inflate(
            android.view.LayoutInflater.from(context),
            this
        )
    }

    /**
     * Validates all fields:
     * - Title, Name on Card, Registered Mobile Number: non-blank.
     * - Aadhaar Number: non-blank and exactly 12 numeric digits.
     *
     * Sets an error on each invalid [TextInputLayout] and clears errors on valid ones.
     *
     * @return `true` if all fields pass; `false` otherwise.
     */
    fun validate(): Boolean {
        val requiredMsg = context.getString(R.string.error_field_required)
        var isValid = true

        fun checkRequired(til: TextInputLayout, value: String): Boolean {
            return if (value.isBlank()) {
                til.error = requiredMsg
                false
            } else {
                til.error = null
                true
            }
        }

        fun checkAadhaarNumber(): Boolean {
            val number = binding.etAadhaarNumber.text.toString().trim()
            return when {
                number.isBlank() -> {
                    binding.tilAadhaarNumber.error = requiredMsg
                    false
                }
                number.length != 12 || !number.all { it.isDigit() } -> {
                    binding.tilAadhaarNumber.error =
                        context.getString(R.string.error_invalid_aadhaar)
                    false
                }
                else -> {
                    binding.tilAadhaarNumber.error = null
                    true
                }
            }
        }

        fun checkMobileNumber(): Boolean {
            val mobile = binding.etMobileNumber.text.toString().trim()
            return when {
                mobile.isBlank() -> {
                    binding.tilMobileNumber.error = requiredMsg
                    false
                }
                mobile.length != 10 || !mobile.all { it.isDigit() } -> {
                    binding.tilMobileNumber.error =
                        context.getString(R.string.error_invalid_mobile)
                    false
                }
                else -> {
                    binding.tilMobileNumber.error = null
                    true
                }
            }
        }

        isValid = checkRequired(binding.tilTitle,      binding.etTitle.text.toString())      && isValid
        isValid = checkRequired(binding.tilNameOnCard, binding.etNameOnCard.text.toString()) && isValid
        isValid = checkAadhaarNumber()                                                       && isValid
        isValid = checkMobileNumber()                                                        && isValid

        return isValid
    }

    /**
     * Pre-populates the form with existing credential data for edit mode.
     */
    fun populate(data: CredentialFormData.AadhaarCardCredentialData) {
        binding.etTitle.setText(data.title)
        binding.etNameOnCard.setText(data.nameOnCard)
        binding.etAadhaarNumber.setText(data.aadhaarNumber)
        binding.etMobileNumber.setText(data.mobileNumber)
    }

    /**
     * Returns the form data. Call [validate] first to ensure all fields are valid.
     */
    fun getFormData(): CredentialFormData.AadhaarCardCredentialData =
        CredentialFormData.AadhaarCardCredentialData(
            title         = binding.etTitle.text.toString().trim(),
            nameOnCard    = binding.etNameOnCard.text.toString().trim(),
            aadhaarNumber = binding.etAadhaarNumber.text.toString().trim(),
            mobileNumber  = binding.etMobileNumber.text.toString().trim()
        )
}
