package com.project.vault.domain

import android.security.keystore.UserNotAuthenticatedException
import com.google.gson.Gson
import com.project.vault.core.Security
import com.project.vault.entity.CDCard
import com.project.vault.entity.EncCDCard

class UnlockCardUseCase {
    companion object {
        suspend fun decryptCard(
            encCDCard: EncCDCard,
            onAuthRequired: suspend () -> Boolean
        ): CDCard? {
            return try {
                performDecryption(encCDCard)
            } catch (e: UserNotAuthenticatedException) {
                if (onAuthRequired()) {
                    try {
                        performDecryption(encCDCard)
                    } catch (retryException: Exception) {
                        null
                    }
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }

        private fun performDecryption(encCDCard: EncCDCard): CDCard {
            val decryptedJson = Security.decryptText(encCDCard.encJson, encCDCard.alias)
            val cdCard = Gson().fromJson(decryptedJson, CDCard::class.java)
            // Ensure the uid and cardName from the database entry are preserved
            return cdCard.copy(uid = encCDCard.uid, cardName = encCDCard.cardName)
        }
    }
}
