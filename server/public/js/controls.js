/**
 * Anti-Theft Dashboard - Controls Module
 * Handles activation/deactivation buttons and per-service stop/start controls
 */

const controlsModule = {
    activateBtn: null,
    deactivateBtn: null,
    refreshBtn: null,
    isProcessing: false,

    // Per-service toggle state and button references
    services: {
        location: { stopped: false, btn: null },
        audio:    { stopped: false, btn: null },
        camera:   { stopped: false, btn: null }
    }
};

// Service display config (icon + label)
const SERVICE_DISPLAY = {
    location: { icon: '📍', label: 'Location' },
    audio:    { icon: '🎤', label: 'Audio' },
    camera:   { icon: '📷', label: 'Camera' }
};

/**
 * Initialize controls
 */
function initControls() {
    controlsModule.activateBtn = document.getElementById('btn-activate');
    controlsModule.deactivateBtn = document.getElementById('btn-deactivate');
    controlsModule.refreshBtn = document.getElementById('btn-refresh');

    // Per-service buttons
    controlsModule.services.location.btn = document.getElementById('btn-stop-location');
    controlsModule.services.audio.btn = document.getElementById('btn-stop-audio');
    controlsModule.services.camera.btn = document.getElementById('btn-stop-camera');

    // Add event listeners
    controlsModule.activateBtn.addEventListener('click', handleActivate);
    controlsModule.deactivateBtn.addEventListener('click', handleDeactivate);
    controlsModule.refreshBtn.addEventListener('click', handleRefresh);

    // Per-service toggle listeners
    for (const serviceName of Object.keys(controlsModule.services)) {
        controlsModule.services[serviceName].btn.addEventListener('click', () => {
            handleServiceToggle(serviceName);
        });
    }

    logToConsole('Controls initialized', 'info');
}

/**
 * Toggles a single service (stop or start)
 */
async function handleServiceToggle(serviceName) {
    if (controlsModule.isProcessing) return;

    const service = controlsModule.services[serviceName];
    const display = SERVICE_DISPLAY[serviceName];

    // If currently stopped, resume it
    if (service.stopped) {
        return resumeService(serviceName);
    }

    // Confirmation before stopping
    if (!confirm(`Stop ${display.label.toLowerCase()} transmission?`)) return;

    try {
        controlsModule.isProcessing = true;
        service.btn.disabled = true;
        service.btn.textContent = '⏳ Stopping...';

        logToConsole(`Sending stop command for ${display.label}...`, 'info');

        if (app.ws && app.ws.readyState === WebSocket.OPEN) {
            app.ws.send(JSON.stringify({
                type: 'command',
                action: 'stop',
                service: serviceName,
                timestamp: Date.now()
            }));

            logToConsole(`✓ Stop ${display.label} command sent`, 'info');
            service.stopped = true;

            // Stop local playback for the specific service
            if (serviceName === 'camera' && typeof stopVideo === 'function') stopVideo();
            if (serviceName === 'audio' && typeof stopAudio === 'function') stopAudio();
        } else {
            logToConsole('✗ WebSocket not connected — cannot send command', 'error');
        }

    } catch (error) {
        logToConsole(`✗ Stop ${display.label} error: ${error.message}`, 'error');
    } finally {
        controlsModule.isProcessing = false;
        service.btn.disabled = false;
        updateServiceButton(serviceName);
    }
}

/**
 * Resumes a previously stopped service
 */
async function resumeService(serviceName) {
    const service = controlsModule.services[serviceName];
    const display = SERVICE_DISPLAY[serviceName];

    try {
        controlsModule.isProcessing = true;
        service.btn.disabled = true;
        service.btn.textContent = '⏳ Resuming...';

        logToConsole(`Sending start command for ${display.label}...`, 'info');

        if (app.ws && app.ws.readyState === WebSocket.OPEN) {
            app.ws.send(JSON.stringify({
                type: 'command',
                action: 'start',
                service: serviceName,
                timestamp: Date.now()
            }));

            logToConsole(`✓ Start ${display.label} command sent`, 'info');
            service.stopped = false;
        } else {
            logToConsole('✗ WebSocket not connected — cannot send command', 'error');
        }

    } catch (error) {
        logToConsole(`✗ Resume ${display.label} error: ${error.message}`, 'error');
    } finally {
        controlsModule.isProcessing = false;
        service.btn.disabled = false;
        updateServiceButton(serviceName);
    }
}

/**
 * Updates a service button's text/icon based on current state
 */
function updateServiceButton(serviceName) {
    const service = controlsModule.services[serviceName];
    const display = SERVICE_DISPLAY[serviceName];

    if (service.stopped) {
        service.btn.textContent = `▶️ Start ${display.label}`;
    } else {
        service.btn.textContent = `${display.icon} Stop ${display.label}`;
    }
}

/**
 * Resets all service toggle states (used on deactivation)
 */
function resetServiceStates() {
    for (const serviceName of Object.keys(controlsModule.services)) {
        controlsModule.services[serviceName].stopped = false;
        updateServiceButton(serviceName);
    }
}

/**
 * Handle activate button click
 */
