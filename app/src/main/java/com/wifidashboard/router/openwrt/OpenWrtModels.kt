package com.wifidashboard.router.openwrt

import java.net.URI
import java.net.URISyntaxException

class OpenWrtEndpoint private constructor(
    val origin: String,
) {
    val ubusUrl: String
        get() = "$origin/ubus"

    companion object {
        fun parse(value: String, allowCleartext: Boolean): OpenWrtEndpoint {
            require(value.length <= 2048) { "Router address is too long" }
            val uri = try {
                URI(value.trim().trimEnd('/'))
            } catch (error: URISyntaxException) {
                throw IllegalArgumentException("Enter a valid router URL", error)
            }
            val scheme = uri.scheme?.lowercase()
            require(scheme == "http" || scheme == "https") {
                "Router URL must start with http:// or https://"
            }
            require(scheme != "http" || allowCleartext) {
                "Cleartext HTTP requires explicit confirmation"
            }
            require(!uri.host.isNullOrBlank()) { "Router URL must include a host" }
            require(uri.rawUserInfo == null) { "Do not put credentials in the router URL" }
            require(uri.rawQuery == null && uri.rawFragment == null) {
                "Router URL must not include a query or fragment"
            }
            require(uri.rawPath.isNullOrEmpty() || uri.rawPath == "/") {
                "Enter the router origin only, without a path"
            }
            require(uri.port == -1 || uri.port in 1..65535) { "Router port is invalid" }

            val normalized = URI(
                scheme,
                null,
                uri.host,
                uri.port,
                null,
                null,
                null,
            ).toASCIIString().removeSuffix("/")
            return OpenWrtEndpoint(normalized)
        }
    }
}

data class RouterCredentials(val username: String, val password: String) {
    init {
        require(username.isNotBlank()) { "Router username is required" }
        require(password.isNotBlank()) { "Router password is required" }
    }
}

data class UbusMethod(val objectName: String, val methodName: String) {
    override fun toString(): String = "$objectName.$methodName"
}

data class RouterCapabilities(
    val detected: Boolean,
    val methods: Set<UbusMethod>,
    val detectionMessage: String? = null,
) {
    fun supports(objectName: String, methodName: String): Boolean =
        detected && UbusMethod(objectName, methodName) in methods
}

data class RouterInterface(
    val name: String,
    val protocol: String?,
    val device: String?,
    val isUp: Boolean?,
    val addresses: List<String>,
)

data class RouterClient(
    val macAddress: String?,
    val ipAddress: String?,
    val hostname: String?,
    val source: String,
    val details: String?,
)

data class RouterSnapshot(
    val model: String?,
    val firmware: String?,
    val uptimeSeconds: Long?,
    val interfaces: List<RouterInterface>?,
    val dhcpLeases: List<RouterClient>,
    val wirelessClients: List<RouterClient>,
    val capabilities: RouterCapabilities,
    val issues: List<String>,
)
