package com.example.exelgramm.core

import java.security.MessageDigest

object PasswordUtils {
    fun hash(password: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest
            .digest(password.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
