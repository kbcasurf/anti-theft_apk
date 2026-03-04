/**
 * WebSocket Server Handler
 * Manages connections between device and web clients
 * Forwards real-time data (location, video, audio) from device to web clients
 */

const WebSocket = require('ws');
const config = require('./config/config');

/**
 * Sets up WebSocket server on the provided HTTP/HTTPS server
 * @param {http.Server|https.Server} server - HTTP or HTTPS server instance
 * @returns {WebSocket.Server} WebSocket server instance
 */
function setupWebSocket(server) {
    const wss = new WebSocket.Server({
        server,
        path: '/ws',
        maxPayload: config.websocket.maxPayloadSize,
        perMessageDeflate: false  // Disable compression for real-time performance
    });

    console.log('[WebSocket] Server initialized on path /ws');

    wss.on('connection', (ws, req) => {
        const clientIP = req.socket.remoteAddress;
        console.log(`[WebSocket] New connection from ${clientIP}`);

        // Set client type on registration
        ws.clientType = null;
        ws.deviceId = null;
        ws.isAlive = true;

        // Handle pong responses for keep-alive
        ws.on('pong', () => {
            ws.isAlive = true;
        });

        // Handle incoming messages
        ws.on('message', (data) => {
            try {
                const message = JSON.parse(data.toString());
                handleMessage(ws, message, clientIP);
            } catch (error) {
                console.error('[WebSocket] Failed to parse message:', error.message);
                ws.send(JSON.stringify({
                    type: 'error',
                    message: 'Invalid message format'
                }));
            }
        });

        // Handle connection close
        ws.on('close', (code, reason) => {
            console.log(`[WebSocket] Connection closed from ${clientIP} - Code: ${code}, Reason: ${reason}`);
            handleDisconnect(ws);
        });

        // Handle errors
        ws.on('error', (error) => {
            console.error(`[WebSocket] Connection error from ${clientIP}:`, error.message);
        });

        // Send connection acknowledgment
        ws.send(JSON.stringify({
            type: 'connected',
            message: 'Connected to Anti-Theft server',
            timestamp: Date.now()
        }));
    });

    // Set up keep-alive ping interval
    const pingInterval = setInterval(() => {
        wss.clients.forEach((ws) => {
            if (ws.isAlive === false) {
                console.log('[WebSocket] Terminating inactive connection');
                return ws.terminate();
            }

            ws.isAlive = false;
            ws.ping();
        });
    }, config.websocket.pingInterval);

    // Clean up on server close
    wss.on('close', () => {
        clearInterval(pingInterval);
        console.log('[WebSocket] Server closed');
    });

    return wss;
}

/**
 * Handles incoming WebSocket messages
 */
function handleMessage(ws, message, clientIP) {
    const { type } = message;

    switch (type) {
        case 'register':
            handleRegistration(ws, message, clientIP);
            break;

        case 'location':
            forwardToWebClients(message);
            break;

        case 'video_frame':
            forwardToWebClients(message);
            break;

        case 'audio_chunk':
            forwardToWebClients(message);
            break;

        case 'status':
            forwardToWebClients(message);
            break;

        case 'command':
            handleCommand(ws, message);
            break;

        case 'command_ack':
            console.log(`[WebSocket] Device acknowledged command: ${message.action} (service: ${message.service || 'all'}, success: ${message.success})`);
            forwardToWebClients(message);
            break;

        default:
            console.warn(`[WebSocket] Unknown message type: ${type}`);
    }
}

/**
 * Handles client registration (device or web client)
 */
function handleRegistration(ws, message, clientIP) {
    const { client_type, device_id } = message;

    if (client_type === 'device') {
        // Device registration
        if (config.state.connectedDevice) {
            console.warn('[WebSocket] Device already connected, closing old connection');
            config.state.connectedDevice.close();
        }

        ws.clientType = 'device';
        ws.deviceId = device_id;
        config.state.connectedDevice = ws;

        console.log(`[WebSocket] ✓ Device registered: ${device_id} from ${clientIP}`);

        ws.send(JSON.stringify({
            type: 'registered',
            client_type: 'device',
            message: 'Device registered successfully',
            activated: config.state.activationState,
            timestamp: Date.now()
        }));

        // Notify all web clients that device connected
        notifyWebClients({
            type: 'device_status',
            connected: true,
            device_id: device_id,
            timestamp: Date.now()
        });

    } else if (client_type === 'web') {
        // Web client registration
        ws.clientType = 'web';
        config.state.webClients.push(ws);

        console.log(`[WebSocket] ✓ Web client registered from ${clientIP} (total: ${config.state.webClients.length})`);

        ws.send(JSON.stringify({
            type: 'registered',
            client_type: 'web',
            message: 'Web client registered successfully',
            device_connected: config.state.connectedDevice !== null,
            activated: config.state.activationState,
            timestamp: Date.now()
        }));

    } else {
        console.warn(`[WebSocket] Unknown client type: ${client_type}`);
        ws.send(JSON.stringify({
            type: 'error',
            message: 'Unknown client type. Use "device" or "web"'
        }));
    }
}

