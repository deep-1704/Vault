package com.project.vault.ui.home.showcred

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import com.project.vault.R
import com.project.vault.databinding.ViewShowCardBinding
import com.project.vault.ui.home.add.CredentialFormData

/**
 * Custom view that renders decrypted Credit / Debit Card credential details.
 *
 * Provides:
 *  - Masked and unmasked display of the card number and CVV with show/hide toggle buttons.
 *  - Copy-to-clipboard buttons for cardholder name, card number, and CVV.
 *  - Formatting for card number and expiration date.
 */
class CardShowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val binding: ViewShowCardBinding

    private var cardNumberRevealed = false
    private var cvvRevealed = false
    private var rawCardNumber = ""
    private var rawCvv = ""

    init {
        orientation = VERTICAL
        binding = ViewShowCardBinding.inflate(LayoutInflater.from(context), this, true)
    }

    /**
     * Binds the card data to the UI views and wires copy/toggle listeners.
     */
    fun bind(
        data: CredentialFormData.CardCredentialData,
        onCopy: (label: String, text: String) -> Unit
    ) {
        rawCardNumber = data.cardNumber
        rawCvv = data.cvv

        binding.tvHolderName.text = data.holderName
        binding.tvExpiry.text = formatExpiry(data.expiryMonth, data.expiryYear)

        updateCardNumberDisplay()
        updateCvvDisplay()

        binding.btnToggleCardNumber.setOnClickListener {
            cardNumberRevealed = !cardNumberRevealed
            updateCardNumberDisplay()
        }

        binding.btnToggleCvv.setOnClickListener {
            cvvRevealed = !cvvRevealed
            updateCvvDisplay()
        }

        binding.btnCopyHolderName.setOnClickListener {
            onCopy(context.getString(R.string.label_card_holder), data.holderName)
        }

        binding.btnCopyCardNumber.setOnClickListener {
            onCopy(context.getString(R.string.label_card_number), rawCardNumber)
        }

        binding.btnCopyCvv.setOnClickListener {
            onCopy(context.getString(R.string.label_cvv), rawCvv)
        }
    }

    private fun updateCardNumberDisplay() {
        if (cardNumberRevealed) {
            binding.tvCardNumber.text = formatCardNumber(rawCardNumber)
            binding.btnToggleCardNumber.setImageResource(R.drawable.ic_visibility_off)
        } else {
            binding.tvCardNumber.text = maskCardNumber(rawCardNumber)
            binding.btnToggleCardNumber.setImageResource(R.drawable.ic_visibility)
        }
    }

    private fun updateCvvDisplay() {
        if (cvvRevealed) {
            binding.tvCvv.text = rawCvv
            binding.btnToggleCvv.setImageResource(R.drawable.ic_visibility_off)
        } else {
            binding.tvCvv.text = "•".repeat(rawCvv.length.coerceAtLeast(3))
            binding.btnToggleCvv.setImageResource(R.drawable.ic_visibility)
        }
    }

    private fun maskCardNumber(number: String): String {
        val clean = number.replace(" ", "")
        if (clean.length <= 4) return "•••• $clean"
        val last4 = clean.takeLast(4)
        return "•••• •••• •••• $last4"
    }

    private fun formatCardNumber(number: String): String {
        val clean = number.replace(" ", "")
        return clean.chunked(4).joinToString(" ")
    }

    private fun formatExpiry(month: String, year: String): String {
        val m = month.padStart(2, '0')
        val y = if (year.length == 4) year.takeLast(2) else year
        return "$m / $y"
    }
}
