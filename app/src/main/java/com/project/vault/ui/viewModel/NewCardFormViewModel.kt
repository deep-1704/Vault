package com.project.vault.ui.viewModel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.project.vault.AppDatabase
import com.project.vault.dao.CardDao
import com.project.vault.model.CDCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NewCardFormViewModel(application: Application): AndroidViewModel(application) {
    private val cardDao: CardDao = AppDatabase.getDatabase(application).cdCardDao()

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

    fun addCard(card: CDCard) {
        viewModelScope.launch(Dispatchers.IO) {
            cardDao.addCard(card)
        }
    }
}
