/**
 * Anti-Theft Dashboard - Video Module
 * Handles video frame display from JPEG stream
 */

const videoModule = {
    videoElement: null,
    placeholderElement: null,
    isStreaming: false,
    frameCount: 0,
    lastFrameTime: 0,
    fpsHistory: [],
    maxFpsHistory: 30
};

/**
 * Initialize video player
 */
function initVideo() {
    videoModule.videoElement = document.getElementById('video');
    videoModule.placeholderElement = document.getElementById('video-placeholder');

    // Add FPS counter to video container
    const videoContainer = document.getElementById('video-container');
    const statsDiv = document.createElement('div');
    statsDiv.id = 'video-stats';
    statsDiv.style.cssText = 'position:absolute; top:10px; right:10px; background:rgba(0,0,0,0.7); color:white; padding:8px 12px; border-radius:4px; font-size:12px; font-family:monospace;';
    statsDiv.innerHTML = 'FPS: <span id="fps-counter">0</span> | Frames: <span id="frame-counter">0</span>';
    videoContainer.style.position = 'relative';
    videoContainer.appendChild(statsDiv);

    logToConsole('Video player initialized', 'info');
}

/**
 * Handle video frame from device
 */
function handleVideoFrame(message) {
    try {
        const { data, timestamp, size, format } = message;

        if (!data) {
            logToConsole('Received empty video frame', 'warning');
            return;
        }

        // Show video element, hide placeholder
        if (!videoModule.isStreaming) {
            videoModule.videoElement.style.display = 'block';
            videoModule.placeholderElement.style.display = 'none';
            videoModule.isStreaming = true;
            logToConsole('Video streaming started', 'info');
        }

        // Decode Base64 and display
        displayFrame(data);

        // Update stats
        videoModule.frameCount++;
        updateVideoStats(timestamp, size);

    } catch (error) {
        logToConsole(`Error displaying video frame: ${error.message}`, 'error');
        console.error('Video frame error:', error);
    }
}

/**
 * Display video frame
 */
function displayFrame(base64Data) {
    try {
        // Create data URL from Base64
        const dataUrl = `data:image/jpeg;base64,${base64Data}`;

        // Update image source
        videoModule.videoElement.src = dataUrl;

    } catch (error) {
        console.error('Failed to display frame:', error);
    }
}

/**
 * Update video statistics (FPS and frame counter)
 */
function updateVideoStats(timestamp, size) {
    const now = Date.now();

    // Calculate FPS
    if (videoModule.lastFrameTime > 0) {
        const timeDiff = now - videoModule.lastFrameTime;
        if (timeDiff > 0) {
            const instantFps = 1000 / timeDiff;
            videoModule.fpsHistory.push(instantFps);

            // Limit history
            if (videoModule.fpsHistory.length > videoModule.maxFpsHistory) {
                videoModule.fpsHistory.shift();
            }

            // Calculate average FPS
            const avgFps = videoModule.fpsHistory.reduce((a, b) => a + b, 0) / videoModule.fpsHistory.length;

            // Update UI
            document.getElementById('fps-counter').textContent = avgFps.toFixed(1);
        }
    }

    videoModule.lastFrameTime = now;

    // Update frame counter
    document.getElementById('frame-counter').textContent = videoModule.frameCount;

    // Log occasionally (every 100 frames)
    if (videoModule.frameCount % 100 === 0) {
        logToConsole(`Video: ${videoModule.frameCount} frames received (${(size / 1024).toFixed(1)} KB)`, 'info');
    }
}

/**
 * Reset video stats
 */
function resetVideoStats() {
    videoModule.frameCount = 0;
    videoModule.fpsHistory = [];
    videoModule.lastFrameTime = 0;
    document.getElementById('fps-counter').textContent = '0';
    document.getElementById('frame-counter').textContent = '0';
}

/**
 * Stop video streaming
 */
function stopVideo() {
    videoModule.isStreaming = false;
    videoModule.videoElement.style.display = 'none';
    videoModule.placeholderElement.style.display = 'block';
    videoModule.videoElement.src = '';
    resetVideoStats();
    logToConsole('Video streaming stopped', 'info');
}
