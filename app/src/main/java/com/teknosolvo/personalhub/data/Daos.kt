package com.teknosolvo.personalhub.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaceDao {
    @Query("SELECT * FROM locations ORDER BY enabled DESC, name COLLATE NOCASE")
    fun observeAll(): Flow<List<Place>>

    @Query("SELECT * FROM locations ORDER BY name COLLATE NOCASE")
    suspend fun all(): List<Place>

    @Query("SELECT * FROM locations WHERE enabled = 1")
    suspend fun enabled(): List<Place>

    @Query("SELECT * FROM locations WHERE id = :id")
    suspend fun byId(id: Long): Place?

    @Query("SELECT * FROM locations WHERE id = :id")
    fun observe(id: Long): Flow<Place?>

    @Query("SELECT * FROM locations WHERE name LIKE '%' || :q || '%' OR address LIKE '%' || :q || '%'")
    suspend fun search(q: String): List<Place>

    @Insert
    suspend fun insert(place: Place): Long

    @Update
    suspend fun update(place: Place)

    @Delete
    suspend fun delete(place: Place)

    @Query("SELECT COUNT(*) FROM locations")
    suspend fun count(): Int

    @Query("DELETE FROM locations")
    suspend fun deleteAll()
}

@Dao
interface LocationEventDao {
    @Insert
    suspend fun insert(event: LocationEvent): Long

    @Query("SELECT * FROM location_events ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<LocationEvent>>

    @Query("SELECT * FROM location_events WHERE timestamp >= :from AND timestamp < :to ORDER BY timestamp ASC")
    suspend fun between(from: Long, to: Long): List<LocationEvent>

    @Query("SELECT * FROM location_events ORDER BY timestamp ASC")
    suspend fun all(): List<LocationEvent>

    @Query("SELECT COUNT(*) FROM location_events")
    suspend fun count(): Int

    @Query("DELETE FROM location_events WHERE timestamp < :before")
    suspend fun deleteBefore(before: Long)

    @Query("DELETE FROM location_events")
    suspend fun deleteAll()
}

@Dao
interface ActivityDao {
    @Insert
    suspend fun insert(activity: DailyActivity): Long

    @Update
    suspend fun update(activity: DailyActivity)

    @Delete
    suspend fun delete(activity: DailyActivity)

    @Query("SELECT * FROM daily_activities WHERE id = :id")
    suspend fun byId(id: Long): DailyActivity?

    @Query("SELECT * FROM daily_activities WHERE id = :id")
    fun observe(id: Long): Flow<DailyActivity?>

    /** Activities overlapping [from, to). */
    @Query(
        "SELECT * FROM daily_activities WHERE startAt < :to AND (endAt IS NULL OR endAt > :from) " +
            "ORDER BY startAt ASC"
    )
    fun observeOverlapping(from: Long, to: Long): Flow<List<DailyActivity>>

    @Query(
        "SELECT * FROM daily_activities WHERE startAt < :to AND (endAt IS NULL OR endAt > :from) " +
            "ORDER BY startAt ASC"
    )
    suspend fun overlapping(from: Long, to: Long): List<DailyActivity>

    @Query("SELECT * FROM daily_activities WHERE endAt IS NULL ORDER BY startAt DESC")
    suspend fun open(): List<DailyActivity>

    @Query("SELECT * FROM daily_activities ORDER BY startAt DESC LIMIT :limit")
    fun observeLatest(limit: Int): Flow<List<DailyActivity>>

    @Query(
        "SELECT * FROM daily_activities WHERE placeId = :placeId AND endAt IS NOT NULL " +
            "ORDER BY endAt DESC LIMIT 1"
    )
    suspend fun lastClosedAt(placeId: Long): DailyActivity?

    @Query(
        "SELECT * FROM daily_activities WHERE title LIKE '%' || :q || '%' OR note LIKE '%' || :q || '%' " +
            "ORDER BY startAt DESC LIMIT 100"
    )
    suspend fun search(q: String): List<DailyActivity>

    @Query("SELECT DISTINCT date FROM daily_activities WHERE date >= :fromDate AND date <= :toDate")
    fun observeActiveDates(fromDate: String, toDate: String): Flow<List<String>>

    @Query("SELECT * FROM daily_activities ORDER BY startAt ASC")
    suspend fun all(): List<DailyActivity>

