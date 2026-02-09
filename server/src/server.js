/**
 * Anti-Theft Server
 * Main server file - Handles HTTPS, REST API, and WebSocket connections
 */

const express = require('express');
const https = require('https');
const fs = require('fs');
const path = require('path');
const cors = require('cors');

// Load configuration and middleware
const config = require('./config/config');
const ipWhitelist = require('./middleware/ipWhitelist');
const apiRoutes = require('./routes/api');
const setupWebSocket = require('./websocket');

// Create Express application
const app = express();

// Apply IP whitelist middleware globally
app.use(ipWhitelist);

// Enable CORS for web dashboard
app.use(cors());

// Parse JSON request bodies
app.use(express.json({ limit: '50mb' }));
app.use(express.urlencoded({ extended: true, limit: '50mb' }));

// Request logging middleware
app.use((req, res, next) => {
    console.log(`[${new Date().toISOString()}] ${req.method} ${req.path}`);
    next();
});

// Mount API routes
app.use('/api', apiRoutes);

// Serve static files from public directory
app.use(express.static(path.join(__dirname, '../public')));

// Serve index.html for root path
app.get('/', (req, res) => {
    res.sendFile(path.join(__dirname, '../public/index.html'));
});

// 404 handler for API routes
app.use('/api/*', (req, res) => {
    res.status(404).json({
        error: 'Not Found',
        message: `API endpoint ${req.path} not found`
    });
});

// Load SSL certificates
let serverInstance;

if (config.useHTTPS) {
    try {
        const sslOptions = {
            key: fs.readFileSync(config.keyPath),
            cert: fs.readFileSync(config.certPath)
        };

        // Create HTTPS server
        serverInstance = https.createServer(sslOptions, app);
        console.log('[Server] HTTPS enabled with SSL certificates');

    } catch (error) {
        console.error('[Server] Failed to load SSL certificates:', error.message);
        console.error('[Server] Make sure cert.pem and key.pem exist in the certs directory');
        process.exit(1);
    }
} else {
    // Fall back to HTTP (not recommended for production)
    const http = require('http');
    serverInstance = http.createServer(app);
    console.warn('[Server] ⚠️  Running without HTTPS (not recommended)');
}

// Attach WebSocket server
setupWebSocket(serverInstance);

// Start listening
serverInstance.listen(config.port, config.host, () => {
    console.log('');
    console.log('═══════════════════════════════════════════════════════');
    console.log('   🛡️  Anti-Theft Server');
    console.log('═══════════════════════════════════════════════════════');
    console.log('');
    console.log(`   Status: ✓ Running`);
    console.log(`   Protocol: ${config.useHTTPS ? 'HTTPS' : 'HTTP'}`);
    console.log(`   Host: ${config.host}`);
    console.log(`   Port: ${config.port}`);
    console.log('');
    console.log(`   Web Dashboard: https://${config.host === '0.0.0.0' ? 'localhost' : config.host}:${config.port}`);
    console.log(`   API Endpoint: https://${config.host === '0.0.0.0' ? 'localhost' : config.host}:${config.port}/api`);
    console.log(`   WebSocket: wss://${config.host === '0.0.0.0' ? 'localhost' : config.host}:${config.port}/ws`);
    console.log('');
    console.log(`   Whitelisted IPs: ${config.whitelistedIPs.length} configured`);
    console.log(`   Max Devices: ${config.maxConnectedDevices}`);
    console.log('');
    console.log('═══════════════════════════════════════════════════════');
    console.log('');
    console.log('Press Ctrl+C to stop the server');
    console.log('');
});

// Graceful shutdown
process.on('SIGTERM', () => {
    console.log('[Server] SIGTERM received, shutting down gracefully...');
    serverInstance.close(() => {
        console.log('[Server] Server closed');
        process.exit(0);
    });
});

process.on('SIGINT', () => {
    console.log('\n[Server] SIGINT received, shutting down gracefully...');
    serverInstance.close(() => {
        console.log('[Server] Server closed');
        process.exit(0);
    });
});

// Handle uncaught exceptions
process.on('uncaughtException', (error) => {
    console.error('[Server] Uncaught Exception:', error);
    process.exit(1);
});

process.on('unhandledRejection', (reason, promise) => {
    console.error('[Server] Unhandled Rejection at:', promise, 'reason:', reason);
    process.exit(1);
});

module.exports = serverInstance;
