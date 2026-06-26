package com.example.exelgramm.core

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PasswordUtils {

    /**
     * Текущее число итераций PBKDF2. Хранится внутри строки хэша (`iter:hex`),
     * поэтому значение можно повышать без блокировки уже зарегистрированных
     * пользователей — старые хэши проверяются своим числом итераций, а при
     * успешном входе перехэшируются на актуальное (см. [needsRehash]).
     */
    private const val DEFAULT_ITERATIONS = 210_000

    /** Число итераций у легаси-хэшей без префикса (первая версия PBKDF2). */
    private const val LEGACY_ITERATIONS = 65_536

    private const val KEY_LENGTH = 256
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val SEPARATOR = ":"

    /** Генерирует случайную соль (32 hex-символа = 16 байт). */
    fun generateSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.toHex()
    }

    /** PBKDF2-HMAC-SHA256 хэш с солью; формат `<iterations>:<hex>`. */
    fun hash(password: String, salt: String): String =
        DEFAULT_ITERATIONS.toString() + SEPARATOR + derive(password, salt, DEFAULT_ITERATIONS)

    /**
     * Проверяет пароль против хэша. Поддерживает как новый формат `iter:hex`,
     * так и легаси-хэш без префикса (трактуется как [LEGACY_ITERATIONS]).
     * Использует [MessageDigest.isEqual] для constant-time сравнения.
     */
    fun verify(password: String, hash: String, salt: String): Boolean {
        val (iterations, expectedHex) = parse(hash)
        val computed = derive(password, salt, iterations).toByteArray(Charsets.UTF_8)
        val expected = expectedHex.toByteArray(Charsets.UTF_8)
        return MessageDigest.isEqual(computed, expected)
    }

    /** true, если хэш создан с числом итераций ниже актуального и его стоит обновить. */
    fun needsRehash(hash: String): Boolean = parse(hash).first < DEFAULT_ITERATIONS

    /**
     * Legacy SHA-256 хэш без соли (использовался до PBKDF2).
     * Нужен для миграции существующих аккаунтов.
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
