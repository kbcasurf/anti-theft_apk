package com.antitheft.ui

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.work.*
import com.antitheft.databinding.ActivityMainBinding
import com.antitheft.network.ApiClient
import com.antitheft.utils.Constants
import com.antitheft.utils.PreferencesManager
import com.antitheft.worker.CheckInWorker
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Main activity for configuring the Anti-Theft application
 * Allows users to set server URL/port and test connectivity
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefsManager: PreferencesManager
    private lateinit var apiClient: ApiClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize managers
        prefsManager = PreferencesManager(this)
        apiClient = ApiClient(prefsManager)

        // Load saved configuration
        loadConfiguration()

        // Display device information
        displayDeviceInfo()

        // Update WorkManager status
        updateWorkManagerStatus()

        // Set up button listeners
        setupListeners()
    }

    /**
     * Loads saved server configuration from preferences
     */
    private fun loadConfiguration() {
        binding.editServerUrl.setText(prefsManager.serverUrl)
        binding.editServerPort.setText(prefsManager.serverPort.toString())

        updateConnectionStatus()
    }

    /**
     * Displays device ID and auth token
     */
    private fun displayDeviceInfo() {
        binding.txtDeviceId.text = prefsManager.deviceId
        binding.txtAuthToken.text = prefsManager.authToken
    }

    /**
     * Sets up click listeners for buttons
     */
    private fun setupListeners() {
        binding.btnSaveConfig.setOnClickListener {
            saveConfiguration()
        }

        binding.btnTestConnection.setOnClickListener {
            testConnection()
        }
    }

    /**
     * Validates and saves server configuration
     */
    private fun saveConfiguration() {
        val url = binding.editServerUrl.text.toString().trim()
        val portStr = binding.editServerPort.text.toString().trim()

        // Validate inputs
        if (url.isEmpty()) {
            binding.editServerUrl.error = "Server URL is required"
            return
        }

        val port = portStr.toIntOrNull()
        if (port == null || port < 1 || port > 65535) {
            binding.editServerPort.error = "Invalid port number (1-65535)"
            return
        }

        // Save to preferences
        prefsManager.serverUrl = url
        prefsManager.serverPort = port

        Toast.makeText(this, "Configuration saved", Toast.LENGTH_SHORT).show()
        Log.i(TAG, "Configuration saved: $url:$port")

        // Update status
        updateConnectionStatus()

        // Schedule WorkManager check-ins
        scheduleCheckInWorker()
    }

    /**
     * Tests connection to the configured server
     */
    private fun testConnection() {
        if (!prefsManager.isConfigured()) {
            Toast.makeText(this, "Please configure server first", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnTestConnection.isEnabled = false
        binding.txtConnectionStatus.text = "Testing connection..."

        lifecycleScope.launch {
            try {
                val (success, message) = apiClient.testConnection()

                binding.txtConnectionStatus.text = message

                if (success) {
                    Toast.makeText(this@MainActivity, "Connection successful!", Toast.LENGTH_SHORT).show()
                    Log.i(TAG, "Connection test successful")
                } else {
                    Toast.makeText(this@MainActivity, "Connection failed: $message", Toast.LENGTH_LONG).show()
                    Log.w(TAG, "Connection test failed: $message")
                }
            } catch (e: Exception) {
                binding.txtConnectionStatus.text = "Error: ${e.message}"
                Toast.makeText(this@MainActivity, "Test failed: ${e.message}", Toast.LENGTH_LONG).show()
                Log.e(TAG, "Connection test exception", e)
            } finally {
                binding.btnTestConnection.isEnabled = true
            }
        }
    }

    /**
     * Updates the connection status display
     */
    private fun updateConnectionStatus() {
        if (prefsManager.isConfigured()) {
            binding.txtConnectionStatus.text = "Configured: ${prefsManager.getBaseUrl()}"
        } else {
            binding.txtConnectionStatus.text = "Not configured"
        }
    }

    /**
     * Schedules the periodic check-in worker
     */
    private fun scheduleCheckInWorker() {
        // Remove network constraint - check-ins will fail gracefully if no network
        // This prevents Android JobScheduler from blocking jobs when it thinks CONNECTIVITY is unsatisfied
        val constraints = Constraints.Builder()
            // .setRequiredNetworkType(NetworkType.CONNECTED)  // REMOVED - was blocking periodic jobs
            .build()

        val checkInRequest = PeriodicWorkRequestBuilder<CheckInWorker>(
            Constants.CHECK_IN_INTERVAL_MINUTES,
            TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()

        // Use REPLACE to ensure new interval and constraints are applied
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            Constants.WORK_NAME_CHECK_IN,
            ExistingPeriodicWorkPolicy.REPLACE,  // Changed from KEEP to force update
            checkInRequest
        )

        Log.i(TAG, "CheckInWorker scheduled with ${Constants.CHECK_IN_INTERVAL_MINUTES}-minute interval")
        Toast.makeText(this, "Background check-ins enabled (${Constants.CHECK_IN_INTERVAL_MINUTES} min interval)", Toast.LENGTH_SHORT).show()

        // Update status display
        updateWorkManagerStatus()
    }

    /**
     * Updates the WorkManager status display
     */
    private fun updateWorkManagerStatus() {
        WorkManager.getInstance(this)
            .getWorkInfosForUniqueWorkLiveData(Constants.WORK_NAME_CHECK_IN)
            .observe(this) { workInfos ->
                if (workInfos.isNullOrEmpty()) {
                    binding.txtWorkManagerStatus.text = "Not scheduled"
                } else {
                    val workInfo = workInfos[0]
                    val statusText = when (workInfo.state) {
                        WorkInfo.State.ENQUEUED -> "Scheduled (waiting)"
                        WorkInfo.State.RUNNING -> "Running"
                        WorkInfo.State.SUCCEEDED -> "Last run: Success"
                        WorkInfo.State.FAILED -> "Last run: Failed"
                        WorkInfo.State.BLOCKED -> "Blocked"
                        WorkInfo.State.CANCELLED -> "Cancelled"
                    }
                    binding.txtWorkManagerStatus.text = statusText
                }
            }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
