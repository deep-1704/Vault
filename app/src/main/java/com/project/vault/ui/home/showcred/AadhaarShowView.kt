package com.project.vault.ui.home.showcred

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import com.project.vault.R
import com.project.vault.databinding.ViewShowAadhaarBinding
import com.project.vault.ui.home.add.CredentialFormData

/**
 * Custom view that renders decrypted Aadhaar Card credential details.
 *
 * Provides:
 *  - Plain text display for Name on Card and Mobile Number with copy buttons.
 *  - Masked display of the Aadhaar Number (e.g. •••• •••• 1234) with a
 *    show/hide toggle button and a copy-to-clipboard button.
 */
class AadhaarShowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val binding: ViewShowAadhaarBinding

    private var aadhaarRevealed = false
    private var rawAadhaar = ""

    init {
        orientation = VERTICAL
        binding = ViewShowAadhaarBinding.inflate(LayoutInflater.from(context), this, true)
    }

    /**
     * Binds the Aadhaar card data to the UI views and wires copy/toggle listeners.
     */
    fun bind(
        data: CredentialFormData.AadhaarCardCredentialData,
        onCopy: (label: String, text: String) -> Unit
    ) {
        rawAadhaar = data.aadhaarNumber

        binding.tvNameOnCard.text   = data.nameOnCard
        binding.tvMobileNumber.text = data.mobileNumber

        updateAadhaarDisplay()

        binding.btnToggleAadhaarNumber.setOnClickListener {
            aadhaarRevealed = !aadhaarRevealed
            updateAadhaarDisplay()
        }

        binding.btnCopyNameOnCard.setOnClickListener {
            onCopy(context.getString(R.string.label_name_on_card), data.nameOnCard)
        }
        binding.btnCopyAadhaarNumber.setOnClickListener {
            onCopy(context.getString(R.string.label_aadhaar_number), rawAadhaar)
        }
        binding.btnCopyMobileNumber.setOnClickListener {
            onCopy(context.getString(R.string.label_mobile_number), data.mobileNumber)
        }
    }

    private fun updateAadhaarDisplay() {
        if (aadhaarRevealed) {
            binding.tvAadhaarNumber.text = formatAadhaar(rawAadhaar)
            binding.btnToggleAadhaarNumber.setImageResource(R.drawable.ic_visibility_off)
        } else {
            binding.tvAadhaarNumber.text = maskAadhaar(rawAadhaar)
            binding.btnToggleAadhaarNumber.setImageResource(R.drawable.ic_visibility)
        }
    }

    /**
     * Masks the first 8 digits showing only the last 4.
     *
     * Example: 123456789012 → •••• •••• 9012
     */
    private fun maskAadhaar(number: String): String {
        if (number.length != 12) return number
        val last4 = number.takeLast(4)
        return "•••• •••• $last4"
    }

    /**
     * Formats the 12-digit Aadhaar number into groups of 4 separated by spaces.
     *
     * Example: 123456789012 → 1234 5678 9012
     */
    private fun formatAadhaar(number: String): String {
        return number.chunked(4).joinToString(" ")
    }
}
