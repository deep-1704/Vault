package com.project.vault.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class EncCDCard(
    @PrimaryKey @ColumnInfo(name = "uid") override val uid: Int? = null,
    @ColumnInfo(name = "alias") val alias: String,
    @ColumnInfo(name = "card_name") override val cardName: String,
    @ColumnInfo(name = "enc_json") val encJson: String
//    @ColumnInfo(name = "enc_card_number") val cardNumber: String,
//    @ColumnInfo(name = "enc_card_holder_name") val cardHolderName: String,
//    @ColumnInfo(name = "enc_exp_month") val expMonth: Int,
//    @ColumnInfo(name = "enc_exp_year") val expYear: Int,
//    @ColumnInfo(name = "enc_cvv") val cvv: Int
): ICDCard
