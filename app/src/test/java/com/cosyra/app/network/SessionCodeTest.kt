package com.cosyra.app.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionCodeTest {
    @Test
    fun generatedCodeAlwaysHasSixDigits() {
        repeat(1_000) {
            assertTrue(SessionCode.isValid(SessionCode.generate()))
        }
    }

    @Test
    fun validationRejectsInvalidCodes() {
        assertFalse(SessionCode.isValid("12345"))
        assertFalse(SessionCode.isValid("1234567"))
        assertFalse(SessionCode.isValid("12A456"))
        assertFalse(SessionCode.isValid(""))
    }
}