    @Query("SELECT COUNT(*) FROM daily_activities")
    suspend fun count(): Int

    @Query("DELETE FROM daily_activities")
    suspend fun deleteAll()
}

@Dao
interface TrackerStateDao {
    @Query("SELECT * FROM tracker_state WHERE id = 1")
    suspend fun get(): TrackerStateEntity?

    @Query("SELECT * FROM tracker_state WHERE id = 1")
    fun observe(): Flow<TrackerStateEntity?>

    @Upsert
    suspend fun put(state: TrackerStateEntity)

    @Query("DELETE FROM tracker_state")
    suspend fun clear()
}

@Dao
interface VaultDao {
    @Query("SELECT * FROM vault_accounts ORDER BY updatedAt DESC")
    fun observeAccounts(): Flow<List<VaultAccountEntity>>

    @Query("SELECT * FROM vault_accounts")
    suspend fun allAccounts(): List<VaultAccountEntity>

    @Query("SELECT * FROM vault_accounts WHERE id = :id")
    suspend fun account(id: Long): VaultAccountEntity?

    @Insert
    suspend fun insertAccount(account: VaultAccountEntity): Long

    @Update
    suspend fun updateAccount(account: VaultAccountEntity)

    @Query("DELETE FROM vault_accounts WHERE id = :id")
    suspend fun deleteAccount(id: Long)

    @Query("UPDATE vault_accounts SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("SELECT COUNT(*) FROM vault_accounts")
    suspend fun accountCount(): Int

    @Query("DELETE FROM vault_accounts")
    suspend fun deleteAllAccounts()

    @Query("SELECT * FROM vault_categories ORDER BY sortOrder, name COLLATE NOCASE")
    fun observeCategories(): Flow<List<VaultCategory>>

    @Query("SELECT * FROM vault_categories ORDER BY sortOrder, name COLLATE NOCASE")
    suspend fun categories(): List<VaultCategory>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCategory(category: VaultCategory): Long

    @Query("SELECT * FROM vault_categories WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun categoryByName(name: String): VaultCategory?

    @Delete
    suspend fun deleteCategory(category: VaultCategory)
}

@Dao
interface SecurityDao {
    @Query("SELECT * FROM security_settings WHERE id = 1")
    suspend fun get(): SecuritySettings?

    @Query("SELECT * FROM security_settings WHERE id = 1")
    fun observe(): Flow<SecuritySettings?>

    @Upsert
    suspend fun put(settings: SecuritySettings)

    @Query("DELETE FROM security_settings")
    suspend fun clear()
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM quick_notes ORDER BY pinned DESC, updatedAt DESC")
    fun observeAll(): Flow<List<QuickNote>>

    @Query("SELECT * FROM quick_notes ORDER BY updatedAt DESC")
    suspend fun all(): List<QuickNote>

    @Query("SELECT * FROM quick_notes WHERE id = :id")
    suspend fun byId(id: Long): QuickNote?

    @Query("SELECT * FROM quick_notes WHERE title LIKE '%' || :q || '%' OR body LIKE '%' || :q || '%'")
    suspend fun search(q: String): List<QuickNote>

    @Upsert
    suspend fun upsert(note: QuickNote): Long

    @Delete
    suspend fun delete(note: QuickNote)

    @Query("SELECT COUNT(*) FROM quick_notes")
    suspend fun count(): Int

    @Query("DELETE FROM quick_notes")
    suspend fun deleteAll()
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM app_settings")
    fun observeAll(): Flow<List<AppSettingEntry>>

    @Query("SELECT * FROM app_settings")
    suspend fun all(): List<AppSettingEntry>

    @Upsert
    suspend fun put(entry: AppSettingEntry)
}

@Dao
interface NotificationDao {
    @Insert
    suspend fun insert(n: AppNotification): Long

    @Query("SELECT * FROM app_notifications ORDER BY createdAt DESC LIMIT 200")
    fun observeAll(): Flow<List<AppNotification>>

    @Query("SELECT COUNT(*) FROM app_notifications WHERE read = 0")
    fun observeUnread(): Flow<Int>

    @Query("UPDATE app_notifications SET read = 1")
    suspend fun markAllRead()

    @Query("DELETE FROM app_notifications")
    suspend fun deleteAll()
}
