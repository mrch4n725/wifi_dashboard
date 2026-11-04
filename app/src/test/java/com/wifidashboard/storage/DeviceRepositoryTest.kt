package com.wifidashboard.storage

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DeviceRepositoryTest {
    @Test
    fun persistsIdentityLabelsAndObservationHistory() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            NetworkDatabase::class.java,
        ).allowMainThreadQueries().build()

        try {
            val repository = DeviceRepository(database)
            repository.record(
                DeviceObservation(
                    source = "Android neighbor table",
                    observedAt = 1_000L,
                    ipAddress = "192.168.1.30",
                    macAddress = "aa:bb:cc:dd:ee:ff",
                ),
            )
            repository.updateName("mac:aa:bb:cc:dd:ee:ff", "Office printer")
            repository.updateGroup("mac:aa:bb:cc:dd:ee:ff", "Office")
            repository.record(
                DeviceObservation(
                    source = "Android neighbor table",
                    observedAt = 2_000L,
                    ipAddress = "192.168.1.31",
                    macAddress = "aa:bb:cc:dd:ee:ff",
                    hostname = "printer.local",
                    serviceName = "Office printer._ipp._tcp",
                ),
            )

            val device = repository.observeDevices().first().single()
            val history = repository.observeHistory(device.id).first()

            assertEquals("Office printer", device.customName)
            assertEquals("Office", device.groupName)
            assertEquals(1_000L, device.firstSeenAt)
            assertEquals(2_000L, device.lastSeenAt)
            assertEquals("192.168.1.31", device.latestIp)
            assertEquals("Office printer._ipp._tcp", device.latestServiceName)
            assertEquals(2, history.size)
        } finally {
            database.close()
        }
    }

    @Test
    fun persistsAccessControlAuditEvents() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            NetworkDatabase::class.java,
        ).allowMainThreadQueries().build()

        try {
            val repository = DeviceRepository(database)
            repository.recordAccessControlEvent(
                AccessControlEventEntity(
                    deviceId = "mac:aa:bb:cc:dd:ee:ff",
                    deviceLabel = "Office laptop",
                    macAddress = "aa:bb:cc:dd:ee:ff",
                    action = "BLOCK",
                    state = "APPLIED",
                    occurredAt = 1_000L,
                    detail = "Router accepted the policy; traffic verification unavailable.",
                ),
            )

            val event = repository.observeRecentAccessControlEvents().first().single()
            assertEquals("Office laptop", event.deviceLabel)
            assertEquals("BLOCK", event.action)
            assertEquals("APPLIED", event.state)
            assertEquals(1_000L, event.occurredAt)
        } finally {
            database.close()
        }
    }

    @Test
    fun mergesRecentIpOnlyAndMacObservationsWithoutCreatingDuplicateDevices() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            NetworkDatabase::class.java,
        ).allowMainThreadQueries().build()

        try {
            val repository = DeviceRepository(database)
            repository.record(
                DeviceObservation(
                    source = "Active TCP scan",
                    observedAt = 10_000L,
                    ipAddress = "192.168.1.40",
                    serviceName = "80/HTTP",
                ),
            )
            repository.updateName("ip:192.168.1.40", "Office printer")
            repository.updateGroup("ip:192.168.1.40", "Office")
            val recordedIdentity = repository.record(
                DeviceObservation(
                    source = "Android neighbor table",
                    observedAt = 11_000L,
                    ipAddress = "192.168.1.40",
                    macAddress = "aa:bb:cc:dd:ee:40",
                ),
            )

            assertEquals("mac:aa:bb:cc:dd:ee:40", recordedIdentity)
            val devices = repository.observeDevices().first()
            val history = repository.observeHistory("mac:aa:bb:cc:dd:ee:40").first()

            assertEquals(1, devices.size)
            assertEquals("mac:aa:bb:cc:dd:ee:40", devices.single().id)
            assertEquals("Office printer", devices.single().customName)
            assertEquals("Office", devices.single().groupName)
            assertEquals(2, history.size)
        } finally {
            database.close()
        }
    }

    @Test
    fun mergesUnambiguousRecentWeakIdentitiesWithTheSameIp() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            NetworkDatabase::class.java,
        ).allowMainThreadQueries().build()

        try {
            val repository = DeviceRepository(database)
            repository.record(
                DeviceObservation(
                    source = "mDNS",
                    observedAt = 40_000L,
                    ipAddress = "192.168.1.44",
                    hostname = "printer.local",
                ),
            )
            val identityKey = repository.record(
                DeviceObservation(
                    source = "Active TCP scan",
                    observedAt = 41_000L,
                    ipAddress = "192.168.1.44",
                ),
            )

            assertEquals("host:printer.local", identityKey)
            assertEquals(1, repository.observeDevices().first().size)
            assertEquals(2, repository.observeHistory(identityKey).first().size)
        } finally {
            database.close()
        }
    }

    @Test
    fun mergesMaclessObservationIntoOneUnambiguousRecentMacIdentity() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            NetworkDatabase::class.java,
        ).allowMainThreadQueries().build()

        try {
            val repository = DeviceRepository(database)
            repository.record(
                DeviceObservation(
                    source = "Android neighbor table",
                    observedAt = 20_000L,
                    ipAddress = "192.168.1.41",
                    macAddress = "aa:bb:cc:dd:ee:41",
                ),
            )
            val identityKey = repository.record(
                DeviceObservation(
                    source = "SSDP/UPnP",
                    observedAt = 21_000L,
                    ipAddress = "192.168.1.41",
                    serviceName = "Sky Q (SSDP/UPnP)",
                ),
            )

            assertEquals("mac:aa:bb:cc:dd:ee:41", identityKey)
            assertEquals(1, repository.observeDevices().first().size)
            assertEquals(2, repository.observeHistory(identityKey).first().size)
        } finally {
            database.close()
        }
    }

    @Test
    fun keepsAmbiguousMaclessObservationSeparate() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            NetworkDatabase::class.java,
        ).allowMainThreadQueries().build()

        try {
            val repository = DeviceRepository(database)
            repository.record(
                DeviceObservation(
                    source = "router",
                    observedAt = 30_000L,
                    ipAddress = "192.168.1.42",
                    macAddress = "aa:bb:cc:dd:ee:42",
                ),
            )
            repository.record(
                DeviceObservation(
                    source = "router",
                    observedAt = 30_500L,
                    ipAddress = "192.168.1.42",
                    macAddress = "aa:bb:cc:dd:ee:43",
                ),
            )
            repository.record(
                DeviceObservation(
                    source = "SSDP/UPnP",
                    observedAt = 31_000L,
                    ipAddress = "192.168.1.42",
                ),
            )

            assertEquals(3, repository.observeDevices().first().size)
        } finally {
            database.close()
        }
    }

    @Test
    fun doesNotMergeAnOldReusedIpFromHistoricalObservation() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            NetworkDatabase::class.java,
        ).allowMainThreadQueries().build()

        try {
            val repository = DeviceRepository(database)
            repository.record(
                DeviceObservation(
                    source = "router",
                    observedAt = 1_000L,
                    ipAddress = "192.168.1.45",
                    macAddress = "aa:bb:cc:dd:ee:45",
                ),
            )
            repository.record(
                DeviceObservation(
                    source = "router",
                    observedAt = 300_000L,
                    ipAddress = "192.168.1.46",
                    macAddress = "aa:bb:cc:dd:ee:45",
                ),
            )
            repository.record(
                DeviceObservation(
                    source = "Active TCP scan",
                    observedAt = 301_000L,
                    ipAddress = "192.168.1.45",
                ),
            )

            assertEquals(2, repository.observeDevices().first().size)
        } finally {
            database.close()
        }
    }
}
