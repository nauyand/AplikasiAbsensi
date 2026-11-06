package com.teknosolvo.personalhub.backup

import androidx.room.withTransaction
import com.teknosolvo.personalhub.data.DailyActivity
import com.teknosolvo.personalhub.data.HubDatabase
import com.teknosolvo.personalhub.data.Place
import com.teknosolvo.personalhub.data.QuickNote
import com.teknosolvo.personalhub.vault.CryptoBox
import com.teknosolvo.personalhub.vault.VaultAccount
import com.teknosolvo.personalhub.vault.VaultRepository
import com.teknosolvo.personalhub.vault.VaultSecret
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Base64
import javax.crypto.AEADBadTagException

@Serializable
data class BackupEnvelope(
    val format: String = FORMAT,
    val version: Int = 1,
    val createdAt: Long,
    val kdf: String = "PBKDF2-HMAC-SHA256",
    val iterations: Int,
    val salt: String,
    val cipher: String = "AES-256-GCM",
    val data: String,
) {
    companion object { const val FORMAT = "nauy-hub-backup" }
}

@Serializable
data class BackupPlace(
    val id: Long, val name: String, val kind: String, val latitude: Double, val longitude: Double,
    val radiusM: Int, val colorArgb: Long, val iconKey: String, val enabled: Boolean, val address: String?,
    val createdAt: Long, val updatedAt: Long,
)

@Serializable
data class BackupActivity(
    val placeId: Long?, val title: String, val kind: String, val date: String, val startAt: Long,
    val endAt: Long?, val source: String, val edited: Boolean, val note: String?,
    val createdAt: Long, val updatedAt: Long,
)

@Serializable
data class BackupNote(
    val title: String, val body: String, val tag: String, val pinned: Boolean, val createdAt: Long, val updatedAt: Long,
)

@Serializable
data class BackupAccount(
    val category: String?, val favorite: Boolean, val createdAt: Long, val updatedAt: Long, val secret: VaultSecret,
)

@Serializable
data class BackupPayload(
    val createdAt: Long,
    val places: List<BackupPlace>,
    val activities: List<BackupActivity>,
    val notes: List<BackupNote>,
    /** Null when the vault was locked / not set up at export time. */
    val vault: List<BackupAccount>?,
    val customCategories: List<String> = emptyList(),
)

data class BackupStats(val places: Int, val activities: Int, val notes: Int, val accounts: Int?)

class WrongBackupPasswordException : Exception("Password backup salah atau file rusak")

/**
 * Encrypted backup. The whole payload (including decrypted vault entries) is serialized to JSON
 * and encrypted with AES-256-GCM under a key derived from a *backup password* (PBKDF2, 210k
 * iterations). The file contains no plaintext data, and is portable to a new device (unlike the
 * Keystore-bound PIN wrapping).
 */
