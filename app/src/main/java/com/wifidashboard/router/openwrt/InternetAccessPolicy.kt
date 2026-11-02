package com.wifidashboard.router.openwrt

import java.security.MessageDigest
import java.util.Locale

enum class InternetAccessAction { BLOCK, RESTORE }

enum class AccessControlState { REQUESTED, APPLIED, VERIFIED, ROLLED_BACK, FAILED }

data class InternetAccessPolicy(
    val sectionName: String,
    val macAddress: String,
    val sourceZone: String = "lan",
    val destinationZone: String = "wan",
    val target: String = "REJECT",
) {
    companion object {
        fun forDevice(macAddress: String, sourceZone: String): InternetAccessPolicy {
            val normalized = macAddress.trim().lowercase(Locale.ROOT).replace('-', ':')
            require(normalized.matches(Regex("([0-9a-f]{2}:){5}[0-9a-f]{2}"))) {
                "A valid router-reported MAC address is required for internet access control"
            }
            require(normalized != "00:00:00:00:00:00" && normalized != "ff:ff:ff:ff:ff:ff") {
                "The router did not report a usable device MAC address"
            }
            require(sourceZone.matches(Regex("[A-Za-z0-9_.-]{1,64}"))) {
                "The router did not report a usable firewall zone for this client"
            }
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(normalized.toByteArray(Charsets.UTF_8))
                .take(8)
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
            return InternetAccessPolicy("wifi_dashboard_$digest", normalized, sourceZone)
        }
    }
}

data class InternetAccessPreview(
    val policy: InternetAccessPolicy,
    val action: InternetAccessAction,
    val existingSection: Boolean,
    val firewallFingerprint: String,
)

data class InternetAccessOperation(
    val action: InternetAccessAction,
    val state: AccessControlState,
    val message: String,
)
