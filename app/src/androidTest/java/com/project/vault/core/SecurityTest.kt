package com.project.vault.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecurityTest {

    @Test
    fun testEncryptionDecryption() {
        val alias = "test_key_alias"
        val originalText = "Hello, KeyStore!"

        Security.generateKey(alias)
        val encryptedText = Security.encryptText(originalText, alias)

        assertNotEquals(originalText, encryptedText)

        val decryptedText = Security.decryptText(encryptedText, alias)
        assertEquals(originalText, decryptedText)
    }

    @Test
    fun testDifferentAliases() {
        val alias1 = "alias_1"
        val alias2 = "alias_2"
        val text = "Confidential Data"

        Security.generateKey(alias1)
        Security.generateKey(alias2)

        val encryptedWith1 = Security.encryptText(text, alias1)

        // Decrypting with wrong alias should fail or produce wrong result
        // In GCM, it will likely throw AEADBadTagException during doFinal
        try {
            Security.decryptText(encryptedWith1, alias2)
            fail("Should have thrown an exception when decrypting with wrong key")
        } catch (e: Exception) {
            // Success - exception expected
        }
    }

    @Test(expected = IllegalStateException::class)
    fun testMissingAlias() {
        val nonExistentAlias = "missing_alias"
        Security.encryptText("some text", nonExistentAlias)
    }
}
