/**
 * Server configuration
 * Centralized settings for the Anti-Theft server
 */

const fs = require('fs');
const path = require('path');

const STATE_FILE = path.join(__dirname, '../../data/state.json');

/**
 * Loads persisted activation state from disk.
 * Returns false if file doesn't exist or is unreadable.
 */
function loadPersistedState() {
    try {
        if (fs.existsSync(STATE_FILE)) {
            const data = JSON.parse(fs.readFileSync(STATE_FILE, 'utf8'));
            console.log(`[Config] Loaded persisted state: activationState=${data.activationState}`);
            return data.activationState === true;
        }
    } catch (e) {
        console.warn('[Config] Failed to load persisted state:', e.message);
    }
    return false;
}

/**
 * Persists activation state to disk so it survives container restarts.
 */
function persistActivationState(activated) {
    try {
        const dir = path.dirname(STATE_FILE);
        if (!fs.existsSync(dir)) {
            fs.mkdirSync(dir, { recursive: true });
        }
        fs.writeFileSync(STATE_FILE, JSON.stringify({ activationState: activated }));
        console.log(`[Config] Persisted activationState=${activated}`);
    } catch (e) {
        console.error('[Config] Failed to persist state:', e.message);
    }
}

const config = {
    // Server settings
    port: process.env.PORT || 3000,
    host: '0.0.0.0',

    // IP whitelist for security
    // Add your home network IP addresses here
    whitelistedIPs: [
        '127.0.0.1',           // Localhost IPv4
        '::1',                  // Localhost IPv6
        '::ffff:127.0.0.1',    // IPv4-mapped IPv6 localhost
        '192.168.0.0/16',      // All 192.168.x.x private networks
        '172.16.0.0/12',       // Docker / private networks
        '10.0.0.0/8',          // Private network range
    ],

    // HTTPS/WSS settings
    useHTTPS: true,
    certPath: path.join(__dirname, '../../certs/cert.pem'),
    keyPath: path.join(__dirname, '../../certs/key.pem'),

    // Connection limits
    maxConnectedDevices: 1,  // Only one device can connect at a time

    // Application state (activationState persisted to disk, rest is runtime-only)
    state: {
        activationState: loadPersistedState(),
        connectedDevice: null,
        webClients: [],
        lastCheckIn: null,
    },

    // WebSocket settings
    websocket: {
        pingInterval: 30000,      // 30 seconds
        pingTimeout: 10000,       // 10 seconds
        maxPayloadSize: 1024 * 1024 * 10,  // 10MB for video frames
    },

    // Logging
    logLevel: process.env.LOG_LEVEL || 'info',

    // State persistence helper
    persistActivationState,
};

module.exports = config;
