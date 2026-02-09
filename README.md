# Anti-Theft APK

A personal anti-theft system for Android that lets you remotely track your phone's GPS location, stream live video from the camera, and capture audio from the microphone — all controlled from a self-hosted web dashboard on your home network.

> **For personal use only.** Using this on someone else's device without consent is illegal. Ensure compliance with local privacy laws.

## How It Works

```
┌─────────────┐         ┌──────────────┐         ┌─────────────┐
│   Android    │  poll   │ Home Server  │  view   │  Web UI     │
│    Phone     │────────>│  (Node.js)   │<────────│ (Browser)   │
│              │  stream │              │broadcast│             │
│              │========>│              │========>│             │
└─────────────┘   WSS   └──────────────┘   WSS   └─────────────┘
```

1. **Idle** — The phone checks in with the server every 15 minutes via WorkManager (minimal battery: ~1-2%/day)
2. **Activate** — You click "Activate" on the web dashboard
3. **Track** — On the next check-in, the phone starts a foreground service, opens a WebSocket, and streams location, video, and audio in real-time to the dashboard

## Features

- **GPS tracking** — Live location displayed on an interactive map (Leaflet.js)
- **Camera streaming** — Real-time video feed from the phone camera (CameraX → JPEG frames)
- **Audio streaming** — Live microphone capture (AudioRecord → PCM chunks)
- **Remote activation** — Trigger tracking from the web dashboard
- **Battery-efficient** — Dormant until activated; ~5-10%/hour when actively tracking
- **Self-hosted** — Runs on your home network, no cloud dependencies
- **IP whitelist** — Only your configured IPs can access the server
- **Encrypted** — HTTPS/WSS with self-signed TLS certificates
- **No data stored** — Real-time streaming only, nothing persists on the server
- **Docker support** — Deploy the server with `docker compose up`

## Project Structure

```
anti-theft_apk/
├── android-app/                # Kotlin Android application
│   └── app/src/main/java/com/antitheft/
│       ├── ui/                 # MainActivity
│       ├── worker/             # CheckInWorker (WorkManager)
│       ├── service/            # TrackingService (foreground)
│       ├── network/            # ApiClient, WebSocketClient, NetworkMonitor
│       ├── media/              # CameraManager, AudioRecorder, LocationProvider
│       ├── receiver/           # ServiceRestartReceiver
│       └── utils/              # Constants, PreferencesManager, etc.
│
├── server/                     # Node.js home server
│   ├── src/
│   │   ├── server.js           # Express + HTTPS entry point
│   │   ├── websocket.js        # WebSocket relay (phone → browser)
│   │   ├── routes/api.js       # REST API (check-in, activate, status)
│   │   ├── middleware/         # IP whitelist, auth token
│   │   └── config/config.js   # Server configuration
│   └── public/                 # Web dashboard (HTML/CSS/JS)
│       ├── index.html
│       └── js/                 # app.js, map.js, video.js, audio.js, controls.js
│
├── docker-compose.yml          # Docker deployment for the server
└── docs/                       # Architecture and implementation guides
```

## Tech Stack

| Component     | Technology                                                        |
|---------------|-------------------------------------------------------------------|
| Android app   | Kotlin, Android SDK (API 24+), WorkManager, CameraX, OkHttp      |
| Server        | Node.js 18+, Express.js, ws (WebSocket)                          |
| Web dashboard | Vanilla JS, Leaflet.js (maps), Web Audio API                     |
| Security      | Self-signed TLS, IP whitelist, Bearer token auth                  |
| Deployment    | Docker Compose                                                    |

## Prerequisites

- **Android Studio** (Hedgehog or later)
- **JDK 17+**
- **Node.js 18+** and npm
- **OpenSSL** (for generating TLS certificates)
- **Android device** running Android 7.0+ (API 24)

## Setup

### 1. Server

```bash
cd server
npm install

# Generate self-signed TLS certificates
mkdir -p certs
openssl req -x509 -newkey rsa:4096 -keyout certs/key.pem -out certs/cert.pem -days 365 -nodes

# Configure your home IP in src/config/config.js (whitelistedIPs array)

# Start the server
npm start          # production
npm run dev        # development (auto-reload with nodemon)
```

The dashboard will be available at `https://localhost:3000`.

#### Docker alternative

```bash
# Make sure certs/ directory has cert.pem and key.pem
docker compose up -d
```

### 2. Android App

```bash
cd android-app
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

Then on the phone:
1. Open the app and enter your server IP and port
2. Grant the required permissions (camera, microphone, location, notifications)
3. Disable battery optimization for the app

### 3. Test

```bash
# Verify the server is running
curl -k https://localhost:3000/api/status

# Watch Android logs
adb logcat -s AntiTheft:*
```

Wait for a check-in (up to 15 minutes) or trigger one manually, then click **Activate** on the web dashboard.

## API Endpoints

| Method | Endpoint           | Description                          | Auth    |
|--------|--------------------|--------------------------------------|---------|
| POST   | `/api/check-in`    | Phone periodic check-in              | Bearer  |
| POST   | `/api/activate`    | Enable tracking mode                 | —       |
| POST   | `/api/deactivate`  | Disable tracking mode                | —       |
| GET    | `/api/status`      | Current server/device status         | —       |

WebSocket endpoint: `wss://<host>:3000/ws`

## Security

- **IP whitelist** — Server rejects connections from non-whitelisted IPs
- **TLS encryption** — All HTTP and WebSocket traffic uses HTTPS/WSS
- **Auth token** — Phone API requests include a Bearer token
- **No persistence** — Video, audio, and location data is never stored
- **Single device** — Only one phone can connect at a time

### Recommended practices

- Keep the server behind a firewall
- Use a strong, unique auth token
- Use a VPN if accessing remotely
- Review server logs regularly

## Battery Impact

| State   | What's running                               | Estimated drain     |
|---------|----------------------------------------------|---------------------|
| Idle    | WorkManager check-in every 15 min            | ~1-2% per day       |
| Active  | GPS + camera + audio + WebSocket             | ~5-10% per hour     |

## Troubleshooting

| Problem                  | Things to check                                              |
|--------------------------|--------------------------------------------------------------|
| App not checking in      | Server URL/port, network connectivity, firewall rules        |
| Can't connect to server  | SSL certificate, IP whitelist, auth token                    |
| No video/audio           | Permissions granted, foreground service notification visible  |
| Battery draining in idle | Verify tracking is deactivated, check background services    |

## License

This project is for personal use only. Use at your own risk.
