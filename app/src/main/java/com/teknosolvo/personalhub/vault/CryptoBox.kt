package com.teknosolvo.personalhub.vault

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Pure-JCE crypto primitives. AES-256-GCM with a random 96-bit IV per message and a 128-bit tag.
 * Blob layout: `iv (12 bytes) || ciphertext || tag (16 bytes)`.
 */
object CryptoBox {
    const val TRANSFORMATION = "AES/GCM/NoPadding"
    const val IV_LEN = 12
    const val TAG_BITS = 128
    const val KEY_BYTES = 32

    private val random = SecureRandom()

    fun randomBytes(n: Int): ByteArray = ByteArray(n).also { random.nextBytes(it) }

    fun newDataKey(): ByteArray = randomBytes(KEY_BYTES)

    fun keyOf(bytes: ByteArray): SecretKey {
        require(bytes.size == KEY_BYTES) { "AES-256 key required" }
        return SecretKeySpec(bytes, "AES")
    }

    fun encrypt(key: SecretKey, plaintext: ByteArray, aad: ByteArray? = null): ByteArray {
        val iv = randomBytes(IV_LEN)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        aad?.let { cipher.updateAAD(it) }
        return iv + cipher.doFinal(plaintext)
    }

    /** Throws [javax.crypto.AEADBadTagException] when the key is wrong or the data was tampered with. */
    fun decrypt(key: SecretKey, blob: ByteArray, aad: ByteArray? = null): ByteArray {
        require(blob.size > IV_LEN + TAG_BITS / 8) { "Ciphertext too short" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, blob, 0, IV_LEN))
        aad?.let { cipher.updateAAD(it) }
        return cipher.doFinal(blob, IV_LEN, blob.size - IV_LEN)
    }

    /** PBKDF2-HMAC-SHA256 → AES-256 key. */
    fun deriveKey(secret: CharArray, salt: ByteArray, iterations: Int): SecretKey {
        val spec = PBEKeySpec(secret, salt, iterations, KEY_BYTES * 8)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES").also { bytes.fill(0) }
        } finally {
            spec.clearPassword()
        }
    }

    fun wipe(vararg arrays: ByteArray?) = arrays.forEach { it?.fill(0) }
    fun wipe(chars: CharArray?) = chars?.fill('\u0000')
}
