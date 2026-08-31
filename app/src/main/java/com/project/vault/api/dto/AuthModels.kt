package com.project.vault.api.dto

import com.google.gson.annotations.SerializedName

/**
 * Payload for POST /auth/signup.
 */
data class SignupRequest(
    @SerializedName("user")
    val user: UserCredentialsDto,
    @SerializedName("device")
    val device: DeviceRegistrationDto
)

/**
 * User credentials component for signup.
 */
data class UserCredentialsDto(
    @SerializedName("username")
    val username: String,
    @SerializedName("password")
    val password: String
)

/**
 * Device registration component for signup.
 */
data class DeviceRegistrationDto(
    @SerializedName("id")
    val id: String,
    @SerializedName("public_key")
    val publicKey: String
)

/**
 * Payload for POST /auth/login.
 */
data class LoginRequest(
    @SerializedName("id")
    val id: String,
    @SerializedName("public_key")
    val publicKey: String
)
