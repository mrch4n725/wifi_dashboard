package com.wifidashboard.storage

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow

class DeviceRepository(private val database: NetworkDatabase) {
    private val dao = database.deviceDao()

    fun observeDevices(): Flow<List<DeviceListItem>> = dao.observeDevices()

    fun observeDevice(deviceId: String): Flow<DeviceListItem?> = dao.observeDevice(deviceId)

    fun observeHistory(deviceId: String): Flow<List<DeviceObservationEntity>> =
        dao.observeHistory(deviceId)

    fun observeRecentAccessControlEvents(): Flow<List<AccessControlEventEntity>> =
        database.accessControlAuditDao().observeRecent()

    suspend fun recordAccessControlEvent(event: AccessControlEventEntity) {
        database.accessControlAuditDao().insert(event)
    }

    suspend fun record(observation: DeviceObservation): String {
        val identity = DeviceIdentity.from(observation)
        val identityKey = identity.key
        val mac = observation.macAddress?.let(DeviceIdentity::normalizeMac)
        val hostname = observation.hostname?.let(DeviceIdentity::normalizeHostname)
        val ipAddress = observation.ipAddress?.takeIf(String::isNotBlank)
        val observedAt = observation.observedAt
        val matchFrom = (observedAt - IDENTITY_MATCH_WINDOW_MILLIS).coerceAtLeast(0)
        val matchThrough = observedAt + IDENTITY_MATCH_WINDOW_MILLIS
        var recordedIdentityKey = identityKey

        database.withTransaction {
            val matchedStrongIdentities = if (identity.confidence < 0.9f) {
                dao.findRecentStrongIdentity(ipAddress, hostname, matchFrom, matchThrough)
            } else {
                emptyList()
            }
            val matchedWeakIdentities =
                dao.findRecentWeakIdentities(ipAddress, hostname, matchFrom, matchThrough)
            val targetIdentityKey = when {
                identity.confidence >= 0.9f -> identityKey
                matchedStrongIdentities.size == 1 -> matchedStrongIdentities.single()
                matchedStrongIdentities.isEmpty() && matchedWeakIdentities.size == 1 ->
                    matchedWeakIdentities.single()
                else -> identityKey
            }
            recordedIdentityKey = targetIdentityKey
            dao.insertDevice(
                DeviceEntity(
                    identityKey = targetIdentityKey,
                    customName = null,
                    groupName = null,
                    firstSeenAt = observedAt,
                    lastSeenAt = observedAt,
                    identityConfidence = identity.confidence,
                ),
            )

            val canConsolidateWeakIdentity =
                identity.confidence >= 0.9f || matchedStrongIdentities.size == 1
            if (canConsolidateWeakIdentity) {
                if (
                    matchedWeakIdentities.size == 1 &&
                    matchedWeakIdentities.single() != targetIdentityKey
                ) {
                    val weakIdentityKey = matchedWeakIdentities.single()
                    dao.mergeDeviceMetadata(weakIdentityKey, targetIdentityKey)
                    dao.moveObservations(weakIdentityKey, targetIdentityKey)
                    dao.deleteDevice(weakIdentityKey)
                }
            }

            dao.updateLastSeen(targetIdentityKey, observedAt, identity.confidence)
            dao.insertObservation(
                DeviceObservationEntity(
                    deviceId = targetIdentityKey,
                    source = observation.source,
                    observedAt = observedAt,
                    ipAddress = ipAddress,
                    macAddress = mac,
                    hostname = hostname,
                    serviceName = observation.serviceName?.takeIf(String::isNotBlank),
                    detail = observation.detail,
                ),
            )
        }
        return recordedIdentityKey
    }

    suspend fun updateName(deviceId: String, name: String?) {
        dao.updateName(deviceId, name?.trim()?.takeIf(String::isNotEmpty))
    }

    suspend fun updateGroup(deviceId: String, group: String?) {
        dao.updateGroup(deviceId, group?.trim()?.takeIf(String::isNotEmpty))
    }

    companion object {
        private const val IDENTITY_MATCH_WINDOW_MILLIS = 120_000L
    }
}

data class DeviceIdentity(val key: String, val confidence: Float) {
    companion object {
        fun from(observation: DeviceObservation): DeviceIdentity {
            val mac = observation.macAddress?.let(::normalizeMac)
            val hostname = observation.hostname?.let(::normalizeHostname)
            val ipAddress = observation.ipAddress?.takeIf(String::isNotBlank)
            return when {
                mac != null -> DeviceIdentity("mac:$mac", 1f)
                hostname != null -> DeviceIdentity("host:$hostname", 0.65f)
                ipAddress != null -> DeviceIdentity("ip:$ipAddress", 0.3f)
                else -> throw IllegalArgumentException(
                    "A device observation must include a usable MAC address, hostname, or IP address",
                )
            }
        }

        fun normalizeMac(value: String): String? {
            val normalized = value.lowercase(java.util.Locale.ROOT).replace('-', ':')
            return normalized.takeIf {
                it.matches(Regex("([0-9a-f]{2}:){5}[0-9a-f]{2}")) &&
                    it != "00:00:00:00:00:00" &&
                    it != "ff:ff:ff:ff:ff:ff"
            }
        }

        fun normalizeHostname(value: String): String? =
            value.trim().trimEnd('.').lowercase(java.util.Locale.ROOT).takeIf {
                it.isNotBlank() && it != "localhost" && it.length <= 253
            }
    }
}