class BackupManager(
    private val db: HubDatabase,
    private val vault: VaultRepository?,
    private val clock: () -> Long = System::currentTimeMillis,
    private val iterations: Int = ITERATIONS,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun buildPayload(includeVault: Boolean): BackupPayload {
        val categories = db.vault().categories()
        val catName = categories.associate { it.id to it.name }
        val accounts: List<BackupAccount>? = if (includeVault && vault != null) {
            vault.allDecrypted().map { BackupAccount(it.categoryId?.let(catName::get), it.favorite, it.createdAt, it.updatedAt, it.secret) }
        } else null
        return BackupPayload(
            createdAt = clock(),
            places = db.places().all().map {
                BackupPlace(it.id, it.name, it.kind, it.latitude, it.longitude, it.radiusM, it.colorArgb, it.iconKey,
                    it.enabled, it.address, it.createdAt, it.updatedAt)
            },
            activities = db.activities().all().map {
                BackupActivity(it.placeId, it.title, it.kind, it.date, it.startAt, it.endAt, it.source, it.edited, it.note,
                    it.createdAt, it.updatedAt)
            },
            notes = db.notes().all().map { BackupNote(it.title, it.body, it.tag, it.pinned, it.createdAt, it.updatedAt) },
            vault = accounts,
            customCategories = categories.filter { !it.builtin }.map { it.name },
        )
    }

    fun encrypt(payload: BackupPayload, password: CharArray): ByteArray {
        require(password.size >= MIN_PASSWORD) { "Password backup minimal $MIN_PASSWORD karakter" }
        val salt = CryptoBox.randomBytes(16)
        val key = CryptoBox.deriveKey(password, salt, iterations)
        val plain = json.encodeToString(BackupPayload.serializer(), payload).toByteArray()
        try {
            val blob = CryptoBox.encrypt(key, plain, AAD)
            val env = BackupEnvelope(
                createdAt = payload.createdAt, iterations = iterations,
                salt = Base64.getEncoder().encodeToString(salt), data = Base64.getEncoder().encodeToString(blob),
            )
            return json.encodeToString(BackupEnvelope.serializer(), env).toByteArray()
        } finally {
            CryptoBox.wipe(plain)
        }
    }

    fun decrypt(file: ByteArray, password: CharArray): BackupPayload {
        val env = try {
            json.decodeFromString(BackupEnvelope.serializer(), file.decodeToString())
        } catch (e: Exception) {
            throw IllegalArgumentException("Bukan file backup NAUY Hub")
        }
        require(env.format == BackupEnvelope.FORMAT) { "Format backup tidak dikenal" }
        require(env.version <= 1) { "Versi backup lebih baru dari aplikasi" }
        val key = CryptoBox.deriveKey(password, Base64.getDecoder().decode(env.salt), env.iterations)
        val plain = try {
            CryptoBox.decrypt(key, Base64.getDecoder().decode(env.data), AAD)
        } catch (e: AEADBadTagException) {
            throw WrongBackupPasswordException()
        }
        try {
            return json.decodeFromString(BackupPayload.serializer(), plain.decodeToString())
        } finally {
            CryptoBox.wipe(plain)
        }
    }

    suspend fun export(password: CharArray, includeVault: Boolean): ByteArray =
        try { encrypt(buildPayload(includeVault), password) } finally { CryptoBox.wipe(password) }

    fun stats(p: BackupPayload) = BackupStats(p.places.size, p.activities.size, p.notes.size, p.vault?.size)

    /**
     * Replace local tracker data and notes with the backup. Vault entries are restored only when
     * [restoreVault] is true and the vault is unlocked (they are re-encrypted with this device's key).
     */
    suspend fun restore(p: BackupPayload, restoreVault: Boolean) {
        db.withTransaction {
            db.activities().deleteAll()
            db.events().deleteAll()
            db.places().deleteAll()
            db.trackerState().clear()
            db.notes().deleteAll()
            p.places.forEach {
                db.places().insert(
                    Place(it.id, it.name, it.kind, it.latitude, it.longitude, it.radiusM, it.colorArgb, it.iconKey,
                        it.enabled, it.address, it.createdAt, it.updatedAt)
                )
            }
            val placeIds = p.places.map { it.id }.toSet()
            p.activities.forEach {
                db.activities().insert(
                    DailyActivity(
                        placeId = it.placeId?.takeIf { id -> id in placeIds }, title = it.title, kind = it.kind,
                        date = it.date, startAt = it.startAt, endAt = it.endAt ?: maxOf(p.createdAt, it.startAt), source = it.source,
                        edited = it.edited, note = it.note, createdAt = it.createdAt, updatedAt = it.updatedAt,
                    )
                )
            }
            p.notes.forEach {
                db.notes().upsert(QuickNote(title = it.title, body = it.body, tag = it.tag, pinned = it.pinned,
                    createdAt = it.createdAt, updatedAt = it.updatedAt))
            }
        }
        val items = p.vault
        if (restoreVault && items != null && vault != null) {
            p.customCategories.forEach { vault.addCategory(it) }
            vault.importAccounts(
                items.map { it.category to VaultAccount(categoryId = null, favorite = it.favorite, createdAt = it.createdAt,
                    updatedAt = it.updatedAt, secret = it.secret) },
                replace = true,
            )
        }
    }

    companion object {
        const val ITERATIONS = 210_000
        const val MIN_PASSWORD = 8
        private val AAD = "nauy-hub-backup-v1".toByteArray()
    }
}
