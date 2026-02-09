/**
 * REST API Routes
 * Handles device check-ins, activation control, and status queries
 */

const express = require('express');
const router = express.Router();
const config = require('../config/config');
const authMiddleware = require('../middleware/auth');

/**
 * GET /api/status
 * Returns current server status
 * Public endpoint (no auth required)
 */
router.get('/status', (req, res) => {
    console.log('[API] GET /api/status');

    res.json({
        status: 'ok',
        message: 'Anti-Theft Server is running',
        timestamp: Date.now(),
        activated: config.state.activationState,
        deviceConnected: config.state.connectedDevice !== null,
        webClientsConnected: config.state.webClients.length,
        version: '1.0.0'
    });
});

/**
 * POST /api/check-in
 * Device periodic check-in endpoint
 * Requires authentication
 */
router.post('/check-in', authMiddleware, (req, res) => {
    const { device_id, timestamp } = req.body;
    const authToken = req.authToken;

    // Log timing since last check-in for debugging
    const now = Date.now();
    if (config.state.lastCheckIn) {
        const elapsed = Math.round((now - config.state.lastCheckIn.timestamp) / 1000);
        console.log(`[API] POST /api/check-in - Device: ${device_id} (${elapsed}s since last check-in)`);
    } else {
        console.log(`[API] POST /api/check-in - Device: ${device_id} (first check-in)`);
    }

    // Validate request body
    if (!device_id) {
        return res.status(400).json({
            error: 'Bad Request',
            message: 'Missing device_id in request body'
        });
    }

    // Update last check-in time
    config.state.lastCheckIn = {
        deviceId: device_id,
        authToken: authToken,
        timestamp: now
    };

    // Return activation state
    const response = {
        activated: config.state.activationState,
        message: config.state.activationState
            ? 'Tracking activated - start service'
            : 'No tracking requested',
        timestamp: Date.now()
    };

    console.log(`[API] Check-in response: activated=${response.activated}`);
    res.json(response);
});

/**
 * POST /api/activate
 * Activates device tracking
 * Used by web dashboard to start tracking
 */
router.post('/activate', (req, res) => {
    console.log('[API] POST /api/activate');

    config.state.activationState = true;
    config.persistActivationState(true);

    // Notify device of activation (if connected)
    if (config.state.connectedDevice) {
        try {
            config.state.connectedDevice.send(JSON.stringify({
                type: 'activation_changed',
                activated: true,
                timestamp: Date.now()
            }));
            // Also send start command to resume media if previously stopped
            config.state.connectedDevice.send(JSON.stringify({
                type: 'command',
                action: 'start',
                timestamp: Date.now()
            }));
            console.log('[API] Activation notification and start command sent to device');
        } catch (error) {
            console.error('[API] Failed to notify device of activation:', error.message);
        }
    }

    // Notify web clients via WebSocket (if connected)
    notifyWebClients({
        type: 'activation_changed',
        activated: true,
        timestamp: Date.now()
    });

    res.json({
        success: true,
        message: 'Tracking activated',
        activated: true,
        timestamp: Date.now()
    });
});

/**
 * POST /api/deactivate
 * Deactivates device tracking
 * Used by web dashboard to stop tracking
 */
router.post('/deactivate', (req, res) => {
    console.log('[API] POST /api/deactivate');

    config.state.activationState = false;
    config.persistActivationState(false);

    // Notify device of deactivation (if connected)
    if (config.state.connectedDevice) {
        try {
            config.state.connectedDevice.send(JSON.stringify({
                type: 'activation_changed',
                activated: false,
                timestamp: Date.now()
            }));
            console.log('[API] Deactivation notification sent to device');
        } catch (error) {
            console.error('[API] Failed to notify device of deactivation:', error.message);
        }
    }

    // Send stop command to device (if connected)
    if (config.state.connectedDevice) {
        try {
            config.state.connectedDevice.send(JSON.stringify({
                type: 'command',
                action: 'stop',
                timestamp: Date.now()
            }));
            console.log('[API] Stop command sent to device');
        } catch (error) {
            console.error('[API] Failed to send stop command:', error.message);
        }
    }

    // Notify web clients
    notifyWebClients({
        type: 'activation_changed',
        activated: false,
        timestamp: Date.now()
    });

    res.json({
        success: true,
        message: 'Tracking deactivated',
        activated: false,
        timestamp: Date.now()
    });
});

/**
 * GET /api/device-status
 * Returns detailed device connection status
 */
router.get('/device-status', (req, res) => {
    console.log('[API] GET /api/device-status');

    const deviceStatus = {
        connected: config.state.connectedDevice !== null,
        lastCheckIn: config.state.lastCheckIn,
        activationState: config.state.activationState,
        timestamp: Date.now()
    };

    res.json(deviceStatus);
});

/**
 * Helper function to notify all connected web clients
 */
function notifyWebClients(message) {
    config.state.webClients.forEach(client => {
        try {
            if (client.readyState === 1) { // WebSocket.OPEN
                client.send(JSON.stringify(message));
            }
        } catch (error) {
            console.error('[API] Failed to notify web client:', error.message);
        }
    });
}

module.exports = router;
