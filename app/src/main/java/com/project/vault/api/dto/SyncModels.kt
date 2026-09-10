package com.project.vault.api.dto

import com.google.gson.annotations.SerializedName

/**
 * A single device entry returned by GET /device.
 */
data class DeviceDto(
    @SerializedName("id")
    val id: String,
    @SerializedName("owner")
    val owner: String,
    @SerializedName("public_key")
    val publicKey: String
)

/**
 * One item in the POST /sync request body — one entry per target device.
 *
 * @param deviceId     The target device's UUID.
 * @param credentialId The server-side credential ID, or null on the first sync
 *                     (the server will create a new record).
 * @param content      Hybrid-encrypted credential payload for this specific device,
 *                     Base64-encoded.
 */
data class SyncItemRequest(
    @SerializedName("deviceId")
    val deviceId: String,
    @SerializedName("credentialId")
    val credentialId: Long?,
    @SerializedName("content")
    val content: String
)

/**
 * Response from POST /sync — the server-assigned credential record.
 *
 * @param id    The server-side credential ID. Store as [CredentialEntity.serverId]
 *              for subsequent sync calls so the server updates rather than creates.
 * @param owner The username of the authenticated credential owner.
 */
data class SyncResponse(
    @SerializedName("id")
    val id: Long,
    @SerializedName("owner")
    val owner: String
)

/**
 * One item returned by GET /sync/{deviceId} — represents a single credential
 * payload that was synced to this device.
 *
 * @param deviceId     The target device ID this item was synced to.
 * @param credentialId The server-side credential ID this item belongs to.
 * @param content      Hybrid-encrypted credential payload for this device, Base64-encoded.
 */
data class SyncItemResponse(
    @SerializedName("deviceId")
    val deviceId: String,
    @SerializedName("credentialId")
    val credentialId: Long,
    @SerializedName("content")
    val content: String
)
