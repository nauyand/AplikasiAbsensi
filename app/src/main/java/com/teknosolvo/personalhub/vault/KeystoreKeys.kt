package com.teknosolvo.personalhub.vault

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Wraps / unwraps a key with a hardware-backed, non-exportable key. Abstracted for tests. */
interface KeyWrapper {
    fun wrap(plain: ByteArray): ByteArray
    fun unwrap(blob: ByteArray): ByteArray
}

/** Biometric-bound key operations (BiometricPrompt + CryptoObject). Abstracted for tests. */
interface BiometricKeys {
    /** Cipher for wrapping the data key; must be authorized by BiometricPrompt before use. */
    fun encryptCipher(): Cipher

    /** Cipher for unwrapping, or null if the key was invalidated (new fingerprint enrolled). */
    fun decryptCipher(blob: ByteArray): Cipher?

    fun delete()
}

/**
 * Android Keystore implementation.
 *
 * - `hub_device_wrap`: AES-256-GCM, no user auth. Second layer under the PIN, binding the PIN
 *   wrapping to this device so an extracted database cannot be brute-forced offline.
 * - `hub_bio_wrap`: AES-256-GCM, requires strong biometric for *every* use, invalidated when a new
 *   biometric is enrolled.
 */
class AndroidKeystoreKeys : KeyWrapper, BiometricKeys {
    private val ks: KeyStore by lazy { KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) } }

    private fun deviceKey(): SecretKey {
        (ks.getKey(DEVICE_ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(DEVICE_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    private fun bioKey(create: Boolean): SecretKey? {
        (ks.getKey(BIO_ALIAS, null) as? SecretKey)?.let { return it }
        if (!create) return null
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val b = KeyGenParameterSpec.Builder(BIO_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            b.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        } else {
            @Suppress("DEPRECATION")
            b.setUserAuthenticationValidityDurationSeconds(-1)
        }
        gen.init(b.build())
        return gen.generateKey()
    }

    override fun wrap(plain: ByteArray): ByteArray {
        val c = Cipher.getInstance(CryptoBox.TRANSFORMATION)
        c.init(Cipher.ENCRYPT_MODE, deviceKey())
        return c.iv + c.doFinal(plain)
    }

    override fun unwrap(blob: ByteArray): ByteArray {
        val c = Cipher.getInstance(CryptoBox.TRANSFORMATION)
        c.init(Cipher.DECRYPT_MODE, deviceKey(), GCMParameterSpec(CryptoBox.TAG_BITS, blob, 0, CryptoBox.IV_LEN))
        return c.doFinal(blob, CryptoBox.IV_LEN, blob.size - CryptoBox.IV_LEN)
    }

    override fun encryptCipher(): Cipher {
        // Always start from a fresh key when (re)enabling biometrics.
        delete()
        val c = Cipher.getInstance(CryptoBox.TRANSFORMATION)
        c.init(Cipher.ENCRYPT_MODE, bioKey(create = true))
        return c
    }

    override fun decryptCipher(blob: ByteArray): Cipher? {
        val key = bioKey(create = false) ?: return null
        return try {
            Cipher.getInstance(CryptoBox.TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(CryptoBox.TAG_BITS, blob, 0, CryptoBox.IV_LEN))
            }
        } catch (e: KeyPermanentlyInvalidatedException) {
            delete()
            null
        }
    }

    override fun delete() {
        if (ks.containsAlias(BIO_ALIAS)) ks.deleteEntry(BIO_ALIAS)
    }

    fun deleteAll() {
        delete()
        if (ks.containsAlias(DEVICE_ALIAS)) ks.deleteEntry(DEVICE_ALIAS)
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val DEVICE_ALIAS = "hub_device_wrap"
        private const val BIO_ALIAS = "hub_bio_wrap"
    }
}
