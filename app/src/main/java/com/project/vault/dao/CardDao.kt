package com.project.vault.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.project.vault.entity.CDCard

@Dao
interface CardDao {
    @Query("SELECT * FROM CDCard")
    fun getAllCards(): LiveData<List<CDCard>>

    @Insert
    suspend fun addCard(card: CDCard)

    @Delete
    suspend fun deleteCard(card: CDCard)
}
