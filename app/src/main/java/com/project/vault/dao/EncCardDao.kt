package com.project.vault.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.project.vault.entity.EncCDCard

@Dao
interface EncCardDao {
    @Query("SELECT * FROM EncCDCard")
    fun getAllCards(): LiveData<List<EncCDCard>>

    @Insert
    suspend fun addCard(card: EncCDCard)

    @Delete
    suspend fun deleteCard(card: EncCDCard)
    
    @Query("DELETE FROM EncCDCard WHERE uid = :uid")
    suspend fun deleteCardWithUid(uid: Int?)w
}