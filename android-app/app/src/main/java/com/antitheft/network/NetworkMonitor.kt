package com.antitheft.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log

/**
 * Monitors network connectivity changes and notifies callbacks
 * Helps WebSocket client handle network transitions gracefully
 */
class NetworkMonitor(
    private val context: Context,
    private val callback: NetworkCallback
) {

    private val connectivityManager: ConnectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var isMonitoring = false

    /**
     * Starts monitoring network connectivity changes
     */
    fun startMonitoring() {
        if (isMonitoring) {
            Log.w(TAG, "Already monitoring network")
            return
        }

        Log.i(TAG, "Starting network monitoring")

        // Create network request for all transports (WiFi, Cellular, etc.)
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            .build()

        // Create callback
        networkCallback = object : ConnectivityManager.NetworkCallback() {

            override fun onAvailable(network: Network) {
                Log.i(TAG, "Network available: $network")
                callback.onNetworkAvailable()
            }

            override fun onLost(network: Network) {
                Log.w(TAG, "Network lost: $network")
                callback.onNetworkLost()
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                val isValidated = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

                Log.d(TAG, "Network capabilities changed: hasInternet=$hasInternet, isValidated=$isValidated")

                if (hasInternet && isValidated) {
                    // Get connection type
                    val connectionType = when {
                        networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
                        networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
                        networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                        else -> "Unknown"
                    }

                    Log.i(TAG, "Connected via: $connectionType")
                    callback.onNetworkTypeChanged(connectionType)
                }
            }

            override fun onUnavailable() {
                Log.w(TAG, "Network unavailable")
                callback.onNetworkUnavailable()
            }
        }

        // Register callback
        try {
            connectivityManager.registerNetworkCallback(networkRequest, networkCallback!!)
            isMonitoring = true

            // Check current connectivity state
            checkCurrentConnectivity()

        } catch (e: Exception) {
            Log.e(TAG, "Failed to register network callback", e)
        }
    }

    /**
     * Stops monitoring network connectivity changes
     */
    fun stopMonitoring() {
        if (!isMonitoring) {
            Log.w(TAG, "Not monitoring network")
            return
        }

        Log.i(TAG, "Stopping network monitoring")

        try {
            networkCallback?.let {
                connectivityManager.unregisterNetworkCallback(it)
            }
            networkCallback = null
            isMonitoring = false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister network callback", e)
        }
    }

    /**
     * Checks current network connectivity state
     */
    private fun checkCurrentConnectivity() {
        val activeNetwork = connectivityManager.activeNetwork
        val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)

        if (networkCapabilities != null) {
            val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val isValidated = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

            Log.d(TAG, "Current connectivity: hasInternet=$hasInternet, isValidated=$isValidated")

            if (hasInternet && isValidated) {
                callback.onNetworkAvailable()
            } else {
                callback.onNetworkUnavailable()
            }
        } else {
            Log.w(TAG, "No active network")
            callback.onNetworkUnavailable()
        }
    }

    /**
     * Returns true if currently monitoring
     */
    fun isMonitoring(): Boolean = isMonitoring

    /**
     * Returns true if network is currently available
     */
    fun isNetworkAvailable(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork
        val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)

        return networkCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
            networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    companion object {
        private const val TAG = "NetworkMonitor"
    }
}

/**
 * Callback interface for network state changes
 */
interface NetworkCallback {
    /**
     * Called when network becomes available
     */
    fun onNetworkAvailable()

    /**
     * Called when network is lost
     */
    fun onNetworkLost()

    /**
     * Called when network becomes unavailable
     */
    fun onNetworkUnavailable()

    /**
     * Called when network type changes (WiFi, Cellular, etc.)
     */
    fun onNetworkTypeChanged(type: String)
}
