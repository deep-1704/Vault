package com.project.vault.auth

import com.google.gson.Gson
import com.project.vault.api.dto.DeviceRegistrationDto
import com.project.vault.api.dto.LoginRequest
import com.project.vault.api.dto.SignupRequest
import com.project.vault.api.dto.UserCredentialsDto
import okhttp3.Credentials
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthTest {

    private val gson = Gson()

    @Test
    fun testSignupRequestJsonSerialization() {
        val request = SignupRequest(
            user = UserCredentialsDto("alice", "securePassword123"),
            device = DeviceRegistrationDto("device-uuid-001", "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8A...")
        )

        val json = gson.toJson(request)
        assertTrue(json.contains("\"username\":\"alice\""))
        assertTrue(json.contains("\"password\":\"securePassword123\""))
        assertTrue(json.contains("\"id\":\"device-uuid-001\""))
        assertTrue(json.contains("\"public_key\":\"MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8A...\""))

        val deserialized = gson.fromJson(json, SignupRequest::class.java)
        assertEquals("alice", deserialized.user.username)
        assertEquals("securePassword123", deserialized.user.password)
        assertEquals("device-uuid-001", deserialized.device.id)
        assertEquals("MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8A...", deserialized.device.publicKey)
    }

    @Test
    fun testLoginRequestJsonSerialization() {
        val request = LoginRequest(
            id = "device-uuid-002",
            publicKey = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8A..."
        )

        val json = gson.toJson(request)
        assertTrue(json.contains("\"id\":\"device-uuid-002\""))
        assertTrue(json.contains("\"public_key\":\"MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8A...\""))

        val deserialized = gson.fromJson(json, LoginRequest::class.java)
        assertEquals("device-uuid-002", deserialized.id)
        assertEquals("MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8A...", deserialized.publicKey)
    }

    @Test
    fun testBasicAuthHeaderGeneration() {
        val header = Credentials.basic("alice", "securePassword123")
        assertNotNull(header)
        assertTrue(header.startsWith("Basic "))
    }

    @Test
    fun testDeviceRegistrationForbiddenExceptionDefaultMessage() {
        val ex = com.project.vault.repository.AuthException.DeviceRegistrationForbiddenException()
        assertEquals(
            "Authentication failed: This device may already be registered to another account or access is forbidden.",
            ex.message
        )
        assertTrue(ex is com.project.vault.repository.AuthException)
    }

    @Test
    fun testDeviceRegistrationForbiddenExceptionCustomMessage() {
        val customMsg = "Custom forbidden error"
        val ex = com.project.vault.repository.AuthException.DeviceRegistrationForbiddenException(customMsg)
        assertEquals(customMsg, ex.message)
    }

    @Test
    fun testAuthExceptionResolutionForDeviceForbidden() {
        val ex: Throwable = com.project.vault.repository.AuthException.DeviceRegistrationForbiddenException()
        val userFacingMessage = when (ex) {
            is com.project.vault.repository.AuthException.InvalidCredentialsException -> ex.message ?: "Invalid username or password"
            is com.project.vault.repository.AuthException.DeviceRegistrationForbiddenException -> ex.message ?: "Authentication failed: This device may already be registered to another account or access is forbidden."
            is com.project.vault.repository.AuthException.ApiException -> ex.message ?: "Authentication failed"
            else -> ex.localizedMessage ?: "Network error occurred"
        }
        assertEquals(
            "Authentication failed: This device may already be registered to another account or access is forbidden.",
            userFacingMessage
        )
    }
}

