package com.example.exelgramm.core

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PasswordUtils {

    /**
     * Current PBKDF2 iteration count. Stored inside the hash string (`iter:hex`),
     * so it can be raised without locking out registered users — old hashes are
     * verified with their own iteration count and rehashed on successful login
     * (see [needsRehash]).
     */
    private const val DEFAULT_ITERATIONS = 210_000

    /** Iteration count for legacy hashes without an `iter:` prefix (first PBKDF2 version). */
    private const val LEGACY_ITERATIONS = 65_536

    private const val KEY_LENGTH = 256
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val SEPARATOR = ":"

    /** Generates a random salt (32 hex chars = 16 bytes). */
    fun generateSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.toHex()
    }

    /** PBKDF2-HMAC-SHA256 hash with salt; format `<iterations>:<hex>`. */
    fun hash(password: String, salt: String): String =
        DEFAULT_ITERATIONS.toString() + SEPARATOR + derive(password, salt, DEFAULT_ITERATIONS)

    /**
     * Verifies a password against a hash. Supports the new `iter:hex` format and
     * legacy unprefixed hashes (treated as [LEGACY_ITERATIONS]).
     * Uses [MessageDigest.isEqual] for constant-time comparison.
     */
    fun verify(password: String, hash: String, salt: String): Boolean {
        val (iterations, expectedHex) = parse(hash)
        val computed = derive(password, salt, iterations).toByteArray(Charsets.UTF_8)
        val expected = expectedHex.toByteArray(Charsets.UTF_8)
        return MessageDigest.isEqual(computed, expected)
    }

    /** True if the hash uses fewer iterations than the current default and should be upgraded. */
    fun needsRehash(hash: String): Boolean = parse(hash).first < DEFAULT_ITERATIONS

    /**
     * Legacy unsalted SHA-256 hash (used before PBKDF2).
     * Kept for migrating existing accounts.
     */
    fun sha256(password: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(password.toByteArray(Charsets.UTF_8))
            .toHex()

    private fun derive(password: String, salt: String, iterations: Int): String {
        val saltBytes = salt.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val spec = PBEKeySpec(password.toCharArray(), saltBytes, iterations, KEY_LENGTH)
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded.toHex()
        } finally {
            spec.clearPassword()
        }
    }

    private fun parse(hash: String): Pair<Int, String> {
        val idx = hash.indexOf(SEPARATOR)
        if (idx <= 0) return LEGACY_ITERATIONS to hash
        val iterations = hash.substring(0, idx).toIntOrNull() ?: LEGACY_ITERATIONS
        return iterations to hash.substring(idx + 1)
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
