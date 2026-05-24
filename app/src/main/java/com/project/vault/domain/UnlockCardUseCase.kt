package com.project.vault.domain

import com.google.gson.Gson
import com.project.vault.core.Security
import com.project.vault.entity.CDCard
import com.project.vault.entity.EncCDCard

class UnlockCardUseCase {
    companion object {
        fun decryptCard(encCDCard: EncCDCard): CDCard {
            val decryptedJson = Security.decryptText(encCDCard.encJson, encCDCard.alias)
            val cdCard = Gson().fromJson(decryptedJson, CDCard::class.java)
            // Ensure the uid and cardName from the database entry are preserved
            return cdCard.copy(uid = encCDCard.uid, cardName = encCDCard.cardName)
        }
    }
}