/**
 * Handles commands from web clients to device
 */
function handleCommand(ws, message) {
    console.log('[WebSocket] handleCommand called');
    console.log(`[WebSocket] Client type: ${ws.clientType}`);
    console.log(`[WebSocket] Message:`, JSON.stringify(message));

    if (ws.clientType !== 'web') {
        console.warn('[WebSocket] ❌ Command received from non-web client');
        return;
    }

    if (!config.state.connectedDevice) {
        console.warn('[WebSocket] ❌ No device connected');
        ws.send(JSON.stringify({
            type: 'error',
            message: 'No device connected'
        }));
        return;
    }

    console.log(`[WebSocket] Device connection state: ${config.state.connectedDevice.readyState}`);

    // Forward command to device
    try {
        const commandAction = message.action || message.command || 'unknown';
        const commandService = message.service || 'all';
        const messageToSend = JSON.stringify(message);

        console.log(`[WebSocket] 📤 Forwarding command '${commandAction}' (service: ${commandService}) to device`);
        console.log(`[WebSocket] Message being sent: ${messageToSend}`);

        config.state.connectedDevice.send(messageToSend);
        console.log(`[WebSocket] ✓ Command '${commandAction}' (service: ${commandService}) successfully forwarded to device`);

        // Send confirmation back to web client
        const confirmation = {
            type: 'command_sent',
            action: commandAction,
            timestamp: Date.now()
        };
        if (message.service) confirmation.service = message.service;
        ws.send(JSON.stringify(confirmation));
        console.log('[WebSocket] ✓ Confirmation sent to web client');
    } catch (error) {
        console.error('[WebSocket] ❌ Failed to forward command:', error.message);
        console.error('[WebSocket] Error stack:', error.stack);
        ws.send(JSON.stringify({
            type: 'error',
            message: 'Failed to send command to device'
        }));
    }
}

/**
 * Forwards messages from device to all web clients
 */
function forwardToWebClients(message) {
    const messageStr = JSON.stringify(message);
    let successCount = 0;

    config.state.webClients.forEach(client => {
        try {
            if (client.readyState === WebSocket.OPEN) {
                client.send(messageStr);
                successCount++;
            }
        } catch (error) {
            console.error('[WebSocket] Failed to forward to web client:', error.message);
        }
    });

    if (successCount > 0 && message.type !== 'video_frame') {
        console.log(`[WebSocket] Forwarded ${message.type} to ${successCount} web client(s)`);
    } else if (successCount === 0) {
        console.warn(`[WebSocket] ⚠ Received ${message.type} from device but no web clients connected to receive it`);
    }
}

/**
 * Notifies all web clients with a message
 */
function notifyWebClients(message) {
    config.state.webClients.forEach(client => {
        try {
            if (client.readyState === WebSocket.OPEN) {
                client.send(JSON.stringify(message));
            }
        } catch (error) {
            console.error('[WebSocket] Failed to notify web client:', error.message);
        }
    });
}

/**
 * Handles client disconnection
 */
function handleDisconnect(ws) {
    if (ws.clientType === 'device') {
        // Only clear if this ws is still the active device connection
        // (prevents a stale close event from clearing a newer connection)
        if (config.state.connectedDevice === ws) {
            console.log('[WebSocket] Device disconnected');
            config.state.connectedDevice = null;

            // Notify web clients
            notifyWebClients({
                type: 'device_status',
                connected: false,
                timestamp: Date.now()
            });
        } else {
            console.log('[WebSocket] Stale device connection closed (ignored)');
        }

    } else if (ws.clientType === 'web') {
        const index = config.state.webClients.indexOf(ws);
        if (index > -1) {
            config.state.webClients.splice(index, 1);
            console.log(`[WebSocket] Web client disconnected (remaining: ${config.state.webClients.length})`);
        }
    }
}

module.exports = setupWebSocket;
