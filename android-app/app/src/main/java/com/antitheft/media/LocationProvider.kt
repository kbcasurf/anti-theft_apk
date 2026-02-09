package com.antitheft.media

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import com.antitheft.utils.Constants

/**
 * Provides GPS location tracking using FusedLocationProviderClient
 * Delivers location updates every 30 seconds with high accuracy
 */
class LocationProvider(private val context: Context) {

    private var fusedLocationClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null
    private var updateCallback: ((LocationData) -> Unit)? = null

    /**
     * Starts receiving location updates
     * @param callback Function called when new location is received
     */
    fun startLocationUpdates(callback: (LocationData) -> Unit) {
        if (!checkPermissions()) {
            Log.e(TAG, "Location permissions not granted")
            return
        }

        updateCallback = callback

        // Initialize fused location client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

        // Create location request
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            Constants.LOCATION_UPDATE_INTERVAL_MS
        ).apply {
            setMinUpdateIntervalMillis(Constants.LOCATION_FASTEST_INTERVAL_MS)
            setWaitForAccurateLocation(false)
        }.build()

        // Create location callback
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    handleLocationUpdate(location)
                }
            }

            override fun onLocationAvailability(availability: LocationAvailability) {
                if (!availability.isLocationAvailable) {
                    Log.w(TAG, "Location not available")
                }
            }
        }

        // Request location updates
        try {
            fusedLocationClient?.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )
            Log.i(TAG, "Location updates started")

            // Get last known location immediately
            fusedLocationClient?.lastLocation?.addOnSuccessListener { location ->
                location?.let { handleLocationUpdate(it) }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException: Location permission denied", e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start location updates", e)
        }
    }

    /**
     * Stops receiving location updates
     */
    fun stopLocationUpdates() {
        locationCallback?.let {
            fusedLocationClient?.removeLocationUpdates(it)
            Log.i(TAG, "Location updates stopped")
        }
        locationCallback = null
        updateCallback = null
        fusedLocationClient = null
    }

    /**
     * Handles location update and converts to LocationData
     */
    private fun handleLocationUpdate(location: Location) {
        val locationData = LocationData(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracy = location.accuracy,
            altitude = if (location.hasAltitude()) location.altitude else null,
            speed = if (location.hasSpeed()) location.speed else null,
            bearing = if (location.hasBearing()) location.bearing else null,
            timestamp = System.currentTimeMillis()
        )

        Log.d(TAG, "Location update: lat=${locationData.latitude}, lon=${locationData.longitude}, accuracy=${locationData.accuracy}m")
        updateCallback?.invoke(locationData)
    }

    /**
     * Checks if location permissions are granted
     */
    fun checkPermissions(): Boolean {
        val fineLocation = ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseLocation = ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return fineLocation || coarseLocation
    }

    companion object {
        private const val TAG = "LocationProvider"
    }
}

/**
 * Data class representing location information
 */
data class LocationData(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val altitude: Double? = null,
    val speed: Float? = null,
    val bearing: Float? = null,
    val timestamp: Long
)
