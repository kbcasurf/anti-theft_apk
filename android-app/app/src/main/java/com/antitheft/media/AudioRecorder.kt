package com.antitheft.media

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.app.ActivityCompat
import com.antitheft.utils.Constants
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Records audio from microphone using AudioRecord API
 * Captures PCM audio at 16kHz mono 16-bit in 160ms chunks
 */
class AudioRecorder(private val context: Context) {

    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private val isRecording = AtomicBoolean(false)
    private var audioCallback: ((ByteArray) -> Unit)? = null

    /**
     * Starts audio recording
     * @param callback Function called when new audio chunk is captured
     */
    fun startRecording(callback: (ByteArray) -> Unit) {
        if (!checkPermissions()) {
            Log.e(TAG, "Audio recording permission not granted")
            return
        }

        if (isRecording.get()) {
            Log.w(TAG, "Recording already in progress")
            return
        }

        audioCallback = callback

        try {
            // Calculate buffer size
            val minBufferSize = AudioRecord.getMinBufferSize(
                Constants.AUDIO_SAMPLE_RATE,
                Constants.AUDIO_CHANNEL,
                Constants.AUDIO_ENCODING
            )

            if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
                Log.e(TAG, "Invalid buffer size")
                return
            }

            // Use a buffer size that's at least 2x the minimum
            val bufferSize = minBufferSize * 2

            // Create AudioRecord instance
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                Constants.AUDIO_SAMPLE_RATE,
                Constants.AUDIO_CHANNEL,
                Constants.AUDIO_ENCODING,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed")
                audioRecord?.release()
                audioRecord = null
                return
            }

            // Start recording
            audioRecord?.startRecording()
            isRecording.set(true)

            // Start recording thread
            recordingThread = Thread(RecordingRunnable(), "AudioRecordingThread")
            recordingThread?.start()

            Log.i(TAG, "Audio recording started (sample rate: ${Constants.AUDIO_SAMPLE_RATE}Hz)")

        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException: Microphone permission denied", e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start audio recording", e)
        }
    }

    /**
     * Stops audio recording
     */
    fun stopRecording() {
        if (!isRecording.get()) {
            return
        }

        isRecording.set(false)

        try {
            // Stop recording
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null

            // Wait for recording thread to finish
            recordingThread?.join(1000)
            recordingThread = null

            audioCallback = null

            Log.i(TAG, "Audio recording stopped")

        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audio recording", e)
        }
    }

    /**
     * Checks if microphone permission is granted
     */
    fun checkPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Runnable that continuously reads audio data
     */
    private inner class RecordingRunnable : Runnable {
        override fun run() {
            // Calculate chunk size for 160ms at 16kHz mono 16-bit
            // 16000 samples/sec * 0.160 sec = 2560 samples
            // 2560 samples * 2 bytes/sample = 5120 bytes
            val chunkSize = (Constants.AUDIO_SAMPLE_RATE * Constants.AUDIO_BUFFER_SIZE_MS / 1000) * 2

            val audioBuffer = ByteArray(chunkSize)

            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)

            Log.d(TAG, "Recording thread started (chunk size: $chunkSize bytes)")

            while (isRecording.get()) {
                try {
                    val bytesRead = audioRecord?.read(audioBuffer, 0, chunkSize) ?: 0

                    if (bytesRead > 0) {
                        // Send audio chunk to callback
                        val chunk = audioBuffer.copyOf(bytesRead)
                        audioCallback?.invoke(chunk)
                    } else if (bytesRead == AudioRecord.ERROR_INVALID_OPERATION) {
                        Log.e(TAG, "AudioRecord read error: INVALID_OPERATION")
                        break
                    } else if (bytesRead == AudioRecord.ERROR_BAD_VALUE) {
                        Log.e(TAG, "AudioRecord read error: BAD_VALUE")
                        break
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Error reading audio data", e)
                    break
                }
            }

            Log.d(TAG, "Recording thread ended")
        }
    }

    companion object {
        private const val TAG = "AudioRecorder"
    }
}
