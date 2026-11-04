package com.wifidashboard.discovery

import android.content.Context

data class DiscoverySettings(
    val concurrency: Int = ActiveTcpScanner.DEFAULT_CONCURRENCY,
    val connectTimeoutMillis: Int = ActiveTcpScanner.DEFAULT_CONNECT_TIMEOUT_MILLIS,
    val maxDurationMillis: Long = ActiveTcpScanner.DEFAULT_MAX_DURATION_MILLIS,
    val selectedSubnetCidr: String? = null,
) {
    init {
        require(concurrency in 1..ActiveTcpScanner.MAX_CONCURRENCY) {
            "Scan concurrency must be between 1 and ${ActiveTcpScanner.MAX_CONCURRENCY}"
        }
        require(
            connectTimeoutMillis in
                ActiveTcpScanner.MIN_CONNECT_TIMEOUT_MILLIS..ActiveTcpScanner.MAX_CONNECT_TIMEOUT_MILLIS,
        ) {
            "TCP timeout must be between ${ActiveTcpScanner.MIN_CONNECT_TIMEOUT_MILLIS} and " +
                "${ActiveTcpScanner.MAX_CONNECT_TIMEOUT_MILLIS} ms"
        }
        require(maxDurationMillis in
            ActiveTcpScanner.MIN_MAX_DURATION_MILLIS..ActiveTcpScanner.MAX_MAX_DURATION_MILLIS
        ) {
            "Active scan duration must be between ${ActiveTcpScanner.MIN_MAX_DURATION_MILLIS} and " +
                "${ActiveTcpScanner.MAX_MAX_DURATION_MILLIS} ms"
        }
    }
}

class DiscoverySettingsStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun load(): DiscoverySettings = DiscoverySettings(
        concurrency = preferences.getInt(KEY_CONCURRENCY, ActiveTcpScanner.DEFAULT_CONCURRENCY),
        connectTimeoutMillis = preferences.getInt(
            KEY_CONNECT_TIMEOUT,
            ActiveTcpScanner.DEFAULT_CONNECT_TIMEOUT_MILLIS,
        ),
        maxDurationMillis = preferences.getLong(
            KEY_MAX_DURATION,
            ActiveTcpScanner.DEFAULT_MAX_DURATION_MILLIS,
        ),
        selectedSubnetCidr = preferences.getString(KEY_SELECTED_SUBNET, null)
            ?.trim()
            ?.takeIf(String::isNotEmpty),
    )

    fun save(settings: DiscoverySettings) {
        val saved = preferences.edit()
            .putInt(KEY_CONCURRENCY, settings.concurrency)
            .putInt(KEY_CONNECT_TIMEOUT, settings.connectTimeoutMillis)
            .putLong(KEY_MAX_DURATION, settings.maxDurationMillis)
            .apply {
                if (settings.selectedSubnetCidr == null) {
                    remove(KEY_SELECTED_SUBNET)
                } else {
                    putString(KEY_SELECTED_SUBNET, settings.selectedSubnetCidr.trim())
                }
            }
            .commit()
        if (!saved) throw IllegalStateException("Android could not save discovery settings")
    }

    companion object {
        private const val PREFERENCES_NAME = "discovery_settings"
        private const val KEY_CONCURRENCY = "concurrency"
        private const val KEY_CONNECT_TIMEOUT = "connect_timeout_millis"
        private const val KEY_MAX_DURATION = "max_duration_millis"
        private const val KEY_SELECTED_SUBNET = "selected_subnet_cidr"
    }
}
