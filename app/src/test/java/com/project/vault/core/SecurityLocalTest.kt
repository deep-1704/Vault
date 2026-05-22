package com.project.vault.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Base64

class SecurityLocalTest {

    @Test
    fun testBase64Logic() {
        val original = "Test String"
        val encoded = Base64.getEncoder().encodeToString(original.toByteArray())
        val decoded = String(Base64.getDecoder().decode(encoded))
        assertEquals(original, decoded)
    }
    
    // Note: We can't easily test Security methods here because they depend on AndroidKeyStore
    // which is only available in instrumented tests or with heavy mocking.
}
