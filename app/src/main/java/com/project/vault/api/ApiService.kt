package com.project.vault.api

import com.project.vault.api.dto.LoginRequest
import com.project.vault.api.dto.SignupRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

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
}

