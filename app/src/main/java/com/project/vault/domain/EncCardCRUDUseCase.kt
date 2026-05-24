package com.project.vault.domain

import android.app.Application
import androidx.lifecycle.LiveData
import android.security.keystore.UserNotAuthenticatedException
import com.google.gson.Gson
import com.project.vault.AppDatabase
import com.project.vault.core.Security
import com.project.vault.dao.EncCardDao
import com.project.vault.entity.CDCard
import com.project.vault.entity.EncCDCard

class EncCardCRUDUseCase(
    val context: Application,
    private val encCardDao: EncCardDao = AppDatabase.getDatabase(context).encCardDao()
) {

    fun getAllCards(): LiveData<List<EncCDCard>> {
        return encCardDao.getAllCards()
    }

    suspend fun addCard(card: CDCard, onAuthRequired: suspend () -> Boolean): Boolean {
        val alias = generateRandomAlias(10)
        Security.generateKey(alias)

        val encryptedData = mapOf(
            "card_number" to card.cardNumber,
            "card_holder_name" to card.cardHolderName,
            "exp_month" to card.expMonth,
            "exp_year" to card.expYear,
            "cvv" to card.cvv
        )
        val json = Gson().toJson(encryptedData)

        return try {
            performAddCard(json, alias, card)
            true
        } catch (e: UserNotAuthenticatedException) {
            if (onAuthRequired()) {
                try {
                    performAddCard(json, alias, card)
                    true
                } catch (retryException: Exception) {
                    // Handle failure after auth
                    false
                }
            } else {
                false
            }
        } catch (e: Exception) {
            // Handle other exceptions
            false
        }
    }

    private suspend fun performAddCard(json: String, alias: String, card: CDCard) {
        val encJson = Security.encryptText(json, alias)

        val encCard = EncCDCard(
            alias = alias,
            cardName = card.cardName,
            encJson = encJson
        )
        encCardDao.addCard(encCard)
    }

    private fun generateRandomAlias(length: Int): String {
        val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
        return (1..length)
            .map { allowedChars.random() }
            .joinToString("")
    }

    suspend fun deleteCardWithUid(uid: Int?){
        encCardDao.deleteCardWithUid(uid)
    }
}
