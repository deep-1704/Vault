package com.project.vault.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey


@Entity
data class CDCard(
    @PrimaryKey @ColumnInfo(name = "uid") val uid: Int? = null,
    @ColumnInfo(name = "card_name") val cardName: String,
    @ColumnInfo(name = "card_number") val cardNumber: String,
    @ColumnInfo(name = "card_holder_name") val cardHolderName: String,
    @ColumnInfo(name = "exp_month") val expMonth: Int,
    @ColumnInfo(name = "exp_year") val expYear: Int,
    @ColumnInfo(name = "cvv") val cvv: Int
)
