package com.wifidashboard.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.ext.SdkExtensions
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresExtension
import androidx.core.content.ContextCompat
import com.wifidashboard.storage.DeviceObservation
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Inet6Address
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

sealed interface DiscoveryEvent {
    data class Found(val observation: DeviceObservation) : DiscoveryEvent
    data class Warning(val message: String) : DiscoveryEvent
}

class MdnsDiscovery(context: Context) {
    private val appContext = context.applicationContext
    private val nsdManager = appContext.getSystemService(NsdManager::class.java)
    private val wifiManager = appContext.getSystemService(WifiManager::class.java)

    fun scan(durationMillis: Long = SCAN_DURATION_MILLIS): Flow<DiscoveryEvent> = flow {
        require(durationMillis in 1..MAX_SCAN_DURATION_MILLIS) {
            "mDNS scan duration must be between 1 ms and 30 seconds"
        }
        val lock = wifiManager?.createMulticastLock("wifi-dashboard-mdns")
        lock?.setReferenceCounted(false)
        lock?.acquire()
        try {
            withTimeoutOrNull(durationMillis) {
                discoverResults().collect { emit(it) }
            }
        } finally {
            if (lock?.isHeld == true) lock.release()
        }
    }

    private fun discoverResults(): Flow<DiscoveryEvent> = callbackFlow {
        val listeners = ConcurrentHashMap.newKeySet<NsdManager.DiscoveryListener>()
        val serviceInfoCallbacks = ConcurrentHashMap.newKeySet<NsdManager.ServiceInfoCallback>()
        val resolvedServices = ConcurrentHashMap.newKeySet<String>()
        val emittedServiceStates = ConcurrentHashMap.newKeySet<String>()
        val serviceCount = AtomicInteger()
        val resultLimitWarned = AtomicBoolean()

        fun stopListener(listener: NsdManager.DiscoveryListener) {
            if (listeners.remove(listener)) {
                try {
                    nsdManager.stopServiceDiscovery(listener)
                } catch (error: IllegalArgumentException) {
                    Log.w(TAG, "mDNS discovery listener was already stopped", error)
                }
            }
        }

        fun unregisterInfoCallback(callback: NsdManager.ServiceInfoCallback) {
            if (serviceInfoCallbacks.remove(callback)) {
                if (supportsServiceInfoCallback()) {
                    try {
                        unregisterServiceInfoCallbackApi34(callback)
                    } catch (error: IllegalArgumentException) {
                        Log.w(TAG, "mDNS service information callback was already unregistered", error)
                    }
                } else {
                    Log.e(TAG, "Cannot unregister mDNS callback on an unsupported Android extension")
                }
            }
        }

        fun emitService(serviceInfo: NsdServiceInfo) {
            val observation = serviceObservation(serviceInfo)
            val stateKey = listOf(
                observation.hostname,
                observation.serviceName,
                observation.ipAddress,
                observation.detail,
                observation.source,
            ).joinToString("|")
            if (emittedServiceStates.add(stateKey)) {
                trySend(DiscoveryEvent.Found(observation))
            }
        }

        SERVICE_TYPES.forEach { serviceType ->
            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(type: String) = Unit

                override fun onServiceFound(service: NsdServiceInfo) {
                    if (serviceCount.incrementAndGet() > MAX_SERVICES) {
                        serviceCount.decrementAndGet()
                        if (resultLimitWarned.compareAndSet(false, true)) {
                            trySend(DiscoveryEvent.Warning("Discovery result limit reached"))
                        }
                        return
                    }
                    val serviceKey = "${service.serviceName}|${service.serviceType}"
                    if (service.serviceName.isBlank() || !resolvedServices.add(serviceKey)) {
                        serviceCount.decrementAndGet()
                        return
                    }
                    try {
                        if (supportsServiceInfoCallback()) {
                            val callback = object : NsdManager.ServiceInfoCallback {
                                override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                                    serviceInfoCallbacks.remove(this)
                                    trySend(
                                        DiscoveryEvent.Warning(
                                            "Android could not resolve one advertised mDNS service",
                                        ),
                                    )
                                }

                                override fun onServiceUpdated(serviceInfo: NsdServiceInfo) {
                                    emitService(serviceInfo)
                                }

                                override fun onServiceLost() {
                                    serviceInfoCallbacks.remove(this)
                                }

                                override fun onServiceInfoCallbackUnregistered() {
                                    serviceInfoCallbacks.remove(this)
                                }
                            }
                            serviceInfoCallbacks += callback
                            registerServiceInfoCallbackApi34(service, callback)
                        } else {
                            resolveWithLegacyApi(
                                service,
                                onResolved = ::emitService,
                                onFailure = {
                                    trySend(
                                        DiscoveryEvent.Warning(
                                            "Android could not resolve one advertised mDNS service",
                                        ),
                                    )
                                },
                            )
                        }
                    } catch (error: IllegalArgumentException) {
                        trySend(
                            DiscoveryEvent.Warning(
                                "Android rejected an mDNS service resolution request",
                            ),
                        )
                        Log.w(TAG, "Unable to resolve discovered mDNS service", error)
                    } catch (error: SecurityException) {
                        trySend(
                            DiscoveryEvent.Warning(
                                "Android denied permission to resolve an advertised mDNS service",
                            ),
                        )
                        Log.w(TAG, "Permission denied resolving an mDNS service", error)
                    }
                }

                override fun onServiceLost(service: NsdServiceInfo) = Unit

                override fun onDiscoveryStopped(type: String) = Unit

                override fun onStartDiscoveryFailed(type: String, errorCode: Int) {
                    stopListener(this)
                    trySend(DiscoveryEvent.Warning("Android could not browse $type services"))
                    if (listeners.isEmpty()) {
                        close(IllegalStateException("Android could not start any mDNS browse sessions"))
                    }
                }

                override fun onStopDiscoveryFailed(type: String, errorCode: Int) {
                    stopListener(this)
                    trySend(DiscoveryEvent.Warning("Android could not stop browsing $type services"))
                }
            }

