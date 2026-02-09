/**
 * Anti-Theft Dashboard - Map Module
 * Handles location tracking and map display using Leaflet.js
 */

const mapModule = {
    map: null,
    marker: null,
    accuracyCircle: null,
    locationHistory: [],
    maxHistoryPoints: 50,
    isInitialized: false
};

/**
 * Initialize the map
 */
function initMap() {
    try {
        // Remove placeholder
        const mapContainer = document.getElementById('map');
        mapContainer.innerHTML = '';

        // Initialize Leaflet map
        mapModule.map = L.map('map').setView([37.7749, -122.4194], 13); // Default: San Francisco

        // Add OpenStreetMap tiles
        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
            attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
            maxZoom: 19
        }).addTo(mapModule.map);

        // Add scale control
        L.control.scale({
            imperial: true,
            metric: true
        }).addTo(mapModule.map);

        mapModule.isInitialized = true;
        logToConsole('Map initialized', 'info');

    } catch (error) {
        logToConsole(`Failed to initialize map: ${error.message}`, 'error');
        console.error('Map initialization error:', error);
    }
}

/**
 * Handle location update from device
 */
function handleLocationUpdate(message) {
    if (!mapModule.isInitialized) {
        logToConsole('Map not initialized, skipping location update', 'warning');
        return;
    }

    try {
        const { data, timestamp } = message;
        const { latitude, longitude, accuracy, altitude, speed, bearing } = data;

        logToConsole(`Location: ${latitude.toFixed(6)}, ${longitude.toFixed(6)} (±${accuracy.toFixed(1)}m)`, 'info');

        // Store in history
        mapModule.locationHistory.push({
            lat: latitude,
            lng: longitude,
            accuracy: accuracy,
            timestamp: timestamp
        });

        // Limit history size
        if (mapModule.locationHistory.length > mapModule.maxHistoryPoints) {
            mapModule.locationHistory.shift();
        }

        // Update or create marker
        if (mapModule.marker) {
            // Update existing marker
            mapModule.marker.setLatLng([latitude, longitude]);
            mapModule.marker.setPopupContent(createPopupContent(data, timestamp));
        } else {
            // Create new marker
            mapModule.marker = L.marker([latitude, longitude], {
                icon: createCustomIcon()
            }).addTo(mapModule.map);

            mapModule.marker.bindPopup(createPopupContent(data, timestamp));
        }

        // Update or create accuracy circle
        if (mapModule.accuracyCircle) {
            mapModule.accuracyCircle.setLatLng([latitude, longitude]);
            mapModule.accuracyCircle.setRadius(accuracy);
        } else {
            mapModule.accuracyCircle = L.circle([latitude, longitude], {
                radius: accuracy,
                color: '#2196f3',
                fillColor: '#2196f3',
                fillOpacity: 0.1,
                weight: 2
            }).addTo(mapModule.map);
        }

        // Pan map to marker (smooth)
        mapModule.map.panTo([latitude, longitude], {
            animate: true,
            duration: 1.0
        });

        // Draw path if multiple points
        drawLocationPath();

    } catch (error) {
        logToConsole(`Error updating location: ${error.message}`, 'error');
        console.error('Location update error:', error);
    }
}

/**
 * Create custom marker icon
 */
function createCustomIcon() {
    return L.divIcon({
        html: '📍',
        className: 'custom-marker-icon',
        iconSize: [32, 32],
        iconAnchor: [16, 32],
        popupAnchor: [0, -32]
    });
}

/**
 * Create popup content for marker
 */
function createPopupContent(data, timestamp) {
    const time = new Date(timestamp).toLocaleString();
    let content = `
        <div style="min-width: 200px;">
            <strong>📍 Device Location</strong><br>
            <strong>Lat:</strong> ${data.latitude.toFixed(6)}<br>
            <strong>Lon:</strong> ${data.longitude.toFixed(6)}<br>
            <strong>Accuracy:</strong> ±${data.accuracy.toFixed(1)}m<br>
    `;

    if (data.altitude !== undefined && data.altitude !== null) {
        content += `<strong>Altitude:</strong> ${data.altitude.toFixed(1)}m<br>`;
    }

    if (data.speed !== undefined && data.speed !== null) {
        content += `<strong>Speed:</strong> ${(data.speed * 3.6).toFixed(1)} km/h<br>`;
    }

    if (data.bearing !== undefined && data.bearing !== null) {
        content += `<strong>Bearing:</strong> ${data.bearing.toFixed(0)}°<br>`;
    }

    content += `<strong>Time:</strong> ${time}</div>`;
    return content;
}

/**
 * Draw path showing location history
 */
function drawLocationPath() {
    if (mapModule.locationHistory.length < 2) {
        return;
    }

    // Remove old path if exists
    if (mapModule.path) {
        mapModule.map.removeLayer(mapModule.path);
    }

    // Create polyline from history
    const latlngs = mapModule.locationHistory.map(point => [point.lat, point.lng]);

    mapModule.path = L.polyline(latlngs, {
        color: '#667eea',
        weight: 3,
        opacity: 0.7,
        smoothFactor: 1
    }).addTo(mapModule.map);
}

/**
 * Clear location history and path
 */
function clearLocationHistory() {
    mapModule.locationHistory = [];

    if (mapModule.path) {
        mapModule.map.removeLayer(mapModule.path);
        mapModule.path = null;
    }

    logToConsole('Location history cleared', 'info');
}

/**
 * Fit map bounds to show all history points
 */
function fitMapToHistory() {
    if (mapModule.locationHistory.length === 0) {
        return;
    }

    const bounds = L.latLngBounds(
        mapModule.locationHistory.map(point => [point.lat, point.lng])
    );

    mapModule.map.fitBounds(bounds, { padding: [50, 50] });
}
