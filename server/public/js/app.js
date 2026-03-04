/**
 * Anti-Theft Dashboard - Main Application
 * WebSocket client and application state management
 */

// Application state
const app = {
    ws: null,
    reconnectAttempts: 0,
    maxReconnectAttempts: 10,
    reconnectDelay: 5000,
    isConnected: false,
    deviceConnected: false,
    trackingActive: false
};

/**
 * Initialize the application
 */
function initApp() {
    logToConsole('Initializing dashboard...', 'info');

    // Initialize components
    initMap();
    initVideo();
    initAudio();
    initControls();

    // Connect to WebSocket
    connectWebSocket();

    // Start status polling
    startStatusPolling();

    logToConsole('Dashboard initialized successfully', 'info');
}

/**
 * Connect to WebSocket server
 */
function connectWebSocket() {
    if (app.ws && (app.ws.readyState === WebSocket.CONNECTING || app.ws.readyState === WebSocket.OPEN)) {
        logToConsole('WebSocket already connected or connecting', 'warning');
        return;
    }

    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const wsUrl = `${protocol}//${window.location.host}/ws`;

    logToConsole(`Connecting to WebSocket: ${wsUrl}`, 'info');
    updateServerStatus('connecting');

    try {
        app.ws = new WebSocket(wsUrl);

        app.ws.onopen = handleWebSocketOpen;
        app.ws.onmessage = handleWebSocketMessage;
        app.ws.onerror = handleWebSocketError;
        app.ws.onclose = handleWebSocketClose;

    } catch (error) {
        logToConsole(`Failed to create WebSocket: ${error.message}`, 'error');
        scheduleReconnect();
    }
}

/**
 * Handle WebSocket connection opened
 */
function handleWebSocketOpen() {
    logToConsole('WebSocket connected successfully', 'info');
    app.isConnected = true;
    app.reconnectAttempts = 0;
    updateServerStatus('connected');

    // Send registration message
    sendRegistration();
}

/**
 * Send registration message to identify as web client
 */
function sendRegistration() {
    const message = {
        type: 'register',
        client_type: 'web',
        timestamp: Date.now()
    };

    sendWebSocketMessage(message);
    logToConsole('Sent registration message', 'info');
}

/**
 * Handle incoming WebSocket messages
 */
function handleWebSocketMessage(event) {
    try {
        const message = JSON.parse(event.data);

        // Update last update time
        updateLastUpdateTime();

        // Route message to appropriate handler
        switch (message.type) {
            case 'registered':
                handleRegistered(message);
                break;
            case 'location':
                handleLocationUpdate(message);
                break;
            case 'video_frame':
                handleVideoFrame(message);
                break;
            case 'audio_chunk':
                handleAudioChunk(message);
                break;
            case 'device_status':
                handleDeviceStatus(message);
                break;
            case 'device_connected':
                handleDeviceConnected(message);
                break;
            case 'device_disconnected':
                handleDeviceDisconnected(message);
                break;
            case 'activation_changed':
                handleActivationChanged(message);
                break;
            case 'command_ack':
                handleCommandAck(message);
                break;
            case 'connected':
                logToConsole('Server acknowledged connection', 'info');
                break;
            default:
                logToConsole(`Unknown message type: ${message.type}`, 'warning');
        }
    } catch (error) {
        logToConsole(`Failed to parse message: ${error.message}`, 'error');
    }
}

/**
 * Handle registration confirmation
 */
function handleRegistered(message) {
    logToConsole('Registration confirmed by server', 'info');
}

/**
 * Handle device status update (connected/disconnected)
 */
function handleDeviceStatus(message) {
    const isConnected = message.connected;
    app.deviceConnected = isConnected;

    if (isConnected) {
        logToConsole(`Device connected: ${message.device_id || 'Unknown'}`, 'info');
        updateDeviceStatus('connected');
        // Device connected — clear the waiting banner
        if (typeof hideWaitingForDevice === 'function') hideWaitingForDevice();
    } else {
        logToConsole('Device disconnected', 'warning');
        updateDeviceStatus('disconnected');
    }
}

/**
 * Handle device connected notification
 */
function handleDeviceConnected(message) {
    logToConsole('Device connected', 'info');
    app.deviceConnected = true;
    updateDeviceStatus('connected');
    if (typeof hideWaitingForDevice === 'function') hideWaitingForDevice();
}

/**
 * Handle device disconnected notification
 */
function handleDeviceDisconnected(message) {
    logToConsole('Device disconnected', 'warning');
    app.deviceConnected = false;
    updateDeviceStatus('disconnected');
}

/**
 * Handle command acknowledgment from device
 */
function handleCommandAck(message) {
    const status = message.success ? 'successfully' : 'with failure';
    const serviceLabel = message.service
        ? message.service.charAt(0).toUpperCase() + message.service.slice(1)
        : 'All services';
    logToConsole(`Device confirmed: ${serviceLabel} ${message.action} executed ${status}`, message.success ? 'info' : 'error');
}

