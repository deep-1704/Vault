package com.project.vault.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.project.vault.R
import com.project.vault.databinding.NewCardFormFragmentBinding
import com.project.vault.entity.CDCard
import com.project.vault.ui.viewModel.NewCardFormViewModel

import com.project.vault.util.BiometricHelper

class NewCardFormFragment: Fragment() {
    private var _binding: NewCardFormFragmentBinding? = null
    private val binding get() = _binding!!
    private val viewModel: NewCardFormViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = NewCardFormFragmentBinding.inflate(inflater, container, false)

        binding.apply {

            // Restore previously entered values
            etCardName.setText(viewModel.cardName.value)
            etCardNumber.setText(viewModel.cardNumber.value)
            etCardHolderName.setText(viewModel.cardHolderName.value)
            viewModel.expMonth.value?.let { etExpMonth.setText(it.toString()) }
            viewModel.expYear.value?.let { etExpYear.setText(it.toString()) }
            viewModel.cvv.value?.let { etCvv.setText(it.toString()) }

            // Register onClickListener for submit button
            btnSubmit.setOnClickListener {
                if(validateInputs()){
                    viewModel.addCard(CDCard(
                        cardName = etCardName.text.toString(),
                        cardNumber = etCardNumber.text.toString(),
                        cardHolderName = etCardHolderName.text.toString(),
                        expMonth = etExpMonth.text.toString().toInt(),
                        expYear = etExpYear.text.toString().toInt(),
                        cvv = etCvv.text.toString().toInt()
                    )) {
                        BiometricHelper.authenticate(
                            requireActivity(),
                            getString(R.string.biometric_title),
                            getString(R.string.biometric_subtitle)
                        )
                    }
                }
            }
        }

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()

        // Save entered changes to viewModel
        viewModel.apply {
            cardName.value = binding.etCardName.text.toString()
            cardNumber.value = binding.etCardNumber.text.toString()
            cardHolderName.value = binding.etCardHolderName.text.toString()
            expMonth.value = binding.etExpMonth.text.toString().toIntOrNull()
            expYear.value = binding.etExpYear.text.toString().toIntOrNull()
            cvv.value = binding.etCvv.text.toString().toIntOrNull()
        }

        _binding = null
    }

    private fun validateInputs(): Boolean {
        binding.apply {
            // Reset errors
            tilCardName.error = null
            tilCardNumber.error = null
            tilCardHolderName.error = null
            tilExpMonth.error = null
            tilExpYear.error = null
            tilCvv.error = null

            val cardName = etCardName.text.toString()
            if (cardName.isBlank()) {
                tilCardName.error = getString(R.string.error_card_name_empty)
                return false
            }

            val cardNumber = etCardNumber.text.toString()
            if (cardNumber.length !in 13..16) {
                tilCardNumber.error = getString(R.string.error_card_number_length)
                return false
            }

            if (!isValidLuhn(cardNumber)) {
                tilCardNumber.error = getString(R.string.error_luhn_failed)
                return false
            }

            val cardHolderName = etCardHolderName.text.toString()
            if (cardHolderName.isBlank()) {
                tilCardHolderName.error = getString(R.string.error_card_holder_empty)
                return false
            }

            val expMonthStr = etExpMonth.text.toString()
            val expMonth = expMonthStr.toIntOrNull()
            if (expMonth == null || expMonth !in 1..12) {
                tilExpMonth.error = getString(R.string.error_invalid_month)
                return false
            }

            val expYear = etExpYear.text.toString()
            if (expYear.isBlank()) {
                tilExpYear.error = getString(R.string.error_exp_year_empty)
                return false
            }

            val cvv = etCvv.text.toString()
            if (cvv.length != 3) {
                tilCvv.error = getString(R.string.error_cvv_length)
                return false
            }
        }
        return true
    }

    private fun isValidLuhn(number: String): Boolean {
        if (number.isEmpty()) return false

        var step2Sum = 0 // Sum of modified even-position digits
        var step3Sum = 0 // Sum of odd-position digits

        for (i in number.length - 1 downTo 0) {
            val digit = number[i].digitToIntOrNull() ?: return false
            val positionFromRight = number.length - i // 1-based position

            if (positionFromRight % 2 == 0) {
                // Step 1: Double every second digit from right
                var doubled = digit * 2
                // If doubling results in a two-digit number, add the digits
                if (doubled > 9) {
                    doubled = (doubled / 10) + (doubled % 10)
                }
                step2Sum += doubled
            } else {
                // Step 3: Add digits in odd places from right
                step3Sum += digit
            }
        }

        // Step 4 & 5: Check if total sum is divisible by 10
        return (step2Sum + step3Sum) % 10 == 0
    }
}