async function handleActivate() {
    if (controlsModule.isProcessing) {
        return;
    }

    try {
        controlsModule.isProcessing = true;
        controlsModule.activateBtn.disabled = true;
        controlsModule.activateBtn.textContent = '⏳ Activating...';

        logToConsole('Sending activation request...', 'info');

        const response = await fetch('/api/activate', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            }
        });

        const data = await response.json();

        if (data.success) {
            logToConsole('Tracking activated successfully', 'info');
            updateTrackingStatus(true);

            // Show waiting indicator if device is not connected yet
            if (!app.deviceConnected) {
                showWaitingForDevice();
            }
        } else {
            logToConsole(`Activation failed: ${data.message || 'Unknown error'}`, 'error');
        }

    } catch (error) {
        logToConsole(`Activation error: ${error.message}`, 'error');
    } finally {
        controlsModule.isProcessing = false;
        controlsModule.activateBtn.disabled = false;
        controlsModule.activateBtn.textContent = '🎯 Activate Tracking';
    }
}

/**
 * Handle deactivate button click
 */
async function handleDeactivate() {
    if (controlsModule.isProcessing) {
        return;
    }

    // Confirmation dialog
    if (!confirm('Are you sure you want to stop tracking?')) {
        return;
    }

    try {
        controlsModule.isProcessing = true;
        controlsModule.deactivateBtn.disabled = true;
        controlsModule.deactivateBtn.textContent = '⏳ Deactivating...';

        logToConsole('Sending deactivation request...', 'info');

        const response = await fetch('/api/deactivate', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            }
        });

        const data = await response.json();

        if (data.success) {
            logToConsole('Tracking deactivated successfully', 'info');
            updateTrackingStatus(false);

            // Reset all per-service toggle states
            resetServiceStates();

            // Stop media streams
            stopVideo();
            stopAudio();
        } else {
            logToConsole(`Deactivation failed: ${data.message || 'Unknown error'}`, 'error');
        }

    } catch (error) {
        logToConsole(`Deactivation error: ${error.message}`, 'error');
    } finally {
        controlsModule.isProcessing = false;
        controlsModule.deactivateBtn.disabled = false;
        controlsModule.deactivateBtn.textContent = '⏹️ Deactivate Tracking';
    }
}

/**
 * Handle refresh button click
 */
async function handleRefresh() {
    if (controlsModule.isProcessing) {
        return;
    }

    try {
        controlsModule.isProcessing = true;
        controlsModule.refreshBtn.disabled = true;
        controlsModule.refreshBtn.textContent = '⏳ Refreshing...';

        logToConsole('Refreshing status...', 'info');

        // Fetch server status
        const statusResponse = await fetch('/api/status');
        const statusData = await statusResponse.json();

        logToConsole(`Server status: ${statusData.status}`, 'info');
        logToConsole(`Activated: ${statusData.activated}`, 'info');
        logToConsole(`Device connected: ${statusData.deviceConnected}`, 'info');

        // Update UI
        updateTrackingStatus(statusData.activated);
        updateDeviceStatus(statusData.deviceConnected ? 'connected' : 'disconnected');

        // Fetch device status
        const deviceResponse = await fetch('/api/device-status');
        const deviceData = await deviceResponse.json();

        if (deviceData.lastCheckIn) {
            const checkInTime = new Date(deviceData.lastCheckIn.timestamp || Date.now()).toLocaleString();
            logToConsole(`Last check-in: ${checkInTime} (Device: ${deviceData.lastCheckIn.deviceId})`, 'info');
        }

    } catch (error) {
        logToConsole(`Refresh error: ${error.message}`, 'error');
    } finally {
        controlsModule.isProcessing = false;
        controlsModule.refreshBtn.disabled = false;
        controlsModule.refreshBtn.textContent = '🔄 Refresh Status';
    }
}

/**
 * Enable/disable controls based on connection state
 */
function setControlsEnabled(enabled) {
    controlsModule.activateBtn.disabled = !enabled;
    controlsModule.deactivateBtn.disabled = !enabled;
    controlsModule.refreshBtn.disabled = !enabled;

    for (const serviceName of Object.keys(controlsModule.services)) {
        controlsModule.services[serviceName].btn.disabled = !enabled;
    }

    if (!enabled) {
        logToConsole('Controls disabled: No server connection', 'warning');
    }
}

/**
 * Shows a "waiting for device" banner when tracking is activated but device hasn't connected yet
 */
function showWaitingForDevice() {
    // Remove existing banner if any
    hideWaitingForDevice();

    const banner = document.createElement('div');
    banner.id = 'waiting-banner';
    banner.style.cssText = 'background:#fff3cd; color:#856404; border:1px solid #ffc107; border-radius:8px; padding:12px 16px; margin:12px 0; text-align:center; font-size:14px; animation: pulse 2s infinite;';

    const icon = document.createTextNode('\u23F3 ');
    const bold = document.createElement('strong');
    bold.textContent = 'Tracking activated';
    const rest = document.createTextNode(' \u2014 waiting for device to connect (up to 2 minutes)...');
    banner.appendChild(icon);
    banner.appendChild(bold);
    banner.appendChild(rest);

    // Add pulse animation
    const style = document.createElement('style');
    style.id = 'waiting-banner-style';
    style.textContent = '@keyframes pulse { 0%,100% { opacity:1; } 50% { opacity:0.7; } }';
    document.head.appendChild(style);

    // Insert after the control panel
    const controlPanel = document.querySelector('.control-panel') || controlsModule.activateBtn?.parentElement;
    if (controlPanel) {
        controlPanel.after(banner);
    }

    logToConsole('Waiting for device to connect...', 'warning');
}

/**
 * Hides the "waiting for device" banner
 */
function hideWaitingForDevice() {
    const banner = document.getElementById('waiting-banner');
    if (banner) banner.remove();
    const style = document.getElementById('waiting-banner-style');
    if (style) style.remove();
}
