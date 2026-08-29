package com.project.vault.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Manages cryptographic keys using the Android Keystore system.
 *
 * Keys generated here never leave the secure hardware (where available)
 * and are tied to this application's process.
 *
 * Usage:
 *   val manager = KeystoreManager()
 *   manager.generateKey(alias = "vault_master_key")
 *   val key = manager.getKey("vault_master_key")
 */
class KeystoreManager {

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
        load(null)
    }

    /**
     * Generates a new AES-256-GCM key and stores it in the Android Keystore
     * under the given [alias]. If a key with this alias already exists it is
     * returned without regenerating.
     *
     * @param alias Unique identifier for the key within the Keystore.
     */
    fun generateKey(alias: String = DEFAULT_KEY_ALIAS): SecretKey {
        if (keyStore.containsAlias(alias)) {
            return getKey(alias)
                ?: throw IllegalStateException("Key exists under alias '$alias' but could not be retrieved.")
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    /**
     * Retrieves an existing key from the Android Keystore by [alias].
     *
     * @return The [SecretKey] or null if no key exists for this alias.
     */
    fun getKey(alias: String = DEFAULT_KEY_ALIAS): SecretKey? {
        if (!keyStore.containsAlias(alias)) return null
        val entry = keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry
        return entry?.secretKey
    }

    /**
     * Deletes the key associated with [alias] from the Android Keystore.
     * No-op if the alias does not exist.
     */
    fun deleteKey(alias: String = DEFAULT_KEY_ALIAS) {
        if (keyStore.containsAlias(alias)) {
            keyStore.deleteEntry(alias)
        }
    }

    /**
     * Returns true if a key for [alias] already exists in the Keystore.
     */
    fun hasKey(alias: String = DEFAULT_KEY_ALIAS): Boolean {
        return keyStore.containsAlias(alias)
    }

    companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val DEFAULT_KEY_ALIAS = "vault_master_key"
    }
}
