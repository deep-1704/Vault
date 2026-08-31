package com.project.vault.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import javax.crypto.Cipher

/**
 * Manages asymmetric RSA-2048 key pairs using the Android Keystore system.
 *
 * Key storage:
 *   - The key pair is generated once and stored permanently in the Android Keystore
 *     under [KEY_ALIAS]. No file, SharedPreferences, or any other storage is used.
 *   - The private key never leaves secure hardware (TEE / StrongBox where available)
 *     and cannot be exported.
 *   - The key pair survives reboots and app updates, but is wiped on uninstall /
 *     "Clear Data" / factory reset.
 *
 * Usage:
 *   val crypto = CryptoManager()
 *   val ciphertext = crypto.encrypt("sensitive data")   // Base64 string
 *   val plaintext  = crypto.decrypt(ciphertext)         // original string
 */
class CryptoManager {

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
        load(null)
    }

    // ── Key lifecycle ────────────────────────────────────────────────────────

    /**
     * Ensures the RSA key pair exists in the Keystore.
     * Generates a new pair only if none is found under [KEY_ALIAS]. Thread-safe
     * because Android Keystore operations are internally serialised.
     *
     * Call this once at app startup (e.g. from [VaultApplication] or the first
     * ViewModel that needs crypto). Subsequent calls are instant no-ops.
     */
    fun ensureKeyPair() {
        if (keyStore.containsAlias(KEY_ALIAS)) return

        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setAlgorithmParameterSpec(java.security.spec.RSAKeyGenParameterSpec(KEY_SIZE, java.math.BigInteger.valueOf(65537)))
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
            .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA1)
            .build()

        KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_RSA,
            ANDROID_KEYSTORE
        ).apply {
            initialize(spec)
            generateKeyPair()
        }
    }

    // ── Encrypt / Decrypt ────────────────────────────────────────────────────

    /**
     * Encrypts [plaintext] with the RSA public key (OAEP / SHA-256).
     *
     * @return Base64-encoded ciphertext (URL-safe, no line wraps).
     * @throws IllegalStateException if the key pair has not been generated yet.
     */
    fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, publicKey())
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    /**
     * Decrypts a Base64-encoded ciphertext produced by [encrypt] using the RSA
     * private key stored in secure hardware.
     *
     * @return The original plaintext string.
     * @throws IllegalStateException if the key pair has not been generated yet.
     */
    fun decrypt(ciphertext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, privateKey())
        val decoded = Base64.decode(ciphertext, Base64.NO_WRAP)
        return String(cipher.doFinal(decoded), Charsets.UTF_8)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun publicKey(): PublicKey {
        ensureKeyPair()
        val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.PrivateKeyEntry
            ?: throw IllegalStateException("RSA key pair not found in Keystore (alias: $KEY_ALIAS)")
        return entry.certificate.publicKey
    }
    private fun privateKey(): PrivateKey {
        ensureKeyPair()
        val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.PrivateKeyEntry
            ?: throw IllegalStateException("RSA key pair not found in Keystore (alias: $KEY_ALIAS)")
        return entry.privateKey
    }

    // ── Constants ────────────────────────────────────────────────────────────

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"

        /** Stable alias for the single app-wide RSA key pair. */
        const val KEY_ALIAS = "vault_keypair"

        private const val KEY_SIZE = 2048

        /**
         * RSA / OAEP with SHA-256 digest and MGF1 (SHA-1) mask generation.
         * Supported on Android Keystore from API 23+; compatible with API 18+ for
         * pure-JCE operations outside the Keystore.
         */
        private const val TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"
    }
}
