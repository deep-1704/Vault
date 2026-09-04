package com.project.vault.repository

import com.project.vault.api.ApiService
import com.project.vault.api.dto.DeviceDto
import com.project.vault.api.dto.ShareItemRequest
import com.project.vault.entity.dao.CredentialDao
import com.project.vault.security.AuthSessionManager
import com.project.vault.security.CryptoManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository responsible for the credential sharing pipeline:
 *  1. Querying recipient devices by username.
 *  2. Decrypting the local credential using the owner's RSA private key.
 *  3. Encrypting the payload with each recipient device's RSA public key.
 *  4. Pushing shared payload to POST /share/{username}.
 *  5. Updating the local Room entity with [isShared] = true and [serverShareId].
 */
@Singleton
class ShareRepository @Inject constructor(
    private val apiService: ApiService,
    private val dao: CredentialDao,
    private val crypto: CryptoManager,
    private val session: AuthSessionManager
) {

    /**
     * Fetches all registered devices for a target [username].
     * Returns an empty list if the user has no devices or is not found.
     *
     * @throws IllegalStateException if the caller is not logged in.
     * @throws Exception on network or server error.
     */
    suspend fun getDevicesByUsername(username: String): List<DeviceDto> {
        if (!session.hasValidSession()) {
            throw IllegalStateException("You must be logged in to search users")
        }

        val response = apiService.getDevices(username)
        if (response.code() == 404) {
            return emptyList()
        }
        if (!response.isSuccessful) {
            throw Exception("Failed to fetch devices for user '$username' (HTTP ${response.code()})")
        }
        return response.body() ?: emptyList()
    }

    /**
     * Shares the credential identified by [credentialId] with the recipient's [devices].
     *
     * Reuses [entity.serverShareId] if previously shared, allowing the backend to
     * associate the new devices under the existing shared credential.
     *
     * @return The server-assigned shared credential ID.
     */
    suspend fun shareCredential(
        credentialId: Int,
        recipientUsername: String,
        devices: List<DeviceDto>
    ): Long {
        if (!session.hasValidSession()) {
            throw IllegalStateException("You must be logged in to share")
        }
        if (devices.isEmpty()) {
            throw IllegalArgumentException("Recipient has no registered devices")
        }

        // 1. Fetch local credential and decrypt plaintext JSON
        val entity = dao.getById(credentialId)
            ?: throw IllegalArgumentException("Credential #$credentialId not found in local database")
        val plaintextJson = crypto.decrypt(entity.encJsonContent)

        // 2. Encrypt plaintext for each recipient device with its RSA public key
        val serverShareId = entity.serverShareId?.toLongOrNull()
        val shareItems = devices.map { device ->
            val encContent = crypto.encryptWithPublicKey(plaintextJson, device.publicKey)
            ShareItemRequest(
                deviceId     = device.id,
                sharedCredId = serverShareId,
                content      = encContent
            )
        }

        // 3. POST to /share/{username}
        val response = apiService.shareCredential(recipientUsername, shareItems)
        if (!response.isSuccessful) {
            val code = response.code()
            when (code) {
                406 -> throw Exception("You cannot share credentials with yourself")
                404 -> throw Exception("Shared credential or recipient not found")
                400 -> throw Exception("Invalid share request")
                else -> throw Exception("Share failed (HTTP $code)")
            }
        }

        val shareResult = response.body()
            ?: throw Exception("Empty response from share server")

        // 4. Update Room entity with isShared = true and serverShareId
        dao.update(
            entity.copy(
                isShared      = true,
                serverShareId = shareResult.id.toString()
            )
        )

        return shareResult.id
    }
}
