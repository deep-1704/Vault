package com.project.vault.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.project.vault.databinding.NewCardFormFragmentBinding
import com.project.vault.entity.CDCard
import com.project.vault.ui.viewModel.NewCardFormViewModel

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
                    ))
                    Toast.makeText(requireContext(), "Card submitted successfully", Toast.LENGTH_SHORT).show()
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
                tilCardName.error = "Card name cannot be empty"
                return false
            }

            val cardNumber = etCardNumber.text.toString()
            if (cardNumber.length != 16) {
                tilCardNumber.error = "Card number must be 16 digits"
                return false
            }

            val cardHolderName = etCardHolderName.text.toString()
            if (cardHolderName.isBlank()) {
                tilCardHolderName.error = "Card holder name cannot be empty"
                return false
            }

            val expMonthStr = etExpMonth.text.toString()
            val expMonth = expMonthStr.toIntOrNull()
            if (expMonth == null || expMonth !in 1..12) {
                tilExpMonth.error = "Month must be between 1 and 12"
                return false
            }

            val expYear = etExpYear.text.toString()
            if (expYear.isBlank()) {
                tilExpYear.error = "Exp year cannot be empty"
                return false
            }

            val cvv = etCvv.text.toString()
            if (cvv.length != 3) {
                tilCvv.error = "CVV must be 3 digits"
                return false
            }
        }
        return true
    }
}
