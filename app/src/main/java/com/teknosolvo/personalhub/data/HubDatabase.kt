package com.teknosolvo.personalhub.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Place::class,
        LocationEvent::class,
        DailyActivity::class,
        TrackerStateEntity::class,
        VaultAccountEntity::class,
        VaultCategory::class,
        QuickNote::class,
        AppSettingEntry::class,
        SecuritySettings::class,
        AppNotification::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class HubDatabase : RoomDatabase() {
    abstract fun places(): PlaceDao
    abstract fun events(): LocationEventDao
    abstract fun activities(): ActivityDao
    abstract fun trackerState(): TrackerStateDao
    abstract fun vault(): VaultDao
    abstract fun security(): SecurityDao
    abstract fun notes(): NoteDao
    abstract fun settings(): SettingsDao
    abstract fun notifications(): NotificationDao

    companion object {
        const val NAME = "personalhub.db"

        val DEFAULT_CATEGORIES = listOf(
            "Banking" to "bank",
            "E-Wallet" to "wallet",
            "Social Media" to "social",
            "Email" to "email",
            "Gaming" to "game",
            "Marketplace" to "shop",
            "Work" to "work",
            "Server / Hosting" to "server",
            "Developer Tools" to "code",
            "Other" to "other",
        )

        private val seedCallback = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                DEFAULT_CATEGORIES.forEachIndexed { i, (name, icon) ->
                    db.execSQL(
                        "INSERT OR IGNORE INTO vault_categories (name, iconKey, builtin, sortOrder) VALUES (?, ?, 1, ?)",
                        arrayOf<Any>(name, icon, i),
                    )
                }
            }
        }

        fun build(context: Context): HubDatabase =
            Room.databaseBuilder(context, HubDatabase::class.java, NAME)
                .addCallback(seedCallback)
                .build()

        fun inMemory(context: Context): HubDatabase =
            Room.inMemoryDatabaseBuilder(context, HubDatabase::class.java)
                .addCallback(seedCallback)
                .allowMainThreadQueries()
                .build()
    }
}
