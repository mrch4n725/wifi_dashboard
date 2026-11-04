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

    @Query(
        """
        SELECT DISTINCT d.identityKey
        FROM devices d
        JOIN device_observations o ON o.deviceId = d.identityKey
        WHERE d.identityConfidence >= 0.9
          AND d.lastSeenAt BETWEEN :fromAt AND :toAt
          AND o.observedAt BETWEEN :fromAt AND :toAt
          AND (
            (:ipAddress IS NOT NULL AND o.ipAddress = :ipAddress)
            OR (:hostname IS NOT NULL AND o.hostname = :hostname)
          )
        ORDER BY d.lastSeenAt DESC
        """,
    )
    suspend fun findRecentStrongIdentity(
        ipAddress: String?,
        hostname: String?,
        fromAt: Long,
        toAt: Long,
    ): List<String>

    @Query(
        """
        SELECT DISTINCT d.identityKey
        FROM devices d
        JOIN device_observations o ON o.deviceId = d.identityKey
        WHERE d.identityConfidence < 0.9
          AND d.lastSeenAt BETWEEN :fromAt AND :toAt
          AND o.observedAt BETWEEN :fromAt AND :toAt
          AND (
            (:ipAddress IS NOT NULL AND o.ipAddress = :ipAddress)
            OR (:hostname IS NOT NULL AND o.hostname = :hostname)
          )
        ORDER BY d.lastSeenAt DESC
        """,
    )
    suspend fun findRecentWeakIdentities(
        ipAddress: String?,
        hostname: String?,
        fromAt: Long,
        toAt: Long,
    ): List<String>

    @Query(
        """
        UPDATE devices
        SET lastSeenAt = MAX(lastSeenAt, :observedAt),
            identityConfidence = MAX(identityConfidence, :identityConfidence)
        WHERE identityKey = :deviceId
        """,
    )
    suspend fun updateLastSeen(deviceId: String, observedAt: Long, identityConfidence: Float)

    @Query(
        """
        UPDATE devices
        SET firstSeenAt = MIN(firstSeenAt, (SELECT firstSeenAt FROM devices WHERE identityKey = :fromId)),
            lastSeenAt = MAX(lastSeenAt, (SELECT lastSeenAt FROM devices WHERE identityKey = :fromId)),
            customName = COALESCE(customName, (SELECT customName FROM devices WHERE identityKey = :fromId)),
            groupName = COALESCE(groupName, (SELECT groupName FROM devices WHERE identityKey = :fromId))
        WHERE identityKey = :toId
        """,
    )
    suspend fun mergeDeviceMetadata(fromId: String, toId: String)

    @Query("UPDATE device_observations SET deviceId = :toId WHERE deviceId = :fromId")
    suspend fun moveObservations(fromId: String, toId: String)

    @Query("DELETE FROM devices WHERE identityKey = :deviceId")
    suspend fun deleteDevice(deviceId: String)

    @Query("UPDATE devices SET customName = :name WHERE identityKey = :deviceId")
    suspend fun updateName(deviceId: String, name: String?)

    @Query("UPDATE devices SET groupName = :groupName WHERE identityKey = :deviceId")
    suspend fun updateGroup(deviceId: String, groupName: String?)
}

@Dao
interface AccessControlAuditDao {
    @Query("SELECT * FROM access_control_events ORDER BY occurredAt DESC LIMIT 20")
    fun observeRecent(): Flow<List<AccessControlEventEntity>>

    @Insert
    suspend fun insert(event: AccessControlEventEntity)
}
