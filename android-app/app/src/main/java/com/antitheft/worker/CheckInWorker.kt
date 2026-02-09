package com.antitheft.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.antitheft.R
import com.antitheft.network.ApiClient
import com.antitheft.service.TrackingService
import com.antitheft.utils.Constants
import com.antitheft.utils.PreferencesManager
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that performs periodic check-ins with the server
 * Runs every 15 minutes to check if tracking should be activated
 */
class CheckInWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val prefsManager = PreferencesManager(context)
    private val apiClient = ApiClient(prefsManager)

    override suspend fun doWork(): Result {
        val rapidPollAttempt = inputData.getInt(KEY_RAPID_POLL_ATTEMPT, 0)
        Log.d(TAG, "Check-in worker started (rapidPollAttempt=$rapidPollAttempt)")

        // Check if server is configured
        if (!prefsManager.isConfigured()) {
            Log.w(TAG, "Server not configured, skipping check-in")
            return Result.success()
        }

        return try {
            // Make check-in request to server
            val response = apiClient.checkIn()
            Log.d(TAG, "Check-in response: activated=${response.activated}, message=${response.message}")

            if (response.activated) {
                if (TrackingService.isRunning) {
                    Log.d(TAG, "TrackingService already running, skipping")
                } else {
                    Log.i(TAG, "Activation detected, elevating to foreground and starting tracking service")

                    // Elevate this worker to foreground to get exemption for starting foreground service
                    setForegroundAsync(createForegroundInfo())

                    // Now we can start the tracking service
                    startTrackingService()
                }
            } else if (rapidPollAttempt < MAX_RAPID_POLL_ATTEMPTS) {
                // Not activated yet — schedule a rapid follow-up check
                scheduleRapidFollowUp(rapidPollAttempt + 1)
            } else {
                Log.d(TAG, "Rapid polling exhausted ($MAX_RAPID_POLL_ATTEMPTS attempts), waiting for next periodic check-in")
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Check-in failed with exception", e)
            // Retry on failure
            Result.retry()
        }
    }

    /**
     * Schedules a rapid follow-up check-in using OneTimeWorkRequest.
     * Unlike PeriodicWorkRequest (15-min minimum), OneTimeWorkRequest can fire
     * in seconds, giving near-instant activation response.
     */
    private fun scheduleRapidFollowUp(nextAttempt: Int) {
        val inputData = workDataOf(KEY_RAPID_POLL_ATTEMPT to nextAttempt)

        val rapidCheckIn = OneTimeWorkRequestBuilder<CheckInWorker>()
            .setInitialDelay(RAPID_POLL_INTERVAL_SECONDS, TimeUnit.SECONDS)
            .setInputData(inputData)
            .addTag(TAG_RAPID_POLL)
            .build()

        WorkManager.getInstance(applicationContext)
            .enqueueUniqueWork(
                RAPID_POLL_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                rapidCheckIn
            )

        Log.d(TAG, "Scheduled rapid follow-up #$nextAttempt in ${RAPID_POLL_INTERVAL_SECONDS}s")
    }

    /**
     * Creates ForegroundInfo for elevating this worker to foreground
     * Uses disguised notification text and icons for stealth operation
     */
    private fun createForegroundInfo(): ForegroundInfo {
        createNotificationChannel()

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("System Update")  // Disguised title
            .setContentText("Checking for updates...")  // Disguised text
            .setSmallIcon(android.R.drawable.ic_popup_sync)  // System sync icon
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)  // Minimize visibility
            .setSound(null)  // No sound
            .setVibrate(null)  // No vibration
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    /**
     * Creates notification channel for the worker
     * Uses disguised settings for stealth operation
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "System Updates",  // Disguised name
                NotificationManager.IMPORTANCE_MIN  // Minimize importance
            ).apply {
                description = "System update notifications"
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }

            val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Starts the TrackingService as a foreground service
     * Uses setForegroundAsync to get foreground exemption for starting the service
     */
    private fun startTrackingService() {
        try {
            // On Android 14+, we need to be a foreground service ourselves
            // to start another foreground service
            TrackingService.start(applicationContext)
            Log.d(TAG, "TrackingService start requested")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start TrackingService", e)
        }
    }

    companion object {
        private const val TAG = "CheckInWorker"
        private const val CHANNEL_ID = "system_updates"  // Disguised channel ID
        private const val NOTIFICATION_ID = 999

        // Rapid polling: OneTimeWorkRequest chain for fast activation response
        const val KEY_RAPID_POLL_ATTEMPT = "rapid_poll_attempt"
        const val TAG_RAPID_POLL = "rapid_poll_check_in"
        private const val RAPID_POLL_WORK_NAME = "rapid_check_in"
        private const val RAPID_POLL_INTERVAL_SECONDS = 30L
        private const val MAX_RAPID_POLL_ATTEMPTS = 10  // ~5 minutes of rapid polling

        /**
         * Cancels any pending rapid follow-up polls.
         * Called when TrackingService starts to avoid unnecessary check-ins.
         */
        fun cancelRapidPolling(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(RAPID_POLL_WORK_NAME)
            Log.d(TAG, "Rapid polling cancelled")
        }
    }
}
