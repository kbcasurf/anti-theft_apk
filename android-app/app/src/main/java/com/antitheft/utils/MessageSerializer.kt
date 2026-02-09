package com.antitheft.utils

import android.util.Base64
import com.antitheft.media.LocationData
import org.json.JSONObject

/**
 * Utility for serializing media data into JSON messages for WebSocket transmission
 * Handles location data, video frames, and audio chunks
 */
object MessageSerializer {

    /**
     * Message types for WebSocket communication
     */
    object MessageType {
        const val REGISTRATION = "register"
        const val LOCATION = "location"
        const val VIDEO_FRAME = "video_frame"
        const val AUDIO_CHUNK = "audio_chunk"
    }

    /**
     * Builds a registration message to identify this device to the server
     * @param deviceId Unique device identifier
     * @param authToken Authentication token
     * @return JSON string for registration
     */
    fun buildRegistrationMessage(deviceId: String, authToken: String): String {
        return JSONObject().apply {
            put("type", MessageType.REGISTRATION)
            put("client_type", "device")
            put("device_id", deviceId)
            put("auth_token", authToken)
            put("timestamp", System.currentTimeMillis())
        }.toString()
    }

    /**
     * Builds a location message from LocationData
     * @param locationData GPS location data
     * @return JSON string containing location information
     */
    fun buildLocationMessage(locationData: LocationData): String {
        return JSONObject().apply {
            put("type", MessageType.LOCATION)
            put("timestamp", locationData.timestamp)
            put("data", JSONObject().apply {
                put("latitude", locationData.latitude)
                put("longitude", locationData.longitude)
                put("accuracy", locationData.accuracy.toDouble())

                // Optional fields
                locationData.altitude?.let { put("altitude", it) }
                locationData.speed?.let { put("speed", it.toDouble()) }
                locationData.bearing?.let { put("bearing", it.toDouble()) }
            })
        }.toString()
    }

    /**
     * Builds a video frame message from JPEG bytes
     * Encodes the JPEG data as Base64 for JSON transmission
     * @param frameData JPEG compressed frame bytes
     * @return JSON string containing Base64-encoded video frame
     */
    fun buildVideoFrameMessage(frameData: ByteArray): String {
        val base64Data = Base64.encodeToString(frameData, Base64.NO_WRAP)
        return JSONObject().apply {
            put("type", MessageType.VIDEO_FRAME)
            put("timestamp", System.currentTimeMillis())
            put("data", base64Data)
            put("size", frameData.size)
            put("format", "jpeg")
        }.toString()
    }

    /**
     * Builds an audio chunk message from PCM bytes
     * Encodes the audio data as Base64 for JSON transmission
     * @param audioData PCM audio bytes
     * @return JSON string containing Base64-encoded audio chunk
     */
    fun buildAudioChunkMessage(audioData: ByteArray): String {
        val base64Data = Base64.encodeToString(audioData, Base64.NO_WRAP)
        return JSONObject().apply {
            put("type", MessageType.AUDIO_CHUNK)
            put("timestamp", System.currentTimeMillis())
            put("data", base64Data)
            put("size", audioData.size)
            put("format", "pcm")
            put("sample_rate", Constants.AUDIO_SAMPLE_RATE)
            put("channels", 1) // Mono
            put("bit_depth", 16)
        }.toString()
    }

    /**
     * Parses a server command message
     * @param message JSON string from server
     * @return Parsed command type and data
     */
    fun parseServerMessage(message: String): ServerMessage {
        return try {
            val json = JSONObject(message)
            val type = json.optString("type", "unknown")
            val data = json.optJSONObject("data")

            ServerMessage(
                type = type,
                message = json.optString("message", ""),
                activated = json.optBoolean("activated", false),
                action = json.optString("action", ""),
                data = data
            )
        } catch (e: Exception) {
            ServerMessage(
                type = "error",
                message = "Failed to parse server message: ${e.message}",
                activated = false,
                action = "",
                data = null
            )
        }
    }
}

/**
 * Data class representing a parsed server message
 */
data class ServerMessage(
    val type: String,
    val message: String,
    val activated: Boolean,
    val action: String = "",
    val data: JSONObject?
)
