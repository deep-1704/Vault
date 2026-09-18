package com.project.vault.ui.home.showcred

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import com.project.vault.R
import com.project.vault.databinding.ViewShowPanBinding
import com.project.vault.ui.home.add.CredentialFormData

/**
 * Custom view that renders decrypted PAN Card credential details.
 *
 * Provides:
 *  - Plain text display of the Name on Card with a copy-to-clipboard button.
 *  - Masked display of the PAN Number (e.g. ABCDE••••A) with a show/hide toggle
 *    button and a copy-to-clipboard button.
 */
class PanShowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val binding: ViewShowPanBinding

    private var panRevealed = false
    private var rawPan = ""

    init {
        orientation = VERTICAL
        binding = ViewShowPanBinding.inflate(LayoutInflater.from(context), this, true)
    }

    /**
     * Binds the PAN card data to the UI views and wires copy/toggle listeners.
     */
    fun bind(
        data: CredentialFormData.PanCardCredentialData,
        onCopy: (label: String, text: String) -> Unit
    ) {
        rawPan = data.panNumber

        binding.tvNameOnCard.text = data.nameOnCard
        updatePanDisplay()

        binding.btnTogglePanNumber.setOnClickListener {
            panRevealed = !panRevealed
            updatePanDisplay()
        }

        binding.btnCopyNameOnCard.setOnClickListener {
            onCopy(context.getString(R.string.label_name_on_card), data.nameOnCard)
        }

        binding.btnCopyPanNumber.setOnClickListener {
            onCopy(context.getString(R.string.label_pan_number), rawPan)
        }
    }

    private fun updatePanDisplay() {
        if (panRevealed) {
            binding.tvPanNumber.text = rawPan
            binding.btnTogglePanNumber.setImageResource(R.drawable.ic_visibility_off)
        } else {
            binding.tvPanNumber.text = maskPan(rawPan)
            binding.btnTogglePanNumber.setImageResource(R.drawable.ic_visibility)
        }
    }

    /**
     * Masks the middle 4 numeric digits of a PAN while keeping the first 5
     * and last 1 characters visible.
     *
     * Example: ABCDE1234F → ABCDE••••F
     */
    private fun maskPan(pan: String): String {
        if (pan.length != 10) return pan
        val prefix = pan.take(5)          // chars 1–5
        val suffix = pan.takeLast(1)      // char 10
        return "$prefix••••$suffix"
    }
}
