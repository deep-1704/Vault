package com.project.vault.repository

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import com.project.vault.api.ApiService
import com.project.vault.api.dto.DeviceRegistrationDto
import com.project.vault.api.dto.LoginRequest
import com.project.vault.api.dto.SignupRequest
import com.project.vault.api.dto.UserCredentialsDto
import com.project.vault.security.AuthSessionManager
import com.project.vault.security.CryptoManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import okhttp3.Credentials
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

sealed class AuthException(message: String) : Exception(message) {
    class UserAlreadyExistsException(message: String = "Username already exists") : AuthException(message)
    class InvalidCredentialsException(message: String = "Invalid username or password") : AuthException(message)
    class DeviceRegistrationForbiddenException(
        message: String = "Authentication failed: This device may already be registered to another account or access is forbidden."
    ) : AuthException(message)
    class ApiException(message: String) : AuthException(message)
}

/**
 * Repository responsible for user registration, authentication, and session persistence.
 */
@Singleton
class AuthRepository @Inject constructor(
    private val apiService: ApiService,
    private val cryptoManager: CryptoManager,
    private val authSessionManager: AuthSessionManager,
    @ApplicationContext private val context: Context
) {

    val isLoggedIn: StateFlow<Boolean> = authSessionManager.isLoggedIn
    val currentUsername: StateFlow<String?> = authSessionManager.currentUsername

    /**
     * Retrieves the stable Android device ID or falls back to a generated UUID.
     */
    @SuppressLint("HardwareIds")
    fun getDeviceId(): String {
        return Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: UUID.randomUUID().toString()
    }

    /**
     * Registers a new user account and registers the local device's RSA public key.
     */
    suspend fun signup(username: String, password: String): Result<Unit> {
        return try {
            val deviceId = getDeviceId()
            val publicKey = cryptoManager.getPublicKeyBase64()

            val request = SignupRequest(
                user = UserCredentialsDto(username = username, password = password),
                device = DeviceRegistrationDto(id = deviceId, publicKey = publicKey)
            )

            val response = apiService.signup(request)

            when {
                response.isSuccessful || response.code() == 201 -> {
                    authSessionManager.saveSession(username, password, deviceId)
                    Result.success(Unit)
                }
                response.code() == 403 -> {
                    Result.failure(AuthException.DeviceRegistrationForbiddenException())
                }
                response.code() == 409 -> {
                    Result.failure(AuthException.UserAlreadyExistsException())
                }
                else -> {
                    val errorBody = response.errorBody()?.string()?.takeIf { it.isNotBlank() }
                    val message = errorBody ?: "Sign up failed (HTTP ${response.code()})"
                    Result.failure(AuthException.ApiException(message))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Authenticates an existing user via HTTP Basic Auth and registers the active device.
     */
    suspend fun login(username: String, password: String): Result<Unit> {
        return try {
            val deviceId = getDeviceId()
            val publicKey = cryptoManager.getPublicKeyBase64()
            val basicAuthHeader = Credentials.basic(username, password)

            val request = LoginRequest(
                id = deviceId,
                publicKey = publicKey
            )

            val response = apiService.login(basicAuth = basicAuthHeader, request = request)

            when {
                response.isSuccessful || response.code() == 201 -> {
                    authSessionManager.saveSession(username, password, deviceId)
                    Result.success(Unit)
                }
                response.code() == 401 -> {
                    Result.failure(AuthException.InvalidCredentialsException())
                }
                response.code() == 403 -> {
                    Result.failure(AuthException.DeviceRegistrationForbiddenException())
                }
                else -> {
                    val errorBody = response.errorBody()?.string()?.takeIf { it.isNotBlank() }
                    val message = errorBody ?: "Login failed (HTTP ${response.code()})"
                    Result.failure(AuthException.ApiException(message))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Clears local credentials and session state.
     */
    fun logout() {
        authSessionManager.clearSession()
    }
}
