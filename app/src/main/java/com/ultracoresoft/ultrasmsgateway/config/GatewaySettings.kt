package com.ultracoresoft.ultrasmsgateway.config

import android.content.Context
import androidx.core.content.edit

/** What the user configures on the only screen of the app. */
class GatewaySettings(context: Context, private val cipher: TokenCipher = TokenCipher()) {

    private val preferences = context.getSharedPreferences("gateway_settings", Context.MODE_PRIVATE)

    /** WebSocket URL shown in the dashboard, e.g. wss://sms.example.com/v1/device/ws. */
    var serverUrl: String
        get() = preferences.getString(KEY_SERVER_URL, "").orEmpty()
        set(value) = preferences.edit { putString(KEY_SERVER_URL, value.trim()) }

    val hasToken: Boolean
        get() = preferences.contains(KEY_TOKEN)

    /** The device token, decrypted. Null when none is saved or it can no longer be decrypted. */
    fun token(): String? = preferences.getString(KEY_TOKEN, null)?.let(cipher::decrypt)

    fun saveToken(token: String) = preferences.edit { putString(KEY_TOKEN, cipher.encrypt(token.trim())) }

    /** SIM used to send, as an Android subscription id; [DEFAULT_SIM] lets the system choose. */
    var subscriptionId: Int
        get() = preferences.getInt(KEY_SUBSCRIPTION, DEFAULT_SIM)
        set(value) = preferences.edit { putInt(KEY_SUBSCRIPTION, value) }

    /** True while the user wants the gateway running; read at boot to start it again. */
    var enabled: Boolean
        get() = preferences.getBoolean(KEY_ENABLED, false)
        set(value) = preferences.edit { putBoolean(KEY_ENABLED, value) }

    val isConfigured: Boolean
        get() = isValidServerUrl(serverUrl) && hasToken

    companion object {
        const val DEFAULT_SIM = -1
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_TOKEN = "device_token"
        private const val KEY_SUBSCRIPTION = "subscription_id"
        private const val KEY_ENABLED = "enabled"

        fun isValidServerUrl(url: String): Boolean {
            val trimmed = url.trim()
            return (trimmed.startsWith("wss://") || trimmed.startsWith("ws://")) && trimmed.length > "wss://".length
        }

        fun isValidToken(token: String): Boolean = token.trim().startsWith("usgd_") && token.trim().length > 20
    }
}
