package com.cosyra.app.network

import java.security.SecureRandom

object SessionCode {
    private val random = SecureRandom()

    fun generate(): String = random.nextInt(1_000_000).toString().padStart(6, '0')

    fun isValid(value: String): Boolean =
        value.length == 6 && value.all(Char::isDigit)
}
