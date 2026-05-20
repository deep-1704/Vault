package com.project.vault.ui.viewModel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.project.vault.AppDatabase
import com.project.vault.model.CDCard
import com.project.vault.dao.CardDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CardContainerViewModel(application: Application): AndroidViewModel(application) {
    private val cardDao: CardDao = AppDatabase.getDatabase(application).cdCardDao()
    val cards: LiveData<List<CDCard>> = cardDao.getAllCards()

    fun deleteCardAtIndex(index: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            cards.value?.get(index)?.let {
                cardDao.deleteCard(it)
            }
        }
    }
}
