package com.project.vault.ui.home.add

import android.content.Context
import android.text.InputFilter
import android.util.AttributeSet
import android.widget.LinearLayout
import com.project.vault.R
import com.project.vault.databinding.FormPanBinding

/**
 * Custom view that renders the PAN Card credential form.
 *
 * Inflates [R.layout.form_pan] using a <merge> root, so this LinearLayout
 * becomes the effective root. The parent [AddCredentialBottomSheet] adds this
 * view into its [formContainer] FrameLayout when the user picks PAN type.
 *
 * An [InputFilter.AllCaps] is applied to the PAN Number field so characters
 * are auto-uppercased as the user types (no manual capitalisation needed).
 *
 * Call [validate] before reading [getFormData] to ensure all fields are valid.
 */
class PanFormView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val binding: FormPanBinding

    /**
     * Valid PAN pattern: [A-Z]{3}[PCHATF][A-Z][0-9]{4}[A-Z]
     *
     * Breakdown:
     *  - Chars 1–3 : Alphabetic series (AAA–ZZZ)
     *  - Char  4   : Holder type — one of P, C, H, A, T, F
     *  - Char  5   : First character of surname/entity name
     *  - Chars 6–9 : Sequential numeric digits (0001–9999)
     *  - Char 10   : Alphabetic check digit (Income Tax Dept. assigned)
     */
    private val PAN_REGEX = Regex("^[A-Z]{3}[PCHATF][A-Z][0-9]{4}[A-Z]$")

    init {
        orientation = VERTICAL
        binding = FormPanBinding.inflate(
            android.view.LayoutInflater.from(context),
            this
        )
        // Auto-uppercase filter so the user never has to switch case manually
        binding.etPanNumber.filters = arrayOf(InputFilter.AllCaps())
    }

    /**
     * Validates all fields:
     * - Title:       non-blank.
     * - PAN Number:  non-blank AND matches [PAN_REGEX].
     * - Name on Card: non-blank.
     *
     * Sets an error on each invalid [TextInputLayout] and clears errors on valid ones.
     *
     * @return `true` if all fields pass; `false` otherwise.
     */
    fun validate(): Boolean {
        val requiredMsg = context.getString(R.string.error_field_required)
        var isValid = true

        fun checkRequired(
            til: com.google.android.material.textfield.TextInputLayout,
            value: String
        ): Boolean {
            return if (value.isBlank()) {
                til.error = requiredMsg
                false
            } else {
                til.error = null
                true
            }
        }

        fun checkPanNumber(): Boolean {
            val pan = binding.etPanNumber.text.toString().trim()
            return when {
                pan.isBlank() -> {
                    binding.tilPanNumber.error = requiredMsg
                    false
                }
                !PAN_REGEX.matches(pan) -> {
                    binding.tilPanNumber.error = context.getString(R.string.error_invalid_pan)
                    false
                }
                else -> {
                    binding.tilPanNumber.error = null
                    true
                }
            }
        }

        isValid = checkRequired(binding.tilTitle,      binding.etTitle.text.toString())      && isValid
        isValid = checkPanNumber()                                                            && isValid
        isValid = checkRequired(binding.tilNameOnCard, binding.etNameOnCard.text.toString()) && isValid

        return isValid
    }

    /**
     * Pre-populates the form with existing credential data for edit mode.
     */
    fun populate(data: CredentialFormData.PanCardCredentialData) {
        binding.etTitle.setText(data.title)
        binding.etPanNumber.setText(data.panNumber)
        binding.etNameOnCard.setText(data.nameOnCard)
    }

    /**
     * Returns the form data. Call [validate] first to ensure fields are valid.
     */
    fun getFormData(): CredentialFormData.PanCardCredentialData =
        CredentialFormData.PanCardCredentialData(
            title      = binding.etTitle.text.toString().trim(),
            panNumber  = binding.etPanNumber.text.toString().trim(),
            nameOnCard = binding.etNameOnCard.text.toString().trim()
        )
}