            listeners += listener
            try {
                nsdManager.discoverServices(
                    serviceType,
                    NsdManager.PROTOCOL_DNS_SD,
                    listener,
                )
            } catch (error: IllegalArgumentException) {
                listeners.remove(listener)
                trySend(DiscoveryEvent.Warning("Android rejected the $serviceType mDNS browse"))
                Log.w(TAG, "Unable to start mDNS browse for $serviceType", error)
            } catch (error: SecurityException) {
                listeners.remove(listener)
                trySend(DiscoveryEvent.Warning("Android denied local network discovery permission"))
                Log.w(TAG, "Local network discovery permission denied", error)
            }
        }
        if (listeners.isEmpty()) {
            close(IllegalStateException("No mDNS browse session could be started"))
        }

        awaitClose {
            listeners.toList().forEach(::stopListener)
            serviceInfoCallbacks.toList().forEach(::unregisterInfoCallback)
        }
    }

    @Suppress("DEPRECATION")
    private fun resolveWithLegacyApi(
        service: NsdServiceInfo,
        onResolved: (NsdServiceInfo) -> Unit,
        onFailure: () -> Unit,
    ) {
        nsdManager.resolveService(
            service,
            object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    onFailure()
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    onResolved(serviceInfo)
                }
            },
        )
    }

    private fun serviceObservation(service: NsdServiceInfo): DeviceObservation {
        val addresses = serviceAddresses(service)
        val address = addresses.firstOrNull { candidate ->
            !candidate.isAnyLocalAddress &&
                !candidate.isLoopbackAddress &&
                !candidate.isMulticastAddress &&
                (candidate !is Inet6Address || !candidate.isLinkLocalAddress)
        }?.hostAddress
        val port = service.port.takeIf { it > 0 }
        return DeviceObservation(
            source = "mDNS ${service.serviceType}",
            observedAt = System.currentTimeMillis(),
            ipAddress = address,
            hostname = resolvedHostname(service),
            serviceName = service.serviceName,
            detail = port?.let { "Advertised on port $it" },
        )
    }

    private fun serviceAddresses(service: NsdServiceInfo) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            api34HostAddresses(service)
        } else {
            legacyHostAddress(service)?.let(::listOf).orEmpty()
        }

    private fun supportsServiceInfoCallback(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            SdkExtensions.getExtensionVersion(Build.VERSION_CODES.TIRAMISU) >= 7

    private fun supportsResolvedHostname(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            SdkExtensions.getExtensionVersion(Build.VERSION_CODES.TIRAMISU) >= 17

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @RequiresExtension(extension = Build.VERSION_CODES.TIRAMISU, version = 7)
    private fun registerServiceInfoCallbackApi34(
        service: NsdServiceInfo,
        callback: NsdManager.ServiceInfoCallback,
    ) {
        nsdManager.registerServiceInfoCallback(
            service,
            ContextCompat.getMainExecutor(appContext),
            callback,
        )
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @RequiresExtension(extension = Build.VERSION_CODES.TIRAMISU, version = 7)
    private fun unregisterServiceInfoCallbackApi34(callback: NsdManager.ServiceInfoCallback) {
        nsdManager.unregisterServiceInfoCallback(callback)
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun api34HostAddresses(service: NsdServiceInfo) =
        service.hostAddresses.filterNot { it is Inet6Address && it.isLinkLocalAddress }

    private fun resolvedHostname(service: NsdServiceInfo): String? =
        if (supportsResolvedHostname()) api34Hostname(service) else null

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @RequiresExtension(extension = Build.VERSION_CODES.TIRAMISU, version = 17)
    private fun api34Hostname(service: NsdServiceInfo) = service.hostname

    @Suppress("DEPRECATION")
    private fun legacyHostAddress(service: NsdServiceInfo) = service.host

    companion object {
        private const val TAG = "MdnsDiscovery"
        const val SCAN_DURATION_MILLIS = 12_000L
        private const val MAX_SCAN_DURATION_MILLIS = 30_000L
        private const val MAX_SERVICES = 128
        private val SERVICE_TYPES = listOf(
            "_http._tcp.",
            "_ipp._tcp.",
            "_airplay._tcp.",
            "_googlecast._tcp.",
            "_smb._tcp.",
            "_ssh._tcp.",
        )
    }
}
