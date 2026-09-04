package com.project.vault.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.project.vault.api.ApiService
import com.project.vault.api.dto.SyncItemRequest
import com.project.vault.entity.CredentialEntity
import com.project.vault.entity.dao.CredentialDao
import com.project.vault.security.AuthSessionManager
import com.project.vault.security.CryptoManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates the full credential sync pipeline:
 *
 *  1. Verify the user is authenticated.
 *  2. Fetch the credential from Room and decrypt its [encJsonContent].
 *  3. Fetch all of the user's registered devices via GET /device.
 *  4. For each device, re-encrypt the plaintext JSON using that device's RSA public
 *     key (hybrid AES-256-GCM + RSA-OAEP via [CryptoManager.encryptWithPublicKey]).
 *  5. POST the list of per-device encrypted items to /sync.
 *  6. On success, update the local [CredentialEntity] with the server-assigned ID
 *     ([serverId]) and mark [isSynced] = true.
 */
@Singleton
class SyncRepository @Inject constructor(
    private val apiService: ApiService,
    private val dao: CredentialDao,
    private val crypto: CryptoManager,
    private val session: AuthSessionManager
) {

    private val gson = Gson()

    /**
     * Syncs the credential identified by [credentialId] (local Room PK) to all of
     * the authenticated user's registered devices.
     *
     * @throws IllegalStateException if the user is not logged in.
     * @throws IllegalArgumentException if no credential with [credentialId] exists.
     * @throws Exception on any network or server error (propagates to the caller).
     */
    suspend fun syncCredential(credentialId: Int) {
        // 1. Auth guard
        if (!session.hasValidSession()) {
            throw IllegalStateException("You must be logged in to sync")
        }

        // 2. Load and decrypt the credential
        val entity = dao.getById(credentialId)
            ?: throw IllegalArgumentException("Credential #$credentialId not found in local database")
        val plaintextJson = crypto.decrypt(entity.encJsonContent)

        // 3. Fetch all user devices
        val devicesResponse = apiService.getDevices()
        if (!devicesResponse.isSuccessful) {
            val code = devicesResponse.code()
            throw Exception("Failed to fetch devices (HTTP $code)")
        }
        val devices = devicesResponse.body()
            ?: throw Exception("Empty device list returned from server")

        if (devices.isEmpty()) {
            throw Exception("No registered devices found for this account")
        }

        // 4. Build per-device sync items — encrypt content for each device individually
        val serverCredId = entity.serverId?.toLongOrNull() // null on first sync
        val syncItems = devices.map { device ->
            val encContent = crypto.encryptWithPublicKey(plaintextJson, device.publicKey)
            SyncItemRequest(
                deviceId     = device.id,
                credentialId = serverCredId,
                content      = encContent
            )
        }

        // 5. POST to /sync
        val syncResponse = apiService.syncCredential(syncItems)
        if (!syncResponse.isSuccessful) {
            val code = syncResponse.code()
            if (code == 404) {
                dao.deleteById(credentialId)
                throw Exception("Credential is no longer present on the server and has been deleted locally")
            }
            throw Exception("Sync failed (HTTP $code)")
        }
        val syncResult = syncResponse.body()
            ?: throw Exception("Empty response from sync server")

        // 6. Update Room — store server ID and mark as synced
        dao.update(
            entity.copy(
                serverId = syncResult.id.toString(),
                isSynced = true,
                lastSyncedAt = System.currentTimeMillis()
            )
        )
    }

    /**
     * Deletes a synced credential from the sync server across all user devices.
     *
     * @throws IllegalStateException if the user is not logged in.
     * @throws Exception on network / server error.
     */
    suspend fun deleteSyncedCredential(serverCredId: Long) {
        if (!session.hasValidSession()) {
            throw IllegalStateException("You must be logged in to sync")
        }
        val response = apiService.deleteSyncedCredential(serverCredId)
        if (!response.isSuccessful && response.code() != 404) {
            val code = response.code()
            throw Exception("Failed to delete credential from sync server (HTTP $code)")
        }
    }

    /**
     * Result of a global refresh operation.
     *
     * @param newImported Number of credentials pulled from the server and saved locally.
     * @param resynced    Number of already-synced local credentials successfully re-synced.
     * @param failed      Number of credentials that failed to sync (skipped gracefully).
     */
    data class RefreshResult(
        val newImported: Int,
        val resynced: Int,
        val failed: Int
    )

    /**
     * Performs a full global refresh for the given [deviceId]:
     *
     *  1. Calls `GET /sync/{deviceId}` to fetch all server items for this device.
     *  2. For each server item, checks if a local credential with that [serverId] already
     *     exists in Room. If not, decrypts the server payload and inserts it as a new
     *     [CredentialEntity] with [isSynced] = true. If it exists, updates it with the latest data.
     *  3. For any local credentials previously synced that are no longer on the server,
     *     removes them from Room to reflect server-side deletion.
     *  4. Returns a [RefreshResult] summarising how many were imported, re-synced, and failed.
     *
     * Individual credential failures are skipped gracefully — all others still complete.
     *
     * @throws IllegalStateException if the user is not logged in.
     * @throws Exception if the initial `GET /sync/{deviceId}` network call fails.
     */
    suspend fun globalRefresh(deviceId: String): RefreshResult {
        if (!session.hasValidSession()) {
            throw IllegalStateException("You must be logged in to sync")
        }

        val initialEntities = dao.getAllSync()

        // 1. Fetch all synced items from the server for this device
        val fetchResponse = apiService.getSyncedItems(deviceId)
        if (!fetchResponse.isSuccessful) {
            throw Exception("Failed to fetch synced items (HTTP ${fetchResponse.code()})")
        }
        val serverItems = fetchResponse.body() ?: emptyList()

        // 2. Process all server items: update existing local records or insert new ones
        var newImported = 0
        var resynced    = 0
        var failed      = 0

        val processedServerIds = mutableSetOf<String>()

        for (item in serverItems) {
            val serverIdStr = item.credentialId.toString()
            processedServerIds.add(serverIdStr)

            val currentEntities = dao.getAllSync()
            val existingEntity = currentEntities.find { it.serverId == serverIdStr }

            runCatching {
                val plaintextJson = crypto.decrypt(item.content)
                val mapType = object : TypeToken<Map<String, String>>() {}.type
                val map: Map<String, String> = gson.fromJson(plaintextJson, mapType) ?: emptyMap()
                val title    = map["title"]?.takeIf { it.isNotBlank() } ?: existingEntity?.title ?: "Untitled"
                val credType = map["credType"]?.takeIf { it.isNotBlank() } ?: existingEntity?.credType ?: "LOGIN"
                val encContent = crypto.encrypt(plaintextJson)

                if (existingEntity == null) {
                    // New item from server — insert into Room
                    dao.insert(
                        CredentialEntity(
                            title          = title,
                            credType       = credType,
                            encJsonContent = encContent,
                            serverId       = serverIdStr,
                            isSynced       = true,
                            lastSyncedAt   = System.currentTimeMillis()
                        )
                    )
                    newImported++
                    android.util.Log.d("SyncRepository", "Successfully imported new server credential #$serverIdStr")
                } else {
                    // Existing item — update with latest server content
                    dao.update(
                        existingEntity.copy(
                            title          = title,
                            credType       = credType,
                            encJsonContent = encContent,
                            isSynced       = true,
                            lastSyncedAt   = System.currentTimeMillis()
                        )
                    )
                    resynced++
                    android.util.Log.d("SyncRepository", "Successfully updated existing credential #$serverIdStr with server version")
                }
            }.onFailure { e ->
                failed++
                android.util.Log.e("SyncRepository", "Failed to process server credential #$serverIdStr", e)
            }
        }

        // 3. For any local credentials previously synced with a serverId that are no longer
        // on the server, delete them locally to reflect server-side deletions.
        for (entity in initialEntities) {
            val sId = entity.serverId
            if (entity.isSynced && sId != null && sId !in processedServerIds) {
                dao.deleteById(entity.id)
                android.util.Log.d("SyncRepository", "Deleted local credential #${entity.id} because serverId $sId was removed on server")
            }
        }

        // 4. For any local credentials marked isSynced = true that were never uploaded (serverId == null),
        // push them to the server.
        val unuploadedSyncedEntities = initialEntities.filter { it.isSynced && it.serverId == null }
        for (entity in unuploadedSyncedEntities) {
            runCatching { syncCredential(entity.id) }
                .onSuccess { resynced++ }
                .onFailure { failed++ }
        }

        return RefreshResult(newImported = newImported, resynced = resynced, failed = failed)
    }
}

