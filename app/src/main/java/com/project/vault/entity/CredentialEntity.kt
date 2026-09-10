package com.project.vault.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a stored credential.
 *
 * Schema notes:
 *  - [id]             Auto-generated local primary key.
 *  - [serverId]       Server-assigned UUID, null until the credential is synced.
 *  - [isShared]       True when the credential has been shared with another user.
 *  - [isSynced]       True when the credential has been successfully pushed to the backend.
 *  - [title]          Plaintext credential name — stored unencrypted so the home list
 *                     can display titles without a decryption pass per row.
 *  - [credType]       Plaintext type tag ("CARD" or "LOGIN") — unencrypted for future
 *                     filtering/search.
 *  - [encJsonContent] Base64(RSA-OAEP(JSON)) — all sensitive fields (card number, CVV,
 *                     password, expiry, etc.) live here and never touch disk in plaintext.
 */
@Entity(tableName = "credentials")
data class CredentialEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    @ColumnInfo(name = "server_id")
    val serverId: String? = null,

    @ColumnInfo(name = "is_shared")
    val isShared: Boolean = false,

    @ColumnInfo(name = "is_synced")
    val isSynced: Boolean = false,

    @ColumnInfo(name = "last_synced_at")
    val lastSyncedAt: Long? = null,

    val title: String,

    @ColumnInfo(name = "cred_type")
    val credType: String,

    @ColumnInfo(name = "server_share_id")
    val serverShareId: String? = null,

    @ColumnInfo(name = "is_received")
    val isReceived: Boolean = false,

    @ColumnInfo(name = "enc_json_content")
    val encJsonContent: String
)
