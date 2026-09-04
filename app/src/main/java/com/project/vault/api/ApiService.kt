package com.project.vault.api

import com.project.vault.api.dto.DeviceDto
import com.project.vault.api.dto.LoginRequest
import com.project.vault.api.dto.SignupRequest
import com.project.vault.api.dto.ShareItemRequest
import com.project.vault.api.dto.ShareItemResponse
import com.project.vault.api.dto.ShareResponse
import com.project.vault.api.dto.SyncItemRequest
import com.project.vault.api.dto.SyncItemResponse
import com.project.vault.api.dto.SyncResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit service interface defining Vault Sync API endpoints.
 */
interface ApiService {

    /**
     * Registers a new user account and registers their initial device.
     */
    @POST("auth/signup")
    suspend fun signup(
        @Body request: SignupRequest
    ): Response<Unit>

    /**
     * Authenticates an existing user and registers a new/active device.
     * Requires HTTP Basic Auth in the [basicAuth] header.
     */
    @POST("auth/login")
    suspend fun login(
        @Header("Authorization") basicAuth: String,
        @Body request: LoginRequest
    ): Response<Unit>

    /**
     * Fetches all devices belonging to a user.
     * If [username] query parameter is omitted, it defaults to the authenticated user.
     */
    @GET("device")
    suspend fun getDevices(
        @Query("username") username: String? = null
    ): Response<List<DeviceDto>>

    /**
     * Shares credentials with another user's devices.
     * If [ShareItemRequest.sharedCredId] is null, a new SharedCredential is created.
     *
     * @param username Username of recipient user.
     * @param items Encrypted payload per recipient device.
     */
    @POST("share/{username}")
    suspend fun shareCredential(
        @Path("username") username: String,
        @Body items: List<ShareItemRequest>
    ): Response<ShareResponse>

    /**
     * Syncs encrypted credential items across the authenticated user's devices.
     * If [SyncItemRequest.credentialId] is null, the server creates a new record;
     * otherwise it updates the existing one.
     *
     * @return [SyncResponse] containing the server-assigned credential ID and owner.
     */
    @POST("sync")
    suspend fun syncCredential(
        @Body items: List<SyncItemRequest>
    ): Response<SyncResponse>

    /**
     * Fetches all synced credential items destined for the specified device.
     * Verifies that [deviceId] belongs to the authenticated user.
     *
     * @return List of [SyncItemResponse] items — one per synced credential on the server.
     */
    @GET("sync/{deviceId}")
    suspend fun getSyncedItems(
        @Path("deviceId") deviceId: String
    ): Response<List<SyncItemResponse>>

    /**
     * Deletes a synced credential from the sync server across all user devices.
     *
     * @param credId The server-assigned credential ID.
     */
    @DELETE("sync/{credId}")
    suspend fun deleteSyncedCredential(
        @Path("credId") credId: Long
    ): Response<Unit>

    /**
     * Fetches all shared credential items destined for the specified device.
     * Verifies that [deviceId] belongs to the authenticated user.
     *
     * @return List of [ShareItemResponse] items — one per shared credential on the server.
     */
    @GET("share/{deviceId}")
    suspend fun getSharedItems(
        @Path("deviceId") deviceId: String
    ): Response<List<ShareItemResponse>>

    /**
     * Deletes or revokes shared credential access.
     * If called by recipient: revokes access for specified device (if [deviceId] provided) or all recipient devices.
     *
     * @param sharedCredId The server-side shared credential ID.
     * @param deviceId Optional device ID to revoke access from.
     */
    @DELETE("share/{sharedCredId}")
    suspend fun deleteSharedCredential(
        @Path("sharedCredId") sharedCredId: Long,
        @Query("deviceId") deviceId: String? = null
    ): Response<Unit>
}
