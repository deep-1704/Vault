package com.project.vault.ui.viewModel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.viewModelScope
import com.project.vault.entity.CDCard
import com.project.vault.domain.EncCardCRUDUseCase
import com.project.vault.domain.UnlockCardUseCase
import com.project.vault.entity.EncCDCard
import com.project.vault.entity.ICDCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CardContainerViewModel(application: Application): AndroidViewModel(application) {
    private val encCardCRUDUseCase = EncCardCRUDUseCase(application)
    private val _cards = MediatorLiveData<List<ICDCard>>()
    val cards: LiveData<List<ICDCard>> = _cards

    init {
        _cards.addSource(encCardCRUDUseCase.getAllCards()) {
            _cards.value = it
        }
    }

    fun deleteCardAtIndex(index: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            cards.value?.get(index)?.let {
                encCardCRUDUseCase.deleteCardWithUid(it.uid)
            }
        }
    }

    fun unlockCardAtIndex(index: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentList = cards.value?.toMutableList() ?: return@launch
            val card = currentList.getOrNull(index)
            if (card is EncCDCard) {
                val decryptedCard: CDCard = UnlockCardUseCase.decryptCard(card)
                currentList[index] = decryptedCard
                _cards.postValue(currentList)
            }
        }
    }


}
