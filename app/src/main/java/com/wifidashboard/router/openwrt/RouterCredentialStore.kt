package com.wifidashboard.router.openwrt

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONException
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class RouterCredentialStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    @Synchronized
    @Throws(GeneralSecurityException::class, JSONException::class)
    fun save(endpoint: OpenWrtEndpoint, credentials: RouterCredentials) {
        val alias = keyAlias(endpoint)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey(alias))
        cipher.updateAAD(endpoint.origin.toByteArray(StandardCharsets.UTF_8))
        val plaintext = JSONObject()
            .put("username", credentials.username)
            .put("password", credentials.password)
            .toString()
            .toByteArray(StandardCharsets.UTF_8)
        val encrypted = try {
            cipher.doFinal(plaintext)
        } finally {
            plaintext.fill(0)
        }
        val key = preferenceKey(endpoint)
        val stored = preferences.edit()
            .putString("$key.iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("$key.ciphertext", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .commit()
        if (!stored) throw IllegalStateException("Android could not save encrypted router credentials")
    }

    @Synchronized
    @Throws(GeneralSecurityException::class, JSONException::class)
    fun load(endpoint: OpenWrtEndpoint): RouterCredentials? {
        val key = preferenceKey(endpoint)
        val ivValue = preferences.getString("$key.iv", null)
        val ciphertextValue = preferences.getString("$key.ciphertext", null)
        if (ivValue == null && ciphertextValue == null) return null
        if (ivValue == null || ciphertextValue == null) {
            throw IllegalStateException("Saved router credentials are incomplete")
        }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val iv = try {
            Base64.decode(ivValue, Base64.NO_WRAP)
        } catch (error: IllegalArgumentException) {
            throw GeneralSecurityException("Saved credential data is malformed", error)
        }
        val ciphertext = try {
            Base64.decode(ciphertextValue, Base64.NO_WRAP)
        } catch (error: IllegalArgumentException) {
            throw GeneralSecurityException("Saved credential data is malformed", error)
        }
        cipher.init(
            Cipher.DECRYPT_MODE,
            getExistingKey(keyAlias(endpoint)),
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv),
        )
        cipher.updateAAD(endpoint.origin.toByteArray(StandardCharsets.UTF_8))
        val plaintext = cipher.doFinal(ciphertext)
        return try {
            val credentials = JSONObject(String(plaintext, StandardCharsets.UTF_8))
            RouterCredentials(
                username = credentials.getString("username"),
                password = credentials.getString("password"),
            )
        } catch (error: IllegalArgumentException) {
            throw GeneralSecurityException("Saved router credentials are invalid", error)
        } finally {
            plaintext.fill(0)
        }
    }

    @Synchronized
    @Throws(GeneralSecurityException::class)
    fun remove(endpoint: OpenWrtEndpoint) {
        val key = preferenceKey(endpoint)
        val removed = preferences.edit()
            .remove("$key.iv")
            .remove("$key.ciphertext")
            .commit()
        if (!removed) throw IllegalStateException("Android could not remove saved router credentials")
        val keyStore = loadKeyStore()
        if (keyStore.containsAlias(keyAlias(endpoint))) {
            keyStore.deleteEntry(keyAlias(endpoint))
        }
    }

    private fun getOrCreateKey(alias: String): SecretKey {
        val keyStore = loadKeyStore()
        val savedKey = keyStore.getKey(alias, null) as? SecretKey
        if (savedKey != null) return savedKey

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(AES_KEY_SIZE_BITS)
                .build(),
        )
        return generator.generateKey()
    }

    private fun getExistingKey(alias: String): SecretKey =
        loadKeyStore().getKey(alias, null) as? SecretKey
            ?: throw GeneralSecurityException("Android Keystore key for saved credentials is unavailable")

    private fun loadKeyStore(): KeyStore =
        KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }

    private fun keyAlias(endpoint: OpenWrtEndpoint): String =
        "wifi-dashboard-router-${sha256(endpoint.origin)}"

    private fun preferenceKey(endpoint: OpenWrtEndpoint): String =
        "router-${sha256(endpoint.origin)}"

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
            .joinToString(separator = "") { byte ->
                val valueByte = byte.toInt() and 0xff
                HEX_DIGITS[valueByte ushr 4].toString() + HEX_DIGITS[valueByte and 0x0f]
            }

    companion object {
        private const val PREFERENCES_NAME = "router_credentials"
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val AES_KEY_SIZE_BITS = 256
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val HEX_DIGITS = "0123456789abcdef"
    }
}
