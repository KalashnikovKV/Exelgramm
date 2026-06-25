package com.example.exelgramm.core

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PasswordUtils {

    private const val ITERATIONS = 65_536
    private const val KEY_LENGTH = 256
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"

    /** Генерирует случайную соль (32 hex-символа = 16 байт). */
    fun generateSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /** PBKDF2-HMAC-SHA256 хэш с солью. */
    fun hash(password: String, salt: String): String {
        val saltBytes = salt.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val spec = PBEKeySpec(password.toCharArray(), saltBytes, ITERATIONS, KEY_LENGTH)
        val key = SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec)
        spec.clearPassword()
        return key.encoded.joinToString("") { "%02x".format(it) }
    }

    /**
     * Проверяет пароль против PBKDF2-хэша.
     * Использует [MessageDigest.isEqual] для constant-time сравнения,
     * чтобы исключить timing side-channel через короткозамыкание `==`.
     */
    fun verify(password: String, hash: String, salt: String): Boolean {
        val computed = hash(password, salt).toByteArray(Charsets.UTF_8)
        val expected = hash.toByteArray(Charsets.UTF_8)
        return MessageDigest.isEqual(computed, expected)
    }

    /**
     * Legacy SHA-256 хэш без соли (использовался до PBKDF2).
     * Нужен для миграции существующих аккаунтов.
     */
    fun sha256(password: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest
            .digest(password.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
