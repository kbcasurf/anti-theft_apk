# Anti-Theft Android Application

A privacy-focused anti-theft system for Android devices with remote activation and real-time monitoring.

## Project Overview

This project consists of two main components:

1. **Android Application** - Runs on the phone with adaptive check-ins and real-time streaming when activated
2. **Home Server** - Control center for activating tracking and viewing location, camera, and audio feeds

## Key Features

- **Adaptive Check-ins**: Battery-aware periodic polling (1/5/15 min intervals)
- **Remote Activation**: Activate tracking from home server dashboard
- **Real-time Streaming**: GPS location, camera video (both cameras), and audio
- **WebSocket Communication**: Persistent connection when active for instant control
- **IP Whitelisting**: Only your home IP can access the data
- **Stealth Mode**: Disguised app to avoid detection
- **Privacy First**: No Google services, no cloud storage, full control

## Technology Stack

### Android App
- Kotlin
- Jetpack Compose (UI)
- WorkManager (periodic tasks)
- CameraX (camera access)
- FusedLocationProvider (GPS)
- WebSockets (real-time communication)

### Home Server
- Python FastAPI
- WebSockets
- SQLite
- Leaflet.js (mapping)
- HTML5/CSS/JS (dashboard)

## Project Status

Currently in initial planning phase (v0.1.0-planning)

## License

Personal use only.
