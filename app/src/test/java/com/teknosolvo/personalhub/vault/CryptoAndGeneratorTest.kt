package com.teknosolvo.personalhub.vault

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.AEADBadTagException

class CryptoAndGeneratorTest {

    @Test fun `aes gcm round trip`() {
        val key = CryptoBox.keyOf(CryptoBox.newDataKey())
        val blob = CryptoBox.encrypt(key, "rahasia".toByteArray(), "aad".toByteArray())
        assertEquals("rahasia", CryptoBox.decrypt(key, blob, "aad".toByteArray()).decodeToString())
        assertEquals(12 + 7 + 16, blob.size)
    }

    @Test fun `iv is random per message`() {
        val key = CryptoBox.keyOf(CryptoBox.newDataKey())
        val a = CryptoBox.encrypt(key, "x".toByteArray())
        val b = CryptoBox.encrypt(key, "x".toByteArray())
        assertFalse(a.contentEquals(b))
    }

    @Test(expected = AEADBadTagException::class)
    fun `wrong key is rejected`() {
        val blob = CryptoBox.encrypt(CryptoBox.keyOf(CryptoBox.newDataKey()), "x".toByteArray())
        CryptoBox.decrypt(CryptoBox.keyOf(CryptoBox.newDataKey()), blob)
    }

    @Test(expected = AEADBadTagException::class)
    fun `tampered ciphertext is rejected`() {
        val key = CryptoBox.keyOf(CryptoBox.newDataKey())
        val blob = CryptoBox.encrypt(key, "transfer 100".toByteArray())
        blob[blob.size - 20] = (blob[blob.size - 20].toInt() xor 1).toByte()
        CryptoBox.decrypt(key, blob)
    }

    @Test(expected = AEADBadTagException::class)
    fun `wrong aad is rejected`() {
        val key = CryptoBox.keyOf(CryptoBox.newDataKey())
        CryptoBox.decrypt(key, CryptoBox.encrypt(key, "x".toByteArray(), "a".toByteArray()), "b".toByteArray())
    }

    @Test fun `pbkdf2 is deterministic per salt`() {
        val salt = CryptoBox.randomBytes(16)
        val k1 = CryptoBox.deriveKey("123456".toCharArray(), salt, 1000).encoded
        val k2 = CryptoBox.deriveKey("123456".toCharArray(), salt, 1000).encoded
        val k3 = CryptoBox.deriveKey("123457".toCharArray(), salt, 1000).encoded
        assertArrayEquals(k1, k2)
        assertFalse(k1.contentEquals(k3))
        assertEquals(32, k1.size)
    }

    @Test fun `generator honours length and character classes`() {
        repeat(200) {
            val p = PasswordGenerator.generate(GeneratorOptions(length = 16))
            assertEquals(16, p.length)
            assertTrue(p.any { it.isUpperCase() })
            assertTrue(p.any { it.isLowerCase() })
            assertTrue(p.any { it.isDigit() })
            assertTrue(p.any { it in PasswordGenerator.SYMBOLS })
        }
        val digitsOnly = PasswordGenerator.generate(GeneratorOptions(length = 10, upper = false, lower = false, symbols = false))
        assertTrue(digitsOnly.all { it.isDigit() })
        val noAmbiguous = (1..100).joinToString("") { PasswordGenerator.generate(GeneratorOptions(avoidAmbiguous = true)) }
        assertTrue(noAmbiguous.none { it in "Il1O0o" })
    }

    @Test fun `generator output is unique`() {
        val set = (1..1000).map { PasswordGenerator.generate(GeneratorOptions(length = 20)) }.toSet()
        assertEquals(1000, set.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `generator requires a character class`() {
        PasswordGenerator.generate(GeneratorOptions(upper = false, lower = false, digits = false, symbols = false))
    }

    @Test fun `strength estimate`() {
        assertEquals(Strength.VERY_WEAK, PasswordGenerator.strength("1234"))
        assertNotEquals(Strength.VERY_WEAK, PasswordGenerator.strength("Tr0ub4dor&3xyz"))
        assertEquals(Strength.VERY_STRONG, PasswordGenerator.strength(PasswordGenerator.generate(GeneratorOptions(length = 24))))
    }
}
