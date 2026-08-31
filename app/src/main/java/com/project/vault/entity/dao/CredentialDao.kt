package com.project.vault.entity.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.project.vault.entity.CredentialEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for [CredentialEntity].
 *
 * - [getAllFlow] returns a hot [Flow] that the ViewModel collects via [asLiveData()].
 *   Room automatically re-emits whenever the `credentials` table changes.
 * - [insert] is a suspend function safe to call from a coroutine on any dispatcher.
 * - [delete] removes a single entity by its primary key match.
 */
@Dao
interface CredentialDao {

    /**
     * Returns all credentials ordered newest-first.
     * The [Flow] keeps emitting on every insert/delete — no manual refresh needed.
     */
    @Query("SELECT * FROM credentials ORDER BY id DESC")
    fun getAllFlow(): Flow<List<CredentialEntity>>

    /**
     * Inserts a new credential. Returns the new row ID.
     * REPLACE strategy handles the edge case of a duplicate [id] (should not occur
     * with autoGenerate, but guards against bugs during development).
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(credential: CredentialEntity): Long

    /** Deletes a credential by its primary key match. */
    @Delete
    suspend fun delete(credential: CredentialEntity)

    /** Returns a single credential by ID, or null if not found. */
    @Query("SELECT * FROM credentials WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): CredentialEntity?

    /** Updates an existing credential record. */
    @androidx.room.Update
    suspend fun update(credential: CredentialEntity)

    /** Deletes a credential by ID. */
    @Query("DELETE FROM credentials WHERE id = :id")
    suspend fun deleteById(id: Int)
}

