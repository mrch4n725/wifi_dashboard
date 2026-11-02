package com.wifidashboard.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceDao {
    @Query(
        """
        SELECT d.identityKey AS id, d.customName, d.groupName, d.firstSeenAt,
               d.lastSeenAt, d.identityConfidence,
               o.ipAddress AS latestIp, o.macAddress AS latestMac,
               o.hostname AS latestHostname, o.serviceName AS latestServiceName,
               o.source AS latestSource,
               o.detail AS latestDetail
        FROM devices d
        LEFT JOIN device_observations o
          ON o.id = (
            SELECT latest.id FROM device_observations latest
            WHERE latest.deviceId = d.identityKey
            ORDER BY latest.observedAt DESC, latest.id DESC
            LIMIT 1
          )
        ORDER BY d.lastSeenAt DESC
        """,
    )
    fun observeDevices(): Flow<List<DeviceListItem>>

    @Query(
        """
        SELECT d.identityKey AS id, d.customName, d.groupName, d.firstSeenAt,
               d.lastSeenAt, d.identityConfidence,
               o.ipAddress AS latestIp, o.macAddress AS latestMac,
               o.hostname AS latestHostname, o.serviceName AS latestServiceName,
               o.source AS latestSource,
               o.detail AS latestDetail
        FROM devices d
        LEFT JOIN device_observations o
          ON o.id = (
            SELECT latest.id FROM device_observations latest
            WHERE latest.deviceId = d.identityKey
            ORDER BY latest.observedAt DESC, latest.id DESC
            LIMIT 1
          )
        WHERE d.identityKey = :deviceId
        LIMIT 1
        """,
    )
    fun observeDevice(deviceId: String): Flow<DeviceListItem?>

    @Query(
        """
        SELECT * FROM device_observations
        WHERE deviceId = :deviceId
        ORDER BY observedAt DESC, id DESC
        """,
    )
    fun observeHistory(deviceId: String): Flow<List<DeviceObservationEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDevice(device: DeviceEntity)

    @Insert
    suspend fun insertObservation(observation: DeviceObservationEntity)

    @Query("UPDATE devices SET lastSeenAt = MAX(lastSeenAt, :observedAt) WHERE identityKey = :deviceId")
    suspend fun updateLastSeen(deviceId: String, observedAt: Long)

    @Query("UPDATE devices SET customName = :name WHERE identityKey = :deviceId")
    suspend fun updateName(deviceId: String, name: String?)

    @Query("UPDATE devices SET groupName = :groupName WHERE identityKey = :deviceId")
    suspend fun updateGroup(deviceId: String, groupName: String?)
}