/**
 * Handle activation state change
 */
function handleActivationChanged(message) {
    app.trackingActive = message.activated;
    updateTrackingStatus(message.activated);
    logToConsole(`Tracking ${message.activated ? 'activated' : 'deactivated'}`, 'info');
}

/**
 * Handle WebSocket error
 */
function handleWebSocketError(error) {
    logToConsole('WebSocket error occurred', 'error');
    console.error('WebSocket error:', error);
}

/**
 * Handle WebSocket connection closed
 */
function handleWebSocketClose(event) {
    logToConsole(`WebSocket closed: ${event.reason || 'Unknown reason'}`, 'warning');
    app.isConnected = false;
    app.deviceConnected = false;
    updateServerStatus('disconnected');
    updateDeviceStatus('disconnected');

    // Schedule reconnection
    scheduleReconnect();
}

/**
 * Schedule WebSocket reconnection with exponential backoff
 */
function scheduleReconnect() {
    if (app.reconnectAttempts >= app.maxReconnectAttempts) {
        logToConsole('Max reconnection attempts reached', 'error');
        return;
    }

    app.reconnectAttempts++;
    const delay = Math.min(app.reconnectDelay * Math.pow(2, app.reconnectAttempts - 1), 60000);

    logToConsole(`Reconnecting in ${delay / 1000} seconds (attempt ${app.reconnectAttempts})...`, 'warning');

    setTimeout(() => {
        connectWebSocket();
    }, delay);
}

/**
 * Send message via WebSocket
 */
function sendWebSocketMessage(message) {
    if (app.ws && app.ws.readyState === WebSocket.OPEN) {
        app.ws.send(JSON.stringify(message));
        return true;
    } else {
        logToConsole('Cannot send message: WebSocket not connected', 'error');
        return false;
    }
}

/**
 * Update server status indicator
 */
function updateServerStatus(status) {
    const indicator = document.getElementById('server-indicator');
    const statusText = document.getElementById('server-status');

    switch (status) {
        case 'connected':
            indicator.className = 'status-indicator connected';
            statusText.textContent = 'Connected';
            break;
        case 'connecting':
            indicator.className = 'status-indicator';
            indicator.style.background = '#ff9800';
            statusText.textContent = 'Connecting...';
            break;
        case 'disconnected':
            indicator.className = 'status-indicator disconnected';
            statusText.textContent = 'Disconnected';
            break;
    }
}

/**
 * Update device status indicator
 */
function updateDeviceStatus(status) {
    const indicator = document.getElementById('device-indicator');
    const statusText = document.getElementById('device-status');

    if (status === 'connected') {
        indicator.className = 'status-indicator connected';
        statusText.textContent = 'Connected';
    } else {
        indicator.className = 'status-indicator disconnected';
        statusText.textContent = 'Disconnected';
    }
}

/**
 * Update tracking status
 */
function updateTrackingStatus(active) {
    const statusEl = document.getElementById('tracking-status');
    statusEl.textContent = active ? 'Active' : 'Inactive';
    statusEl.style.color = active ? '#4caf50' : '#f44336';
}

/**
 * Update last update time
 */
function updateLastUpdateTime() {
    const lastUpdateEl = document.getElementById('last-update');
    const now = new Date();
    lastUpdateEl.textContent = now.toLocaleTimeString();
}

/**
 * Start polling server status
 */
function startStatusPolling() {
    // Poll every 5 seconds
    setInterval(async () => {
        try {
            const response = await fetch('/api/status');
            const data = await response.json();

            // Update tracking status
            if (data.activated !== undefined) {
                app.trackingActive = data.activated;
                updateTrackingStatus(data.activated);
            }

            // Update device connection status
            if (data.deviceConnected !== undefined) {
                const wasDisconnected = !app.deviceConnected;
                app.deviceConnected = data.deviceConnected;
                updateDeviceStatus(data.deviceConnected ? 'connected' : 'disconnected');

                // Clear waiting banner when device connects via polling
                if (data.deviceConnected && wasDisconnected) {
                    if (typeof hideWaitingForDevice === 'function') hideWaitingForDevice();
                }
            }
        } catch (error) {
            // Silently fail - WebSocket is primary connection
        }
    }, 5000);
}

/**
 * Console logging helper
 */
function logToConsole(message, type = 'info') {
    const consoleEl = document.getElementById('console');
    const timestamp = new Date().toLocaleTimeString();
    const entry = document.createElement('div');
    entry.className = `console-entry console-type-${type}`;
    entry.innerHTML = `<span class="console-timestamp">[${timestamp}]</span> ${message}`;
    consoleEl.appendChild(entry);

    // Auto-scroll to bottom
    consoleEl.scrollTop = consoleEl.scrollHeight;

    // Limit console entries to 100
    while (consoleEl.children.length > 100) {
        consoleEl.removeChild(consoleEl.firstChild);
    }
}

// Initialize app when DOM is ready
document.addEventListener('DOMContentLoaded', initApp);
