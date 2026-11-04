package com.wifidashboard.discovery

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DiscoverySettingsStoreTest {
    @Test
    fun settingsPersistAndAreReadByTheScannerConfiguration() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = DiscoverySettingsStore(context)
        val settings = DiscoverySettings(
            concurrency = 12,
            connectTimeoutMillis = 450,
            maxDurationMillis = 45_000,
            selectedSubnetCidr = "192.168.1.0/24",
        )

        store.save(settings)

        assertEquals(settings, store.load())
    }

    @Test
    fun rejectsOutOfRangeScanSettings() {
        assertThrows(IllegalArgumentException::class.java) {
            DiscoverySettings(concurrency = 100)
        }
        assertThrows(IllegalArgumentException::class.java) {
            DiscoverySettings(connectTimeoutMillis = 50)
        }
        assertThrows(IllegalArgumentException::class.java) {
            DiscoverySettings(maxDurationMillis = 1_000)
        }
    }
}
