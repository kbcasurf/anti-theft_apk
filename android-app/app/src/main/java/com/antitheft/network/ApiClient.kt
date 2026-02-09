package com.antitheft.network

import android.util.Log
import com.antitheft.utils.Constants
import com.antitheft.utils.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * API client for communicating with the Anti-Theft server
 * Handles HTTPS requests with self-signed certificates
 */
class ApiClient(private val prefsManager: PreferencesManager) {

    private val client: OkHttpClient by lazy {
        // Create trust manager that trusts all certificates (for self-signed certificates)
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustAllCerts, java.security.SecureRandom())

        OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }  // Accept all hostnames
            .connectTimeout(Constants.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(Constants.READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Performs check-in with the server to see if tracking should be activated
     * @return CheckInResponse with activation status and message
     */
    suspend fun checkIn(): CheckInResponse = withContext(Dispatchers.IO) {
        val baseUrl = prefsManager.getBaseUrl()
        if (baseUrl.isEmpty()) {
            return@withContext CheckInResponse(false, "Server not configured")
        }

        val jsonObject = JSONObject().apply {
            put("device_id", prefsManager.deviceId)
            put("timestamp", System.currentTimeMillis())
        }

        val requestBody = jsonObject.toString()
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("$baseUrl${Constants.ENDPOINT_CHECK_IN}")
            .post(requestBody)
            .addHeader("Authorization", "Bearer ${prefsManager.authToken}")
            .build()

        try {
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: "{}"
            val json = JSONObject(body)

            if (response.isSuccessful) {
                Log.d(TAG, "Check-in successful: $body")
                CheckInResponse(
                    activated = json.optBoolean("activated", false),
                    message = json.optString("message", "Success")
                )
            } else {
                Log.w(TAG, "Check-in failed with code: ${response.code}")
                CheckInResponse(false, "Error: ${response.code}")
            }
        } catch (e: IOException) {
            Log.e(TAG, "Check-in network error", e)
            CheckInResponse(false, "Network error: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Check-in unexpected error", e)
            CheckInResponse(false, "Error: ${e.message}")
        }
    }

    /**
     * Tests connection to the server status endpoint
     * @return Pair<Boolean, String> (success, message)
     */
    suspend fun testConnection(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val baseUrl = prefsManager.getBaseUrl()
        if (baseUrl.isEmpty()) {
            return@withContext Pair(false, "Server not configured")
        }

        val request = Request.Builder()
            .url("$baseUrl${Constants.ENDPOINT_STATUS}")
            .get()
            .build()

        try {
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""

            if (response.isSuccessful) {
                Log.d(TAG, "Connection test successful: $body")
                Pair(true, "Connected successfully")
            } else {
                Log.w(TAG, "Connection test failed with code: ${response.code}")
                Pair(false, "Connection failed: ${response.code}")
            }
        } catch (e: IOException) {
            Log.e(TAG, "Connection test network error", e)
            Pair(false, "Cannot reach server: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Connection test unexpected error", e)
            Pair(false, "Error: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "ApiClient"
    }
}

/**
 * Response from the check-in API endpoint
 * @param activated Whether tracking should be activated
 * @param message Additional message from server
 */
data class CheckInResponse(
    val activated: Boolean,
    val message: String
)
