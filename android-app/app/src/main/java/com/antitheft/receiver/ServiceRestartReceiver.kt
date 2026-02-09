package com.antitheft.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.antitheft.service.TrackingService

/**
 * Receiver to automatically restart TrackingService if it gets killed
 */
class ServiceRestartReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "ServiceRestartReceiver triggered: ${intent.action}")

        try {
            TrackingService.start(context)
            Log.i(TAG, "TrackingService restart initiated")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restart TrackingService", e)
        }
    }

    companion object {
        private const val TAG = "ServiceRestartReceiver"
    }
}
