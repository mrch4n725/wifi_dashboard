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
}
