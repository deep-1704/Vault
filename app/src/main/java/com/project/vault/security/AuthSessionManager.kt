package com.project.vault.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Credentials
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages persistent user credentials and session authentication state using
 * [EncryptedSharedPreferences] backed by the Android Keystore.
 */
@Singleton
class AuthSessionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val prefs: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private val _isLoggedIn = MutableStateFlow(hasValidSession())
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _currentUsername = MutableStateFlow(getUsername())
    val currentUsername: StateFlow<String?> = _currentUsername.asStateFlow()

    fun hasValidSession(): Boolean {
        return !getUsername().isNullOrBlank() && !getPassword().isNullOrBlank()
    }

    fun getUsername(): String? = prefs.getString(KEY_USERNAME, null)

    fun getPassword(): String? = prefs.getString(KEY_PASSWORD, null)

    fun getDeviceId(): String? = prefs.getString(KEY_DEVICE_ID, null)

    fun getBasicAuthHeader(): String? {
        val user = getUsername()
        val pass = getPassword()
        return if (!user.isNullOrBlank() && !pass.isNullOrBlank()) {
            Credentials.basic(user, pass)
        } else {
            null
        }
    }

    fun saveSession(username: String, password: String, deviceId: String) {
        prefs.edit()
            .putString(KEY_USERNAME, username)
            .putString(KEY_PASSWORD, password)
            .putString(KEY_DEVICE_ID, deviceId)
            .apply()

        _isLoggedIn.value = true
        _currentUsername.value = username
    }

    fun clearSession() {
        prefs.edit()
            .remove(KEY_USERNAME)
            .remove(KEY_PASSWORD)
            .remove(KEY_DEVICE_ID)
            .apply()

        _isLoggedIn.value = false
        _currentUsername.value = null
    }

    companion object {
        private const val PREFS_NAME = "vault_secure_auth_prefs"
        private const val KEY_USERNAME = "auth_username"
        private const val KEY_PASSWORD = "auth_password"
        private const val KEY_DEVICE_ID = "auth_device_id"
    }
}
