package com.wifidashboard.router.openwrt

import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

fun interface UbusTransport {
    @Throws(IOException::class)
    fun post(endpoint: OpenWrtEndpoint, body: JSONObject): JSONObject
}

class HttpUbusTransport(
    private val connectTimeoutMillis: Int = 5_000,
    private val readTimeoutMillis: Int = 10_000,
) : UbusTransport {
    override fun post(endpoint: OpenWrtEndpoint, body: JSONObject): JSONObject {
        val connection = URL(endpoint.ubusUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = connectTimeoutMillis
        connection.readTimeout = readTimeoutMillis
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Connection", "close")

        try {
            connection.outputStream.use { output ->
                output.write(body.toString().toByteArray(Charsets.UTF_8))
            }
            val statusCode = connection.responseCode
            val responseStream = if (statusCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            val responseText = responseStream?.use(::readBounded)
                ?: throw IOException("Router returned HTTP $statusCode without a response body")
            if (statusCode !in 200..299) {
                throw IOException("Router returned HTTP $statusCode")
            }
            return try {
                JSONObject(responseText)
            } catch (error: JSONException) {
                throw IOException("Router returned an invalid JSON-RPC response", error)
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun readBounded(input: java.io.InputStream): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var totalBytes = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            totalBytes += count
            if (totalBytes > MAX_RESPONSE_BYTES) {
                throw IOException("Router response exceeded the 1 MiB safety limit")
            }
            output.write(buffer, 0, count)
        }
        return output.toString(Charsets.UTF_8.name())
    }

    companion object {
        private const val MAX_RESPONSE_BYTES = 1024 * 1024
    }
}
