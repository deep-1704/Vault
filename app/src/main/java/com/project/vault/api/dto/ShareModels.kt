package com.project.vault.api.dto

import com.google.gson.annotations.SerializedName

/**
 * One item in the POST /share/{username} request body — one entry per recipient device.
 *
 * @param deviceId     The recipient device's ID.
 * @param sharedCredId The server-side shared credential ID (from [CredentialEntity.serverShareId]),
 *                     or null on the first share.
 * @param content      Hybrid-encrypted credential payload for this specific device,
 *                     Base64-encoded.
 */
data class ShareItemRequest(
    @SerializedName("deviceId")
    val deviceId: String,
    @SerializedName("sharedCredId")
    val sharedCredId: Long?,
    @SerializedName("content")
    val content: String
)

/**
 * Response from POST /share/{username}.
 *
 * @param id    The server-side shared credential ID.
 * @param owner The username of the authenticated credential owner.
 */
data class ShareResponse(
    @SerializedName("id")
    val id: Long,
    @SerializedName("owner")
    val owner: String
)

/**
 * One item returned by GET /share/{deviceId} — represents a single credential
 * payload that was shared with this device.
 *
 * @param deviceId     The target device ID this item was shared to.
 * @param sharedCredId The server-side shared credential ID this item belongs to.
 * @param content      Hybrid-encrypted credential payload for this device, Base64-encoded.
 */
data class ShareItemResponse(
    @SerializedName("deviceId")
    val deviceId: String,
    @SerializedName("sharedCredId")
    val sharedCredId: Long,
    @SerializedName("content")
    val content: String
)
