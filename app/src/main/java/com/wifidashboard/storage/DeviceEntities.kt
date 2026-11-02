package com.wifidashboard.storage

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "devices",
    indices = [Index(value = ["lastSeenAt"])],
)
data class DeviceEntity(
    @PrimaryKey val identityKey: String,
    val customName: String?,
    val groupName: String?,
    val firstSeenAt: Long,
    val lastSeenAt: Long,
    val identityConfidence: Float,
)

@Entity(
    tableName = "device_observations",
    foreignKeys = [
        ForeignKey(
            entity = DeviceEntity::class,
            parentColumns = ["identityKey"],
            childColumns = ["deviceId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("deviceId"),
        Index("observedAt"),
    ],
)
data class DeviceObservationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deviceId: String,
    val source: String,
    val observedAt: Long,
    val ipAddress: String?,
    val macAddress: String?,
    val hostname: String?,
    val serviceName: String?,
    val detail: String?,
)

data class DeviceListItem(
    @ColumnInfo(name = "id") val id: String,
    val customName: String?,
    val groupName: String?,
    val firstSeenAt: Long,
    val lastSeenAt: Long,
    val identityConfidence: Float,
    val latestIp: String?,
    val latestMac: String?,
    val latestHostname: String?,
    val latestServiceName: String?,
    val latestSource: String?,
    val latestDetail: String?,
)

data class DeviceObservation(
    val source: String,
    val observedAt: Long,
    val ipAddress: String? = null,
    val macAddress: String? = null,
    val hostname: String? = null,
    val serviceName: String? = null,
    val detail: String? = null,
)
