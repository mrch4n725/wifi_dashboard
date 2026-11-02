package com.wifidashboard.storage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [DeviceEntity::class, DeviceObservationEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class NetworkDatabase : RoomDatabase() {
    abstract fun deviceDao(): DeviceDao

    companion object {
        @Volatile
        private var instance: NetworkDatabase? = null

        fun get(context: Context): NetworkDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    NetworkDatabase::class.java,
                    "wifi-dashboard.db",
                ).build().also { instance = it }
            }
    }
}
