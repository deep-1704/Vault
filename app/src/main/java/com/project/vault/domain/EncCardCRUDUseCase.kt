package com.project.vault.domain

import android.app.Application
import androidx.lifecycle.LiveData
import com.project.vault.AppDatabase
import com.project.vault.dao.EncCardDao
import com.project.vault.entity.EncCDCard

class EncCardCRUDUseCase(
    val context: Application,
    private val encCardDao: EncCardDao = AppDatabase.getDatabase(context).encCardDao()
) {

    fun getAllCards(): LiveData<List<EncCDCard>> {
        return encCardDao.getAllCards()
    }

    suspend fun addCard(card: EncCDCard) {
        encCardDao.addCard(card)
    }

    suspend fun deleteCard(card: EncCDCard) {
        encCardDao.deleteCard(card)
    }

    suspend fun deleteCardWithUid(uid: Int?){
        encCardDao.deleteCardWithUid(uid)
    }
}
