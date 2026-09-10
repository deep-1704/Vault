package com.project.vault.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.project.vault.api.ApiService
import com.project.vault.api.dto.DeviceDto
import com.project.vault.api.dto.ShareItemRequest
import com.project.vault.entity.dao.CredentialDao
import com.project.vault.security.AuthSessionManager
import com.project.vault.security.CryptoManager
import javax.inject.Inject
import javax.inject.Provider
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
    private val session: AuthSessionManager,
    private val syncRepositoryProvider: Provider<SyncRepository>
) {

    private val gson = Gson()

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
        if (entity.isReceived) {
            throw IllegalStateException("Received credentials cannot be shared")
        }
        val plaintextJson = crypto.decrypt(entity.encJsonContent)
        val mapType = object : TypeToken<MutableMap<String, String>>() {}.type
        val jsonMap: MutableMap<String, String> = runCatching {
            gson.fromJson<MutableMap<String, String>>(plaintextJson, mapType)
        }.getOrNull() ?: mutableMapOf()

        if (entity.serverId != null) {
            jsonMap["serverId"] = entity.serverId
        }
        if (entity.serverShareId != null) {
            jsonMap["serverShareId"] = entity.serverShareId
        }
        val enrichedPlaintextJson = gson.toJson(jsonMap)

        // 2. Encrypt plaintext for each recipient device with its RSA public key
        val serverShareId = entity.serverShareId?.toLongOrNull()
        val shareItems = devices.map { device ->
            val encContent = crypto.encryptWithPublicKey(enrichedPlaintextJson, device.publicKey)
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

        // 4. Update Room entity with isShared = true, serverShareId, and updated encJsonContent
        val shareIdStr = shareResult.id.toString()
        jsonMap["serverShareId"] = shareIdStr
        val updatedLocalEncContent = crypto.encrypt(gson.toJson(jsonMap))

        dao.update(
            entity.copy(
                isShared       = true,
                serverShareId  = shareIdStr,
                encJsonContent = updatedLocalEncContent
            )
        )

        // 5. If the credential was already synced, re-sync to the owner's devices
        // so their other devices receive the updated serverShareId and isShared = true
        if (entity.isSynced) {
            runCatching {
                syncRepositoryProvider.get().syncCredential(credentialId)
            }.onFailure { e ->
                android.util.Log.e("ShareRepository", "Auto-resync failed after sharing credential #$credentialId", e)
            }
        }

        return shareResult.id
    }

    /**
     * Publishes updated credential content to all recipient devices that have access
     * to the shared credential with [sharedCredId].
     *
     * @return Number of recipient devices to which updates were published.
     */
    suspend fun publishSharedUpdate(
        credentialId: Int,
        sharedCredId: Long
    ): Int {
        if (!session.hasValidSession()) {
            throw IllegalStateException("You must be logged in to update shared credentials")
        }

        // 1. Fetch devices that have received access to this shared credential
        val devicesResponse = apiService.getSharedDevices(sharedCredId)
        if (!devicesResponse.isSuccessful) {
            val code = devicesResponse.code()
            throw Exception("Failed to fetch shared devices (HTTP $code)")
        }
        val devices = devicesResponse.body() ?: emptyList()
        if (devices.isEmpty()) {
            return 0
        }

        // 2. Fetch local credential and decrypt content
        val entity = dao.getById(credentialId)
            ?: throw IllegalArgumentException("Credential #$credentialId not found in local database")
        val plaintextJson = crypto.decrypt(entity.encJsonContent)
        val mapType = object : TypeToken<MutableMap<String, String>>() {}.type
        val jsonMap: MutableMap<String, String> = runCatching {
            gson.fromJson<MutableMap<String, String>>(plaintextJson, mapType)
        }.getOrNull() ?: mutableMapOf()

        if (entity.serverId != null) {
            jsonMap["serverId"] = entity.serverId
        }
        jsonMap["serverShareId"] = sharedCredId.toString()
        val enrichedPlaintextJson = gson.toJson(jsonMap)

        // 3. Group devices by owner and POST /share/{username} for each recipient user
        val devicesByOwner = devices.groupBy { it.owner }
        for ((recipientUsername, recipientDevices) in devicesByOwner) {
            val shareItems = recipientDevices.map { device ->
                val encContent = crypto.encryptWithPublicKey(enrichedPlaintextJson, device.publicKey)
                ShareItemRequest(
                    deviceId     = device.id,
                    sharedCredId = sharedCredId,
                    content      = encContent
                )
            }
            val response = apiService.shareCredential(recipientUsername, shareItems)
            if (!response.isSuccessful) {
                val code = response.code()
                throw Exception("Failed to publish update to user '$recipientUsername' (HTTP $code)")
            }
        }

        return devices.size
    }
}
