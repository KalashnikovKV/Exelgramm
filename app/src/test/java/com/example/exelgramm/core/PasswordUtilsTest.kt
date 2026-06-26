package com.example.exelgramm.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordUtilsTest {

    @Test
    fun `sha256 produces consistent hash for same input`() {
        assertEquals(PasswordUtils.sha256("password"), PasswordUtils.sha256("password"))
    }

    @Test
    fun `sha256 different inputs produce different hashes`() {
        assertNotEquals(PasswordUtils.sha256("pass1"), PasswordUtils.sha256("pass2"))
    }

    @Test
    fun `sha256 returns 64-char hex string`() {
        val hash = PasswordUtils.sha256("test")
        assertEquals(64, hash.length)
        assertTrue(hash.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun `generateSalt returns 32-char hex string`() {
        val salt = PasswordUtils.generateSalt()
        assertEquals(32, salt.length)
        assertTrue(salt.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun `generateSalt produces different values each call`() {
        assertNotEquals(PasswordUtils.generateSalt(), PasswordUtils.generateSalt())
    }

    @Test
    fun `PBKDF2 hash and verify succeed for correct password`() {
        val salt = PasswordUtils.generateSalt()
        val hash = PasswordUtils.hash("correctPassword", salt)
        assertTrue(PasswordUtils.verify("correctPassword", hash, salt))
    }

    @Test
    fun `PBKDF2 verify fails for wrong password`() {
        val salt = PasswordUtils.generateSalt()
        val hash = PasswordUtils.hash("correctPassword", salt)
        assertFalse(PasswordUtils.verify("wrongPassword", hash, salt))
    }

    @Test
    fun `same password with different salts produces different hashes`() {
        val salt1 = PasswordUtils.generateSalt()
        val salt2 = PasswordUtils.generateSalt()
        assertNotEquals(
            PasswordUtils.hash("same", salt1),
            PasswordUtils.hash("same", salt2),
        )
    }

    @Test
    fun `hash embeds iteration count as prefix`() {
        val hash = PasswordUtils.hash("pw", PasswordUtils.generateSalt())
        assertTrue("Expected 'iter:hex' format but got: $hash", hash.matches(Regex("""\d+:[0-9a-f]+""")))
    }

    @Test
    fun `current hash does not need rehash`() {
        assertFalse(PasswordUtils.needsRehash(PasswordUtils.hash("pw", PasswordUtils.generateSalt())))
    }

    @Test
    fun `legacy unprefixed PBKDF2 hash verifies and is flagged for rehash`() {
        val salt = PasswordUtils.generateSalt()
        // Simulates legacy hash (65,536 iterations) without an iteration prefix.
        val legacyHex = legacyPbkdf2Hex("legacyPass", salt)
        assertTrue(PasswordUtils.verify("legacyPass", legacyHex, salt))
        assertTrue(PasswordUtils.needsRehash(legacyHex))
    }

    private fun legacyPbkdf2Hex(password: String, salt: String): String {
        val saltBytes = salt.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val spec = javax.crypto.spec.PBEKeySpec(password.toCharArray(), saltBytes, 65_536, 256)
        val key = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec)
        return key.encoded.joinToString("") { "%02x".format(it) }
    }
}
