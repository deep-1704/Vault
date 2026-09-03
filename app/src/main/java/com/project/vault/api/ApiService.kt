package com.project.vault.api

import com.project.vault.api.dto.DeviceDto
import com.project.vault.api.dto.LoginRequest
import com.project.vault.api.dto.SignupRequest
import com.project.vault.api.dto.SyncItemRequest
import com.project.vault.api.dto.SyncItemResponse
import com.project.vault.api.dto.SyncResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

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
     * Fetches all devices belonging to the currently authenticated user.
     * No username parameter is needed; the server infers the owner from Basic Auth.
     */
    @GET("device")
    suspend fun getDevices(): Response<List<DeviceDto>>

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
}
