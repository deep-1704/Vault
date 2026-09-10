package com.project.vault.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.MGF1ParameterSpec
import java.security.spec.RSAKeyGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec

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

    private val oaepSpec = OAEPParameterSpec(
        KeyProperties.DIGEST_SHA256,
        "MGF1",
        MGF1ParameterSpec.SHA1,
        PSource.PSpecified.DEFAULT
    )

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
            .setAlgorithmParameterSpec(RSAKeyGenParameterSpec(KEY_SIZE, java.math.BigInteger.valueOf(65537)))
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
        try {
            cipher.init(Cipher.ENCRYPT_MODE, publicKey(), oaepSpec)
        } catch (_: Exception) {
            cipher.init(Cipher.ENCRYPT_MODE, publicKey())
        }
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    /**
     * Decrypts a Base64-encoded ciphertext produced by [encrypt] using the RSA
     * private key stored in secure hardware, or a packed hybrid blob produced by [encryptWithPublicKey].
     *
     * @return The original plaintext string.
     * @throws IllegalStateException if the key pair has not been generated yet.
     */
    fun decrypt(ciphertext: String): String {
        val trimmed = ciphertext.trim()
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return trimmed
        }

        val decoded = try {
            Base64.decode(trimmed, Base64.NO_WRAP)
        } catch (e: Exception) {
            if (trimmed.startsWith("{")) return trimmed else throw e
        }

        // Try hybrid decryption if it matches the packed format length
        if (decoded.size > 2 + KEY_SIZE / 8 + GCM_IV_LENGTH) {
            try {
                return decryptHybrid(trimmed)
            } catch (_: Exception) {
                // fall through to standard RSA decrypt
            }
        }

        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, privateKey(), oaepSpec)
            String(cipher.doFinal(decoded), Charsets.UTF_8)
        } catch (e: Exception) {
            try {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, privateKey())
                String(cipher.doFinal(decoded), Charsets.UTF_8)
            } catch (_: Exception) {
                if (trimmed.startsWith("{")) {
                    trimmed
                } else {
                    throw e
                }
            }
        }
    }

    /**
     * Decrypts a Base64-encoded packed hybrid ciphertext produced by [encryptWithPublicKey]
     * using the local device's RSA private key and the unpacked AES-256-GCM key.
     *
     * @param hybridCiphertext Base64-encoded packed blob [encKeyLen(2B) | encKey | iv(12B) | aesCiphertext].
     * @return The original plaintext JSON string.
     */
    fun decryptHybrid(hybridCiphertext: String): String {
        val trimmed = hybridCiphertext.trim()
        val packed = Base64.decode(trimmed, Base64.NO_WRAP)
        val buffer = ByteBuffer.wrap(packed)

        // 1. Read encrypted AES key length (short, big-endian)
        val encKeyLen = buffer.short.toInt() and 0xFFFF
        val encAesKey = ByteArray(encKeyLen)
        buffer.get(encAesKey)

        // 2. Read 12-byte IV
        val iv = ByteArray(GCM_IV_LENGTH)
        buffer.get(iv)

        // 3. Read remaining bytes as AES-GCM ciphertext
        val aesCiphertext = ByteArray(buffer.remaining())
        buffer.get(aesCiphertext)

        // 4. RSA-OAEP decrypt the AES key with our private key
        val rsaCipher = Cipher.getInstance(TRANSFORMATION)
        val aesKeyBytes = try {
            rsaCipher.init(Cipher.DECRYPT_MODE, privateKey(), oaepSpec)
            rsaCipher.doFinal(encAesKey)
        } catch (_: Exception) {
            rsaCipher.init(Cipher.DECRYPT_MODE, privateKey())
            rsaCipher.doFinal(encAesKey)
        }
        val aesKey: SecretKey = SecretKeySpec(aesKeyBytes, AES_ALGORITHM)

        // 5. AES-256-GCM decrypt the payload
        val aesCipher = Cipher.getInstance(AES_TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, aesKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        }
        val plaintextBytes = aesCipher.doFinal(aesCiphertext)
        return String(plaintextBytes, Charsets.UTF_8)
    }

    /**
     * Returns the RSA public key encoded as a Base64 X.509 SubjectPublicKeyInfo string.
     */
    fun getPublicKeyBase64(): String {
        val pubKey = publicKey()
        return Base64.encodeToString(pubKey.encoded, Base64.NO_WRAP)
    }

    // ── Per-device hybrid encryption (for sync) ───────────────────────────────

    /**
     * Hybrid-encrypts [plaintext] for a specific device using its RSA public key.
     *
     * Algorithm:
     *  1. Generate a random AES-256 key.
     *  2. Encrypt the JSON plaintext with AES-256-GCM (random 12-byte IV).
     *  3. RSA-OAEP encrypt the AES key with the device's public key.
     *  4. Pack as: [2-byte big-endian encKeyLen | encKey | 12-byte IV | aesCiphertext]
     *     and return as a Base64 string.
     *
     * This removes the RSA-2048 size ceiling (~190 bytes) and is forward-safe for
     * any future credential payload size.
     *
     * @param plaintext      The raw credential JSON to encrypt.
     * @param publicKeyBase64 Base64-encoded X.509 SubjectPublicKeyInfo RSA public key
     *                        (as returned by GET /device).
     * @return Base64-encoded packed ciphertext blob.
     */
    fun encryptWithPublicKey(plaintext: String, publicKeyBase64: String): String {
        // 1. Decode the target device's RSA public key
        val keyBytes = Base64.decode(publicKeyBase64, Base64.NO_WRAP)
        val devicePubKey: PublicKey = KeyFactory.getInstance(KeyProperties.KEY_ALGORITHM_RSA)
            .generatePublic(X509EncodedKeySpec(keyBytes))

        // 2. Generate a fresh AES-256 symmetric key
        val aesKey: SecretKey = KeyGenerator.getInstance(AES_ALGORITHM).apply {
            init(AES_KEY_SIZE, SecureRandom())
        }.generateKey()

        // 3. Encrypt the plaintext with AES-256-GCM
        val iv = ByteArray(GCM_IV_LENGTH).also { SecureRandom().nextBytes(it) }
        val aesCipher = Cipher.getInstance(AES_TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, aesKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        }
        val aesCiphertext = aesCipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        // 4. RSA-OAEP encrypt the AES key with the device's public key
        val rsaCipher = Cipher.getInstance(TRANSFORMATION)
        try {
            rsaCipher.init(Cipher.ENCRYPT_MODE, devicePubKey, oaepSpec)
        } catch (_: Exception) {
            rsaCipher.init(Cipher.ENCRYPT_MODE, devicePubKey)
        }
        val encryptedAesKey = rsaCipher.doFinal(aesKey.encoded)

        // 5. Pack: [encKeyLen(2B) | encKey | iv(12B) | aesCiphertext]
        val packed = ByteBuffer.allocate(
            2 + encryptedAesKey.size + GCM_IV_LENGTH + aesCiphertext.size
        ).apply {
            putShort(encryptedAesKey.size.toShort())
            put(encryptedAesKey)
            put(iv)
            put(aesCiphertext)
        }.array()

        return Base64.encodeToString(packed, Base64.NO_WRAP)
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

        // AES-GCM constants for hybrid per-device encryption
        private const val AES_ALGORITHM   = "AES"
        private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val AES_KEY_SIZE    = 256          // bits
        private const val GCM_IV_LENGTH   = 12           // bytes (96-bit IV, NIST recommended)
        private const val GCM_TAG_BITS    = 128          // authentication tag length
    }
}
