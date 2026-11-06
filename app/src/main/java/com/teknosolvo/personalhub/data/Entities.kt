package com.teknosolvo.personalhub.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Kind of a registered place. Drives statistics (office hours, home time, break time). */
object PlaceKind {
    const val OFFICE = "OFFICE"
    const val HOME = "HOME"
    const val BREAK = "BREAK"
    const val FOOD = "FOOD"
    const val OTHER = "OTHER"
    val all = listOf(OFFICE, HOME, BREAK, FOOD, OTHER)

    fun label(kind: String) = when (kind) {
        OFFICE -> "Kantor"
        HOME -> "Rumah"
        BREAK -> "Istirahat"
        FOOD -> "Tempat Makan"
        ActivityKind.PERSONAL -> "Aktivitas Pribadi"
        else -> "Lainnya"
    }
}

/** A user-defined location with a geofence radius ("Locations"). */
@Entity(tableName = "locations")
data class Place(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: String = PlaceKind.OTHER,
    val latitude: Double,
    val longitude: Double,
    val radiusM: Int,
    val colorArgb: Long,
    val iconKey: String,
    val enabled: Boolean = true,
    val address: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

object EventType {
    const val ENTER = "ENTER"
    const val EXIT = "EXIT"
}

object EventSource {
    const val GEOFENCE = "GEOFENCE"
    const val CONFIRM = "CONFIRM"
    const val PERIODIC = "PERIODIC"
    const val FOREGROUND = "FOREGROUND"
    const val SIMULATION = "SIMULATION"
}

/** Outcome of processing a raw detection. */
object DetectionStatus {
    const val CONFIRMED = "CONFIRMED"
    const val PENDING = "PENDING"
    const val DUPLICATE = "DUPLICATE"
    const val LOW_ACCURACY = "LOW_ACCURACY"
    const val IMPLAUSIBLE_JUMP = "IMPLAUSIBLE_JUMP"
    const val CANCELLED = "CANCELLED"
    const val MERGED = "MERGED"

    fun label(s: String) = when (s) {
        CONFIRMED -> "Terkonfirmasi"
        PENDING -> "Menunggu konfirmasi"
        DUPLICATE -> "Duplikat diabaikan"
        LOW_ACCURACY -> "Akurasi rendah"
        IMPLAUSIBLE_JUMP -> "Lompatan tidak wajar"
        CANCELLED -> "Dibatalkan (GPS goyah)"
        MERGED -> "Digabung ke kunjungan sebelumnya"
        "MOCK_LOCATION" -> "Lokasi palsu (mock) ditolak"
        "DISABLED" -> "Tracker nonaktif"
        else -> s
    }
}

/** Raw log of every enter/exit detection, including rejected ones (audit trail). */
@Entity(
    tableName = "location_events",
    foreignKeys = [ForeignKey(
        entity = Place::class, parentColumns = ["id"], childColumns = ["placeId"],
        onDelete = ForeignKey.SET_NULL,
    )],
    indices = [Index("placeId"), Index("timestamp")],
)
data class LocationEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val placeId: Long?,
    val placeName: String,
    val type: String,
    val timestamp: Long,
    val latitude: Double?,
    val longitude: Double?,
    val accuracyM: Float?,
    val source: String,
    val status: String,
    val activityId: Long? = null,
)

object ActivityKind {
    /** Visit to a registered place; kind snapshot = place kind. */
    const val PERSONAL = "PERSONAL"
}

object ActivitySource {
    const val AUTO = "AUTO"
    const val MANUAL = "MANUAL"
    const val SIMULATED = "SIMULATED"

    fun label(s: String) = when (s) {
        AUTO -> "Otomatis"
        MANUAL -> "Manual"
        SIMULATED -> "Simulasi"
        else -> s
    }
}

/**
 * One stay at a place (or a manual personal activity): entry time, exit time, duration.
 * ("DailyActivities")
 */
@Entity(
    tableName = "daily_activities",
    foreignKeys = [ForeignKey(
        entity = Place::class, parentColumns = ["id"], childColumns = ["placeId"],
        onDelete = ForeignKey.SET_NULL,
    )],
    indices = [Index("placeId"), Index("startAt"), Index("date")],
)
data class DailyActivity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val placeId: Long?,
    /** Snapshot of the place name, or the title of a personal activity. */
    val title: String,
    /** [PlaceKind] value or [ActivityKind.PERSONAL]. */
    val kind: String,
    /** yyyy-MM-dd of [startAt], local time. */
    val date: String,
    val startAt: Long,
    /** Null while still at the place. */
    val endAt: Long?,
    val source: String = ActivitySource.AUTO,
    /** True once the user corrected an automatic record. */
    val edited: Boolean = false,
    val note: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
) {
    val isOpen: Boolean get() = endAt == null
    val isManual: Boolean get() = source == ActivitySource.MANUAL || edited
}

