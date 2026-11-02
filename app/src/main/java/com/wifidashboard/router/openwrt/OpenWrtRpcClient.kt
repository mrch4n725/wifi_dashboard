package com.wifidashboard.router.openwrt

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

class OpenWrtRpcClient(
    private val endpoint: OpenWrtEndpoint,
    private val transport: UbusTransport,
) {
    private val nextRequestId = AtomicInteger(1)

    fun login(credentials: RouterCredentials): String {
        val arguments = JSONObject()
            .put("username", credentials.username)
            .put("password", credentials.password)
        val response = call(ANONYMOUS_SESSION, "session", "login", arguments)
        val token = response.stringOrNull("ubus_rpc_session")
        if (token == null) {
            throw OpenWrtRpcException("Router accepted the login request but returned no session")
        }
        return token
    }

    fun listObjects(): JSONObject {
        val response = request("list", JSONArray().put("*"))
        return response as? JSONObject
            ?: throw OpenWrtRpcException("Router returned an unexpected capability list")
    }

    fun call(
        sessionToken: String,
        objectName: String,
        methodName: String,
        arguments: JSONObject = JSONObject(),
    ): JSONObject {
        val params = JSONArray()
            .put(sessionToken)
            .put(objectName)
            .put(methodName)
            .put(arguments)
        return request("call", params) as? JSONObject
            ?: throw OpenWrtRpcException("$objectName.$methodName returned an unexpected response")
    }

    private fun request(method: String, params: JSONArray): Any {
        val id = nextRequestId.getAndIncrement()
        val request = JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", id)
            .put("method", method)
            .put("params", params)
        val response = transport.post(endpoint, request)
        if (response.optString("jsonrpc") != "2.0") {
            throw OpenWrtRpcException("Router returned an unsupported JSON-RPC version")
        }
        val responseId = response.opt("id") as? Number
        if (
            responseId == null ||
            responseId.toLong() != id.toLong() ||
            responseId.toDouble() != id.toDouble()
        ) {
            throw OpenWrtRpcException("Router returned a mismatched JSON-RPC request id")
        }
        if (response.has("error")) {
            val error = response.optJSONObject("error")
            val message = error?.optString("message")?.takeIf(String::isNotBlank)
            throw OpenWrtRpcException(message ?: "Router rejected the $method request")
        }
        if (!response.has("result")) {
            throw OpenWrtRpcException("Router returned no result for the $method request")
        }
        return unwrapResult(method, response.opt("result"))
    }

    private fun unwrapResult(method: String, rawResult: Any?): Any {
        if (rawResult is JSONArray && rawResult.length() > 0) {
            val status = rawResult.optInt(0, Int.MIN_VALUE)
            if (status != Int.MIN_VALUE) {
                if (status != 0) {
                    throw OpenWrtRpcException(
                        "Router returned ubus status $status for the $method request",
                    )
                }
                return rawResult.opt(1).takeUnless { it === JSONObject.NULL }
                    ?: JSONObject()
            }
        }
        return rawResult ?: throw OpenWrtRpcException("Router returned an empty result")
    }

    companion object {
        const val ANONYMOUS_SESSION = "00000000000000000000000000000000"
    }
}

class OpenWrtRpcException(
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)

internal fun JSONObject.stringOrNull(key: String): String? =
    optString(key).takeIf { it.isNotBlank() && it != "null" }

internal fun JSONObject.longOrNull(key: String): Long? =
    signedLongOrNull(key)?.takeIf { it >= 0 }

internal fun JSONObject.signedLongOrNull(key: String): Long? =
    when (val value = opt(key)) {
        is Byte, is Short, is Int, is Long ->
            (value as Number).toLong()
        is Float, is Double -> {
            val number = (value as Number).toDouble()
            number.takeIf { it.isFinite() && it % 1.0 == 0.0 }
                ?.toLong()
        }
        else -> null
    }

internal fun JSONObject.booleanOrNull(key: String): Boolean? =
    opt(key) as? Boolean

internal fun JSONArray.jsonObjects(): List<JSONObject> =
    (0 until length()).mapNotNull(::optJSONObject)
