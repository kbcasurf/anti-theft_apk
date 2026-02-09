package com.antitheft.network

import android.util.Log
import com.antitheft.media.LocationData
import com.antitheft.utils.Constants
import com.antitheft.utils.MessageSerializer
import com.antitheft.utils.PreferencesManager
import com.antitheft.utils.ServerMessage
import okhttp3.*
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlin.math.min
import kotlin.math.pow

/**
 * WebSocket client for real-time communication with the anti-theft server
 * Handles connection, reconnection, and streaming of location/video/audio data
 */
class WebSocketClient(
    private val prefsManager: PreferencesManager,
    private val callbacks: WebSocketCallbacks
) {

    private var client: OkHttpClient? = null
    private var webSocket: WebSocket? = null
    private val isConnected = AtomicBoolean(false)
    private val isConnecting = AtomicBoolean(false)
    private val shouldReconnect = AtomicBoolean(true)
    private val reconnectAttempts = AtomicInteger(0)

    /**
     * Connects to the WebSocket server
     */
    fun connect() {
        if (isConnected.get() || isConnecting.get()) {
            Log.w(TAG, "Already connected or connecting")
            return
        }

        isConnecting.set(true)
        Log.i(TAG, "Connecting to WebSocket server...")

        try {
            // Build WebSocket URL
            val baseUrl = prefsManager.getBaseUrl()
            val wsUrl = baseUrl.replace("https://", "wss://") + Constants.WS_PATH

            // Create OkHttp client with SSL trust
            client = createOkHttpClient()

            // Build request
            val request = Request.Builder()
                .url(wsUrl)
                .build()

            // Connect
            webSocket = client?.newWebSocket(request, WebSocketHandler())
            Log.d(TAG, "WebSocket connection initiated to: $wsUrl")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect WebSocket", e)
            isConnecting.set(false)
            scheduleReconnect()
        }
    }

    /**
     * Disconnects from the WebSocket server
     * @param allowReconnect If false, prevents automatic reconnection
     */
    fun disconnect(allowReconnect: Boolean = false) {
        Log.i(TAG, "Disconnecting WebSocket (allowReconnect: $allowReconnect)")
        shouldReconnect.set(allowReconnect)

        try {
            webSocket?.close(1000, "Client disconnect")
            webSocket = null
            isConnected.set(false)
            isConnecting.set(false)
        } catch (e: Exception) {
            Log.e(TAG, "Error closing WebSocket", e)
        }
    }

    /**
     * Sends location data to server
     */
    fun sendLocation(locationData: LocationData) {
        if (!isConnected.get()) {
            Log.w(TAG, "Cannot send location: not connected")
            return
        }

        try {
            val message = MessageSerializer.buildLocationMessage(locationData)
            webSocket?.send(message)
            Log.d(TAG, "Sent location: ${locationData.latitude}, ${locationData.longitude}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send location", e)
        }
    }

    /**
     * Sends video frame to server
     */
    fun sendVideoFrame(frameData: ByteArray) {
        if (!isConnected.get()) {
            Log.w(TAG, "Cannot send video frame: not connected")
            return
        }

        try {
            val message = MessageSerializer.buildVideoFrameMessage(frameData)
            webSocket?.send(message)
            Log.d(TAG, "Sent video frame: ${frameData.size} bytes")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send video frame", e)
        }
    }

    /**
     * Sends audio chunk to server
     */
    fun sendAudioChunk(audioData: ByteArray) {
        if (!isConnected.get()) {
            Log.w(TAG, "Cannot send audio chunk: not connected")
            return
        }

        try {
            val message = MessageSerializer.buildAudioChunkMessage(audioData)
            webSocket?.send(message)
            Log.d(TAG, "Sent audio chunk: ${audioData.size} bytes")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send audio chunk", e)
        }
    }

    /**
     * Returns current connection state
     */
    fun isConnected(): Boolean = isConnected.get()

    /**
     * Creates OkHttp client with SSL trust for self-signed certificates
     */
    private fun createOkHttpClient(): OkHttpClient {
        // Trust all certificates (for self-signed server certificates)
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustAllCerts, SecureRandom())

        return OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS) // No timeout for WebSocket
            .writeTimeout(10, TimeUnit.SECONDS)
            .pingInterval(30, TimeUnit.SECONDS) // Keep-alive ping
            .build()
    }

    /**
     * Schedules reconnection with exponential backoff
     */
    private fun scheduleReconnect() {
        if (!shouldReconnect.get()) {
            Log.i(TAG, "Reconnection disabled")
            return
        }

        val attempts = reconnectAttempts.incrementAndGet()
        if (attempts > MAX_RECONNECT_ATTEMPTS) {
            Log.e(TAG, "Max reconnection attempts reached ($MAX_RECONNECT_ATTEMPTS)")
            callbacks.onMaxReconnectAttemptsReached()
            return
        }

        // Exponential backoff: 5s, 10s, 20s, 40s...
        val delay = (RECONNECT_BASE_DELAY * 2.0.pow((attempts - 1).toDouble())).toLong()
        val cappedDelay = min(delay, RECONNECT_MAX_DELAY)

        Log.i(TAG, "Scheduling reconnect attempt $attempts in ${cappedDelay}ms")
        callbacks.onReconnecting(attempts, cappedDelay)

        // Schedule reconnection
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            connect()
        }, cappedDelay)
    }

    /**
     * Sends registration message to identify device to server
     */
    private fun sendRegistration() {
        try {
            val message = MessageSerializer.buildRegistrationMessage(
                deviceId = prefsManager.deviceId,
                authToken = prefsManager.authToken
            )
            webSocket?.send(message)
            Log.i(TAG, "Sent registration message")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send registration", e)
        }
    }

    /**
     * WebSocket event handler
     */
    private inner class WebSocketHandler : WebSocketListener() {

        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.i(TAG, "WebSocket connected successfully")
            isConnected.set(true)
            isConnecting.set(false)
            reconnectAttempts.set(0) // Reset on successful connection

            // Send registration message
            sendRegistration()

            // Notify callback
            callbacks.onConnected()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            Log.i(TAG, "📨 Received message from server")
            Log.d(TAG, "Raw message: $text")

            try {
                val serverMessage = MessageSerializer.parseServerMessage(text)
                Log.i(TAG, "✓ Message parsed successfully")
                Log.d(TAG, "Message type: ${serverMessage.type}, action: ${serverMessage.action}")
                handleServerMessage(serverMessage)
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to parse server message", e)
                Log.e(TAG, "Raw message that failed: $text")
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "WebSocket connection failed", t)
            isConnected.set(false)
            isConnecting.set(false)

            callbacks.onConnectionError(t.message ?: "Unknown error")

            // Schedule reconnection
            scheduleReconnect()
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.i(TAG, "WebSocket closed: code=$code, reason=$reason")
            isConnected.set(false)
            isConnecting.set(false)

            callbacks.onDisconnected(reason)

            // Schedule reconnection if needed
            if (shouldReconnect.get()) {
                scheduleReconnect()
            }
        }
    }

    /**
     * Handles messages received from server
     */
    private fun handleServerMessage(message: ServerMessage) {
        Log.i(TAG, "🔄 handleServerMessage called")
        Log.d(TAG, "Message details - type: ${message.type}, action: ${message.action}, message: ${message.message}")

        when (message.type) {
            "registered" -> {
                Log.i(TAG, "✓ Server confirmed registration")
                callbacks.onRegistered()
            }
            "activation_changed" -> {
                Log.i(TAG, "✓ Activation state changed: ${message.activated}")
                callbacks.onActivationChanged(message.activated)
            }
            "command" -> {
                Log.i(TAG, "📋 Command message received")
                // Handle commands from server (stop, start, etc.)
                when (message.action) {
                    "stop" -> {
                        Log.i(TAG, "🛑 Received STOP media command from server")
                        Log.i(TAG, "📞 Calling callbacks.onStopCommand()")
                        callbacks.onStopCommand()
                        Log.i(TAG, "✓ onStopCommand() callback completed")
                    }
                    "start" -> {
                        Log.i(TAG, "▶ Received START media command from server")
                        Log.i(TAG, "📞 Calling callbacks.onStartCommand()")
                        callbacks.onStartCommand()
                        Log.i(TAG, "✓ onStartCommand() callback completed")
                    }
                    else -> {
                        Log.w(TAG, "❌ Unknown command action: ${message.action}")
                    }
                }
            }
            "stop" -> {
                // Legacy support for direct stop messages
                Log.i(TAG, "🛑 Received legacy stop command from server")
                Log.i(TAG, "📞 Calling callbacks.onStopCommand()")
                callbacks.onStopCommand()
                Log.i(TAG, "✓ onStopCommand() callback completed")
            }
            "error" -> {
                Log.e(TAG, "❌ Server error: ${message.message}")
                callbacks.onServerError(message.message)
            }
            else -> {
                Log.w(TAG, "❓ Unknown message type: ${message.type}")
            }
        }
    }

    companion object {
        private const val TAG = "WebSocketClient"
        private const val MAX_RECONNECT_ATTEMPTS = 10
        private const val RECONNECT_BASE_DELAY = 5000L // 5 seconds
        private const val RECONNECT_MAX_DELAY = 60000L // 1 minute
    }
}

/**
 * Callbacks for WebSocket events
 */
interface WebSocketCallbacks {
    /**
     * Called when WebSocket successfully connects
     */
    fun onConnected()

    /**
     * Called when device is registered with server
     */
    fun onRegistered()

    /**
     * Called when WebSocket disconnects
     */
    fun onDisconnected(reason: String)

    /**
     * Called when connection fails
     */
    fun onConnectionError(error: String)

    /**
     * Called when reconnection is scheduled
     */
    fun onReconnecting(attempt: Int, delayMs: Long)

    /**
     * Called when max reconnection attempts reached
     */
    fun onMaxReconnectAttemptsReached()

    /**
     * Called when server changes activation state
     */
    fun onActivationChanged(activated: Boolean)

    /**
     * Called when server sends stop command
     */
    fun onStopCommand()

    /**
     * Called when server sends start command to resume media streaming
     */
    fun onStartCommand()

    /**
     * Called when server reports an error
     */
    fun onServerError(error: String)
}
