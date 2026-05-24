package com.project.vault.ui.viewModel

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.project.vault.domain.EncCardCRUDUseCase
import com.project.vault.entity.CDCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NewCardFormViewModel(application: Application): AndroidViewModel(application) {
    private val encCardCRUDUseCase = EncCardCRUDUseCase(application)

    val cardName: MutableLiveData<String> by lazy {
        MutableLiveData<String>("")
    }
    val cardNumber: MutableLiveData<String> by lazy {
        MutableLiveData<String>("")
    }
    val cardHolderName: MutableLiveData<String> by lazy {
        MutableLiveData<String>("")
    }
    val expMonth: MutableLiveData<Int> by lazy {
        MutableLiveData<Int>()
    }
    val expYear: MutableLiveData<Int> by lazy {
        MutableLiveData<Int>()
    }
    val cvv: MutableLiveData<Int> by lazy {
        MutableLiveData<Int>()
    }

    fun addCard(card: CDCard, onAuthRequired: suspend () -> Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val success = encCardCRUDUseCase.addCard(card, onAuthRequired)
            if (success) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "Card added successfully", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
