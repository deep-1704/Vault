package com.project.vault.entity

import com.google.gson.annotations.SerializedName
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey


data class CDCard(
    override val uid: Int? = null,
    @SerializedName("card_name") override val cardName: String,
    @SerializedName("card_number") val cardNumber: String,
    @SerializedName("card_holder_name") val cardHolderName: String,
    @SerializedName("exp_month") val expMonth: Int,
    @SerializedName("exp_year") val expYear: Int,
    @SerializedName("cvv") val cvv: Int
) : ICDCard
