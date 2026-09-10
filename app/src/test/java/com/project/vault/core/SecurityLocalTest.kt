package com.project.vault.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigInteger
import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAKeyGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec

/**
 * Local (JVM) unit tests for the hybrid AES-256-GCM + RSA-OAEP encryption logic
 * implemented in CryptoManager.encryptWithPublicKey.
 *
 * CryptoManager itself cannot be instantiated in unit tests (it references the
 * Android Keystore at class-load time), so these tests replicate the same algorithm
 * inline and verify the wire format and round-trip correctness using pure JCE.
 *
 * Wire format produced by encryptWithPublicKey:
 *   Base64( [2-byte big-endian encKeyLen] | [encryptedAesKey] | [iv 12B] | [aesCiphertext] )
 */
class SecurityLocalTest {

    // ── Ephemeral RSA-2048 key pair ───────────────────────────────────────────

    private lateinit var publicKeyBase64: String
    private lateinit var privateKeyEncoded: ByteArray

    @Before
    fun setup() {
        val kpg = KeyPairGenerator.getInstance("RSA").apply {
            initialize(RSAKeyGenParameterSpec(2048, BigInteger.valueOf(65537)))
        }
        val pair = kpg.generateKeyPair()
        publicKeyBase64   = Base64.getEncoder().encodeToString(pair.public.encoded)
        privateKeyEncoded = pair.private.encoded
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private val oaepSpec = OAEPParameterSpec(
        "SHA-256", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT
    )

    /**
     * Pure-JCE hybrid encrypt — mirrors CryptoManager.encryptWithPublicKey exactly,
     * but uses java.util.Base64 instead of android.util.Base64.
     */
    private fun encryptHybrid(plaintext: String, pubKeyBase64: String): String {
        val keyBytes = Base64.getDecoder().decode(pubKeyBase64)
        val pubKey: PublicKey = KeyFactory.getInstance("RSA")
            .generatePublic(X509EncodedKeySpec(keyBytes))

        val aesKey = KeyGenerator.getInstance("AES").apply {
            init(256, SecureRandom())
        }.generateKey()

        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val aesCipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, aesKey, GCMParameterSpec(128, iv))
        }
        val aesCiphertext = aesCipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        val rsaCipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
        try { rsaCipher.init(Cipher.ENCRYPT_MODE, pubKey, oaepSpec) }
        catch (_: Exception) { rsaCipher.init(Cipher.ENCRYPT_MODE, pubKey) }
        val encryptedAesKey = rsaCipher.doFinal(aesKey.encoded)

        val packed = ByteBuffer.allocate(2 + encryptedAesKey.size + 12 + aesCiphertext.size).apply {
            putShort(encryptedAesKey.size.toShort())
            put(encryptedAesKey)
            put(iv)
            put(aesCiphertext)
        }.array()

        return Base64.getEncoder().encodeToString(packed)
    }

    /** Decrypts a blob produced by encryptHybrid using the test private key. */
    private fun decryptHybrid(blob: String): String {
        val bytes  = Base64.getDecoder().decode(blob)
        val buf    = ByteBuffer.wrap(bytes)
        val keyLen = buf.short.toInt() and 0xFFFF
        val encKey = ByteArray(keyLen).also { buf.get(it) }
        val iv     = ByteArray(12).also { buf.get(it) }
        val ciphertext = ByteArray(buf.remaining()).also { buf.get(it) }

        val privKey = KeyFactory.getInstance("RSA")
            .generatePrivate(PKCS8EncodedKeySpec(privateKeyEncoded))
        val rsaCipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
        try { rsaCipher.init(Cipher.DECRYPT_MODE, privKey, oaepSpec) }
        catch (_: Exception) { rsaCipher.init(Cipher.DECRYPT_MODE, privKey) }
        val aesKeyBytes = rsaCipher.doFinal(encKey)

        val aesCipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(aesKeyBytes, "AES"), GCMParameterSpec(128, iv))
        }
        return String(aesCipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `hybrid encrypt round-trips short credential JSON`() {
        val plaintext = """{"title":"Gmail","username":"alice@gmail.com","password":"s3cret!"}"""
        val encrypted = encryptHybrid(plaintext, publicKeyBase64)
        val decrypted = decryptHybrid(encrypted)
        assertEquals("Round-trip failed for short plaintext", plaintext, decrypted)
    }

    @Test
    fun `hybrid encrypt round-trips payload larger than direct RSA limit`() {
        // RSA-2048/OAEP/SHA-256 direct limit ~190 bytes; this is 300 — tests hybrid path
        val plaintext = "A".repeat(300)
        val encrypted = encryptHybrid(plaintext, publicKeyBase64)
        val decrypted = decryptHybrid(encrypted)
        assertEquals("Round-trip failed for large plaintext", plaintext, decrypted)
    }

    @Test
    fun `hybrid encrypt produces different ciphertext for same plaintext`() {
        val plaintext = "same input"
        val enc1 = encryptHybrid(plaintext, publicKeyBase64)
        val enc2 = encryptHybrid(plaintext, publicKeyBase64)
        // Fresh AES key + IV each call — ciphertexts must differ
        assertNotEquals("Ciphertexts must be non-deterministic (fresh IV each call)", enc1, enc2)
    }

    @Test
    fun `hybrid encrypt output is valid Base64`() {
        val encrypted = encryptHybrid("test payload", publicKeyBase64)
        val decoded = Base64.getDecoder().decode(encrypted)
        assertTrue("Decoded blob should be non-empty", decoded.isNotEmpty())
    }

    @Test
    fun testBase64Logic() {
        val original = "Test String"
        val encoded  = Base64.getEncoder().encodeToString(original.toByteArray())
        val decoded  = String(Base64.getDecoder().decode(encoded))
        assertEquals(original, decoded)
    }
}

// ── BiometricAuthManager session-timing tests ─────────────────────────────────
// These replicate the isSessionValid() logic from BiometricAuthManager using
// plain Long arithmetic — no Android framework dependency needed.

private const val SESSION_MS = 10_000L // mirrors BiometricAuthManager.SESSION_DURATION_MS

private fun isSessionValid(lastAuthTimestamp: Long, nowMs: Long): Boolean =
    lastAuthTimestamp != 0L && (nowMs - lastAuthTimestamp) < SESSION_MS

class BiometricSessionTest {

    @Test
    fun `session is valid when auth happened 5 seconds ago`() {
        val now  = 100_000L
        val last = now - 5_000L   // 5 s ago — within 10 s window
        assertTrue("Session should be valid within 10 s", isSessionValid(last, now))
    }

    @Test
    fun `session is expired when auth happened 11 seconds ago`() {
        val now  = 100_000L
        val last = now - 11_000L  // 11 s ago — past the 10 s window
        assertTrue("Session should be expired after 10 s", !isSessionValid(last, now))
    }

    @Test
    fun `session is invalid when auth has never occurred`() {
        val last = 0L             // default — no auth yet
        assertTrue("Uninitialized timestamp should be treated as expired", !isSessionValid(last, 50_000L))
    }

    @Test
    fun `session expires exactly at the boundary`() {
        val now  = 100_000L
        val last = now - SESSION_MS   // exactly 10 s ago — not strictly less than, so expired
        assertTrue("Session at exactly 10 s should be expired", !isSessionValid(last, now))
    }

    @Test
    fun `session is valid immediately after auth`() {
        val now  = 100_000L
        val last = now             // auth just happened (0 ms ago)
        assertTrue("Session should be valid immediately after auth", isSessionValid(last, now))
    }
}