/** Single-row persistent state of the presence engine. */
@Entity(tableName = "tracker_state")
data class TrackerStateEntity(
    @PrimaryKey val id: Int = 1,
    val currentActivityId: Long? = null,
    val currentPlaceId: Long? = null,
    val pendingPlaceId: Long? = null,
    val pendingSince: Long? = null,
    val pendingExitSince: Long? = null,
    val lastLat: Double? = null,
    val lastLng: Double? = null,
    val lastAccuracy: Float? = null,
    val lastFixAt: Long? = null,
    val lastCheckAt: Long? = null,
    val lastIssue: String? = null,
)

@Entity(
    tableName = "vault_categories",
    indices = [Index(value = ["name"], unique = true)],
)
data class VaultCategory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val iconKey: String,
    val builtin: Boolean = false,
    val sortOrder: Int = 0,
)

/**
 * Vault entry. Everything sensitive (title, username, email, password, url, notes) lives in
 * [payload] as AES-256-GCM ciphertext (iv || ciphertext+tag) under the vault data key.
 */
@Entity(
    tableName = "vault_accounts",
    foreignKeys = [ForeignKey(
        entity = VaultCategory::class, parentColumns = ["id"], childColumns = ["categoryId"],
        onDelete = ForeignKey.SET_NULL,
    )],
    indices = [Index("categoryId")],
)
data class VaultAccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long?,
    val favorite: Boolean = false,
    val payload: ByteArray,
    val createdAt: Long,
    val updatedAt: Long,
) {
    override fun equals(other: Any?) = other is VaultAccountEntity && other.id == id &&
        other.updatedAt == updatedAt && other.payload.contentEquals(payload)

    override fun hashCode() = id.hashCode() * 31 + updatedAt.hashCode()
}

object NoteTag {
    const val WORK = "WORK"
    const val IDEA = "IDEA"
    const val REMINDER = "REMINDER"
    const val IMPORTANT = "IMPORTANT"
    val all = listOf(WORK, IDEA, REMINDER, IMPORTANT)
    fun label(t: String) = when (t) {
        WORK -> "Pekerjaan"
        IDEA -> "Ide"
        REMINDER -> "Pengingat"
        IMPORTANT -> "Penting"
        else -> t
    }
}

@Entity(tableName = "quick_notes", indices = [Index("updatedAt")])
data class QuickNote(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val body: String,
    val tag: String = NoteTag.WORK,
    val pinned: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)

/** Generic key/value app preferences ("AppSettings"). */
@Entity(tableName = "app_settings")
data class AppSettingEntry(
    @PrimaryKey val key: String,
    val value: String,
)

/**
 * Vault security material ("SecuritySettings"). Single row, id = 1.
 *
 * The vault data key (DEK) is never stored in plaintext:
 * - [pinWrappedDek] = AES-GCM(PBKDF2(PIN, salt), AES-GCM(KeystoreKey, DEK))
 * - [bioWrappedDek] = AES-GCM(biometric-bound KeystoreKey, DEK), usable only through BiometricPrompt.
 */
@Entity(tableName = "security_settings")
data class SecuritySettings(
    @PrimaryKey val id: Int = 1,
    val pinSalt: ByteArray,
    val pinIterations: Int,
    val pinLength: Int,
    val pinWrappedDek: ByteArray,
    val bioWrappedDek: ByteArray? = null,
    val biometricEnabled: Boolean = false,
    val autoLockSeconds: Int = 60,
    val lockOnBackground: Boolean = true,
    val screenshotProtection: Boolean = true,
    val appLockEnabled: Boolean = false,
    val clipboardClearSeconds: Int = 30,
    val failedAttempts: Int = 0,
    val lockoutUntil: Long = 0,
    val createdAt: Long,
    val updatedAt: Long,
) {
    override fun equals(other: Any?) = other is SecuritySettings && other.updatedAt == updatedAt &&
        other.failedAttempts == failedAttempts && other.biometricEnabled == biometricEnabled &&
        other.pinWrappedDek.contentEquals(pinWrappedDek)

    override fun hashCode() = updatedAt.hashCode()
}

object NotificationType {
    const val ENTER = "ENTER"
    const val EXIT = "EXIT"
    const val GPS = "GPS"
    const val PERMISSION = "PERMISSION"
    const val SYSTEM = "SYSTEM"
}

/** In-app notification center entries. */
@Entity(tableName = "app_notifications", indices = [Index("createdAt")])
data class AppNotification(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val title: String,
    val body: String,
    val createdAt: Long,
    val read: Boolean = false,
)
