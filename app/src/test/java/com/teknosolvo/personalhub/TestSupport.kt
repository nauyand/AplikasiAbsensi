package com.teknosolvo.personalhub

import com.teknosolvo.personalhub.vault.BiometricKeys
import com.teknosolvo.personalhub.vault.CryptoBox
import com.teknosolvo.personalhub.vault.KeyWrapper
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Software stand-in for the Android Keystore device key (Robolectric has no AndroidKeyStore). */
class FakeKeyWrapper : KeyWrapper {
    private val key = CryptoBox.keyOf(CryptoBox.newDataKey())
    override fun wrap(plain: ByteArray) = CryptoBox.encrypt(key, plain)
    override fun unwrap(blob: ByteArray) = CryptoBox.decrypt(key, blob)
}

/** Software stand-in for the biometric-bound Keystore key. "Authorizing" = returning the cipher. */
class FakeBiometricKeys : BiometricKeys {
    var key: SecretKey? = null
    override fun encryptCipher(): Cipher {
        key = CryptoBox.keyOf(CryptoBox.newDataKey())
        return Cipher.getInstance(CryptoBox.TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(CryptoBox.TAG_BITS, CryptoBox.randomBytes(CryptoBox.IV_LEN)))
        }
    }

    override fun decryptCipher(blob: ByteArray): Cipher? {
        val k = key ?: return null
        return Cipher.getInstance(CryptoBox.TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, k, GCMParameterSpec(CryptoBox.TAG_BITS, blob, 0, CryptoBox.IV_LEN))
        }
    }

    /** Simulates a new fingerprint enrolment invalidating the key. */
    override fun delete() { key = null }
}

class FakeClock(var now: Long = 1_780_000_000_000L) : () -> Long {
    override fun invoke() = now
    fun advance(ms: Long) { now += ms }
}
