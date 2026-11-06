package com.teknosolvo.personalhub.vault

import androidx.room.withTransaction
import com.teknosolvo.personalhub.data.HubDatabase
import com.teknosolvo.personalhub.data.SecuritySettings
import com.teknosolvo.personalhub.data.VaultAccountEntity
import com.teknosolvo.personalhub.data.VaultCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher

/** Plaintext of an encrypted vault entry. Only ever exists in memory while unlocked. */
@Serializable
data class VaultSecret(
    val title: String,
    val username: String = "",
    val email: String = "",
    val password: String = "",
    val url: String = "",
    val notes: String = "",
)

data class VaultAccount(
    val id: Long = 0,
    val categoryId: Long?,
    val favorite: Boolean = false,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val secret: VaultSecret,
)

sealed interface UnlockResult {
    data object Success : UnlockResult
    data class WrongPin(val attemptsLeft: Int) : UnlockResult
    data class LockedOut(val until: Long) : UnlockResult
    data object NotSetUp : UnlockResult
    data class Error(val message: String) : UnlockResult
}

class VaultRepository(
    private val db: HubDatabase,
    private val session: VaultSession,
    private val deviceWrapper: KeyWrapper,
    private val bioKeys: BiometricKeys?,
    private val clock: () -> Long = System::currentTimeMillis,
    private val pinIterations: Int = DEFAULT_ITERATIONS,
) {
    private val dao = db.vault()
    private val security = db.security()
    private val json = Json { ignoreUnknownKeys = true }

    val securityFlow: Flow<SecuritySettings?> = security.observe()
    val categories: Flow<List<VaultCategory>> = dao.observeCategories()

    /** Decrypted accounts; empty while locked. */
    val accounts: Flow<List<VaultAccount>> = combine(dao.observeAccounts(), session.unlocked) { rows, unlocked ->
        if (!unlocked) emptyList() else rows.mapNotNull { runCatching { decryptRow(it) }.getOrNull() }
    }

    suspend fun isSetUp(): Boolean = security.get() != null
    suspend fun settings(): SecuritySettings? = security.get()

    // ------------------------------------------------------------------ PIN

    suspend fun setupPin(pin: CharArray) {
        validatePin(pin)
        check(security.get() == null) { "Vault sudah dibuat" }
        val dek = CryptoBox.newDataKey()
        try {
            val salt = CryptoBox.randomBytes(16)
            val now = clock()
            security.put(
                SecuritySettings(
                    pinSalt = salt,
                    pinIterations = pinIterations,
                    pinLength = pin.size,
                    pinWrappedDek = wrapWithPin(dek, pin, salt, pinIterations),
                    createdAt = now,
                    updatedAt = now,
                )
            )
            session.open(dek.copyOf())
        } finally {
            CryptoBox.wipe(dek)
            CryptoBox.wipe(pin)
        }
    }

    suspend fun unlockWithPin(pin: CharArray): UnlockResult {
        try {
            val s = security.get() ?: return UnlockResult.NotSetUp
            val now = clock()
            if (s.lockoutUntil > now) return UnlockResult.LockedOut(s.lockoutUntil)
            val dek = try {
                unwrapWithPin(s, pin)
            } catch (e: AEADBadTagException) {
                return registerFailure(s)
            } catch (e: Exception) {
                return UnlockResult.Error(e.javaClass.simpleName)
            }
            security.put(s.copy(failedAttempts = 0, lockoutUntil = 0, updatedAt = now))
            session.open(dek)
            return UnlockResult.Success
        } finally {
            CryptoBox.wipe(pin)
        }
    }

    /** Verify the PIN without changing the session (for destructive confirmations). */
    suspend fun verifyPin(pin: CharArray): UnlockResult {
        try {
            val s = security.get() ?: return UnlockResult.NotSetUp
            if (s.lockoutUntil > clock()) return UnlockResult.LockedOut(s.lockoutUntil)
            return try {
                CryptoBox.wipe(unwrapWithPin(s, pin))
                security.put(s.copy(failedAttempts = 0, lockoutUntil = 0))
                UnlockResult.Success
            } catch (e: AEADBadTagException) {
                registerFailure(s)
            }
        } finally {
            CryptoBox.wipe(pin)
        }
    }

    suspend fun changePin(oldPin: CharArray, newPin: CharArray): UnlockResult {
        validatePin(newPin)
        val s = security.get() ?: return UnlockResult.NotSetUp
        val res = verifyPin(oldPin)
        if (res != UnlockResult.Success) return res
        val dek = session.copyKeyBytes()
        try {
            val salt = CryptoBox.randomBytes(16)
            security.put(
                s.copy(
                    pinSalt = salt, pinIterations = pinIterations, pinLength = newPin.size,
                    pinWrappedDek = wrapWithPin(dek, newPin, salt, pinIterations),
                    failedAttempts = 0, lockoutUntil = 0, updatedAt = clock(),
                )
            )
        } finally {
            CryptoBox.wipe(dek)
            CryptoBox.wipe(newPin)
        }
        return UnlockResult.Success
    }

    private suspend fun registerFailure(s: SecuritySettings): UnlockResult {
        val failures = s.failedAttempts + 1
        val lockout = if (failures >= FREE_ATTEMPTS) {
            val exp = (failures - FREE_ATTEMPTS).coerceAtMost(7)
            clock() + (30_000L shl exp).coerceAtMost(MAX_LOCKOUT_MS)
        } else 0L
        security.put(s.copy(failedAttempts = failures, lockoutUntil = lockout))
        return if (lockout > 0) UnlockResult.LockedOut(lockout)
        else UnlockResult.WrongPin(FREE_ATTEMPTS - failures)
    }

    private fun wrapWithPin(dek: ByteArray, pin: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val inner = deviceWrapper.wrap(dek)
        val kek = CryptoBox.deriveKey(pin, salt, iterations)
        return CryptoBox.encrypt(kek, inner, AAD_PIN).also { CryptoBox.wipe(inner) }
    }

    private fun unwrapWithPin(s: SecuritySettings, pin: CharArray): ByteArray {
        val kek = CryptoBox.deriveKey(pin, s.pinSalt, s.pinIterations)
        val inner = CryptoBox.decrypt(kek, s.pinWrappedDek, AAD_PIN)
        return deviceWrapper.unwrap(inner).also { CryptoBox.wipe(inner) }
    }

    private fun validatePin(pin: CharArray) {
        require(pin.size in 4..8 && pin.all { it.isDigit() }) { "PIN harus 4–8 digit angka" }
    }

    // ------------------------------------------------------------------ Biometrics

    /** Step 1 of enabling biometrics: a cipher to authorize with BiometricPrompt. */
    fun biometricEnrollCipher(): Cipher? = bioKeys?.encryptCipher()

    /** Step 2: after BiometricPrompt succeeded, wrap the (unlocked) data key with the cipher. */
    suspend fun completeBiometricEnroll(authorized: Cipher) {
        val s = security.get() ?: return
        val dek = session.copyKeyBytes()
        try {
            val blob = authorized.iv + authorized.doFinal(dek)
            security.put(s.copy(bioWrappedDek = blob, biometricEnabled = true, updatedAt = clock()))
        } finally {
            CryptoBox.wipe(dek)
        }
    }

    suspend fun disableBiometric() {
        bioKeys?.delete()
        val s = security.get() ?: return
        security.put(s.copy(bioWrappedDek = null, biometricEnabled = false, updatedAt = clock()))
    }

    /** Cipher for biometric unlock, or null when biometrics are off or the key was invalidated. */
    suspend fun biometricUnlockCipher(): Cipher? {
        val s = security.get() ?: return null
        val blob = s.bioWrappedDek
        if (!s.biometricEnabled || blob == null || bioKeys == null) return null
        val cipher = bioKeys.decryptCipher(blob)
        if (cipher == null) disableBiometric()
        return cipher
    }

    suspend fun completeBiometricUnlock(authorized: Cipher): UnlockResult {
        val s = security.get() ?: return UnlockResult.NotSetUp
        val blob = s.bioWrappedDek ?: return UnlockResult.Error("Biometrik belum diaktifkan")
        return try {
            val dek = authorized.doFinal(blob, CryptoBox.IV_LEN, blob.size - CryptoBox.IV_LEN)
            security.put(s.copy(failedAttempts = 0, lockoutUntil = 0))
            session.open(dek)
            UnlockResult.Success
        } catch (e: Exception) {
            UnlockResult.Error("Biometrik gagal: ${e.javaClass.simpleName}")
        }
    }

    // ------------------------------------------------------------------ Settings

    suspend fun updateSecurity(transform: (SecuritySettings) -> SecuritySettings) {
        val s = security.get() ?: return
        val next = transform(s).copy(updatedAt = clock())
        security.put(next)
        applySessionSettings(next)
    }

    fun applySessionSettings(s: SecuritySettings?) {
        if (s == null) return
        session.autoLockSeconds = s.autoLockSeconds
        session.lockOnBackground = s.lockOnBackground
    }

    // ------------------------------------------------------------------ Accounts

    fun encryptSecret(secret: VaultSecret): ByteArray =
        CryptoBox.encrypt(session.requireKey(), json.encodeToString(VaultSecret.serializer(), secret).toByteArray(), AAD_ACCOUNT)

    fun decryptRow(row: VaultAccountEntity): VaultAccount {
        val plain = CryptoBox.decrypt(session.requireKey(), row.payload, AAD_ACCOUNT)
        try {
            val secret = json.decodeFromString(VaultSecret.serializer(), plain.decodeToString())
            return VaultAccount(row.id, row.categoryId, row.favorite, row.createdAt, row.updatedAt, secret)
        } finally {
            CryptoBox.wipe(plain)
        }
    }

    suspend fun account(id: Long): VaultAccount? = dao.account(id)?.let { decryptRow(it) }

    suspend fun save(account: VaultAccount): Long {
        require(account.secret.title.isNotBlank()) { "Nama aplikasi/website wajib diisi" }
        val now = clock()
        val payload = encryptSecret(account.secret)
        return if (account.id == 0L) {
            dao.insertAccount(VaultAccountEntity(categoryId = account.categoryId, favorite = account.favorite,
                payload = payload, createdAt = now, updatedAt = now))
        } else {
            val old = dao.account(account.id) ?: error("Akun tidak ditemukan")
            dao.updateAccount(old.copy(categoryId = account.categoryId, favorite = account.favorite,
                payload = payload, updatedAt = now))
            account.id
        }
    }

    suspend fun delete(id: Long) = dao.deleteAccount(id)
    suspend fun setFavorite(id: Long, favorite: Boolean) = dao.setFavorite(id, favorite)
    suspend fun addCategory(name: String, icon: String = "other") =
        dao.insertCategory(VaultCategory(name = name.trim(), iconKey = icon, sortOrder = 100))

    suspend fun deleteCategory(c: VaultCategory) { if (!c.builtin) dao.deleteCategory(c) }
    suspend fun categoryList() = dao.categories()
    suspend fun accountCount() = dao.accountCount()

    suspend fun allDecrypted(): List<VaultAccount> = dao.allAccounts().map { decryptRow(it) }

    /** Replace or merge accounts from a restored backup (vault must be unlocked). */
    suspend fun importAccounts(items: List<Pair<String?, VaultAccount>>, replace: Boolean) {
        db.withTransaction {
            if (replace) dao.deleteAllAccounts()
            for ((categoryName, acc) in items) {
                val catId = categoryName?.let { name ->
                    dao.categoryByName(name)?.id ?: dao.insertCategory(VaultCategory(name = name, iconKey = "other", sortOrder = 100))
                }
                dao.insertAccount(
                    VaultAccountEntity(categoryId = catId, favorite = acc.favorite, payload = encryptSecret(acc.secret),
                        createdAt = acc.createdAt, updatedAt = acc.updatedAt)
                )
            }
        }
    }

    /** Destroy the vault: all accounts, keys and PIN. */
    suspend fun wipeVault() {
        db.withTransaction {
            dao.deleteAllAccounts()
            security.clear()
        }
        bioKeys?.delete()
        session.lock()
    }

    companion object {
        const val DEFAULT_ITERATIONS = 120_000
        const val FREE_ATTEMPTS = 5
        const val MAX_LOCKOUT_MS = 60 * 60_000L
        private val AAD_PIN = "hub-pin-wrap-v1".toByteArray()
        private val AAD_ACCOUNT = "hub-vault-account-v1".toByteArray()
    }
}
