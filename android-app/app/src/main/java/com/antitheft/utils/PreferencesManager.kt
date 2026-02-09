package com.antitheft.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.UUID

/**
 * Manages encrypted storage for sensitive application data
 * Uses EncryptedSharedPreferences with AES256_GCM encryption
 */
class PreferencesManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        Constants.PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    /**
     * Server URL (IP address or hostname)
     */
    var serverUrl: String
        get() = sharedPreferences.getString(Constants.KEY_SERVER_URL, "") ?: ""
        set(value) = sharedPreferences.edit().putString(Constants.KEY_SERVER_URL, value).apply()

    /**
     * Server port (default 3000)
     */
    var serverPort: Int
        get() = sharedPreferences.getInt(Constants.KEY_SERVER_PORT, 3000)
        set(value) = sharedPreferences.edit().putInt(Constants.KEY_SERVER_PORT, value).apply()

    /**
     * Authentication token for API requests
     * Auto-generates a UUID if not already set
     */
    var authToken: String
        get() {
            var token = sharedPreferences.getString(Constants.KEY_AUTH_TOKEN, null)
            if (token == null) {
                token = UUID.randomUUID().toString()
                sharedPreferences.edit().putString(Constants.KEY_AUTH_TOKEN, token).apply()
            }
            return token
        }
        set(value) = sharedPreferences.edit().putString(Constants.KEY_AUTH_TOKEN, value).apply()

    /**
     * Unique device identifier
     * Auto-generates a UUID if not already set
     */
    var deviceId: String
        get() {
            var id = sharedPreferences.getString(Constants.KEY_DEVICE_ID, null)
            if (id == null) {
                id = UUID.randomUUID().toString()
                sharedPreferences.edit().putString(Constants.KEY_DEVICE_ID, id).apply()
            }
            return id
        }
        set(value) = sharedPreferences.edit().putString(Constants.KEY_DEVICE_ID, value).apply()

    /**
     * Returns the complete base URL for API requests
     * Format: https://serverUrl:serverPort
     */
    fun getBaseUrl(): String {
        return if (serverUrl.isNotEmpty() && serverPort > 0) {
            "https://$serverUrl:$serverPort"
        } else {
            ""
        }
    }

    /**
     * Returns the complete WebSocket URL
     * Format: wss://serverUrl:serverPort/ws
     */
    fun getWebSocketUrl(): String {
        return if (serverUrl.isNotEmpty() && serverPort > 0) {
            "wss://$serverUrl:$serverPort${Constants.WS_PATH}"
        } else {
            ""
        }
    }

    /**
     * Checks if server configuration is complete
     * @return true if both server URL and port are set
     */
    fun isConfigured(): Boolean {
        return serverUrl.isNotEmpty() && serverPort > 0
    }

    /**
     * Clears all stored preferences (for testing or reset)
     */
    fun clearAll() {
        sharedPreferences.edit().clear().apply()
    }
}
