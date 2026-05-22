package com.project.vault.core

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class Security {

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val AES_MODE = "AES/GCM/NoPadding"
        private const val IV_SIZE = 12 // GCM recommended IV size
        private const val TAG_SIZE = 128 // GCM authentication tag size

        /**
         * Generates an AES key and stores it in the Android KeyStore.
         *
         * @param alias The alias for the key.
         */
        @JvmStatic
        fun generateKey(alias: String) {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true) // Important for security
                .build()

            keyGenerator.init(keyGenParameterSpec)
            keyGenerator.generateKey()
        }

        /**
         * Encrypts the provided text using the key associated with the alias.
         *
         * @param text The text to encrypt.
         * @param alias The alias of the key to use.
         * @return The Base64 encoded encrypted string (IV + Ciphertext).
         */
        @JvmStatic
        fun encryptText(text: String, alias: String): String {
            val cipher = Cipher.getInstance(AES_MODE)
            cipher.init(Cipher.ENCRYPT_MODE, getSecretKey(alias))

            val iv = cipher.iv
            val encryptedBytes = cipher.doFinal(text.toByteArray(Charsets.UTF_8))

            // Combine IV and Ciphertext
            val combined = ByteArray(iv.size + encryptedBytes.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encryptedBytes, 0, combined, iv.size, encryptedBytes.size)

            return Base64.encodeToString(combined, Base64.DEFAULT)
        }

        /**
         * Decrypts the provided Base64 encoded text using the key associated with the alias.
         *
         * @param encryptedText The Base64 encoded encrypted string (IV + Ciphertext).
         * @param alias The alias of the key to use.
         * @return The decrypted plain text.
         * @throws Exception if decryption fails or key is not found.
         */
        @JvmStatic
        fun decryptText(encryptedText: String, alias: String): String {
            val combined = Base64.decode(encryptedText, Base64.DEFAULT)
            if (combined.size < IV_SIZE) {
                throw IllegalArgumentException("Invalid encrypted text")
            }

            val iv = combined.sliceArray(0 until IV_SIZE)
            val encryptedBytes = combined.sliceArray(IV_SIZE until combined.size)

            val cipher = Cipher.getInstance(AES_MODE)
            val spec = GCMParameterSpec(TAG_SIZE, iv)
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(alias), spec)

            val decryptedBytes = cipher.doFinal(encryptedBytes)
            return String(decryptedBytes, Charsets.UTF_8)
        }

        private fun getSecretKey(alias: String): SecretKey {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            return (keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.secretKey
                ?: throw IllegalStateException("Key not found for alias: $alias")
        }
    }
}
