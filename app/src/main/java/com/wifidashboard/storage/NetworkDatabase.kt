package com.wifidashboard.storage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [DeviceEntity::class, DeviceObservationEntity::class, AccessControlEventEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class NetworkDatabase : RoomDatabase() {
    abstract fun deviceDao(): DeviceDao
    abstract fun accessControlAuditDao(): AccessControlAuditDao

    companion object {
        @Volatile
        private var instance: NetworkDatabase? = null

        fun get(context: Context): NetworkDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    NetworkDatabase::class.java,
                    "wifi-dashboard.db",
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `access_control_events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `deviceId` TEXT NOT NULL, `deviceLabel` TEXT NOT NULL, `macAddress` TEXT NOT NULL, `action` TEXT NOT NULL, `state` TEXT NOT NULL, `occurredAt` INTEGER NOT NULL, `detail` TEXT NOT NULL)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_access_control_events_occurredAt` ON `access_control_events` (`occurredAt`)",
                )
            }
        }
    }
}
