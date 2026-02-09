/**
 * Anti-Theft Dashboard - Audio Module
 * Handles audio playback from PCM stream using Web Audio API
 */

const audioModule = {
    audioContext: null,
    audioQueue: [],
    isPlaying: false,
    isMuted: false,
    volume: 1.0,
    chunkCount: 0,
    nextPlayTime: 0,
    sampleRate: 16000,
    channels: 1,
    bitDepth: 16
};

/**
 * Initialize audio player
 */
function initAudio() {
    try {
        // Create Audio Context
        const AudioContext = window.AudioContext || window.webkitAudioContext;
        audioModule.audioContext = new AudioContext();

        logToConsole(`Audio initialized (rate: ${audioModule.audioContext.sampleRate}Hz)`, 'info');

        // Add audio controls
        addAudioControls();

    } catch (error) {
        logToConsole(`Failed to initialize audio: ${error.message}`, 'error');
        console.error('Audio initialization error:', error);
    }
}

/**
 * Add audio controls to the page
 */
function addAudioControls() {
    // Find a suitable place to add controls (video panel or create new panel)
    const videoPanel = document.querySelector('.panel h2');
    if (videoPanel && videoPanel.textContent.includes('Video')) {
        const audioControls = document.createElement('div');
        audioControls.id = 'audio-controls';
        audioControls.style.cssText = 'margin-top:15px; display:flex; gap:10px; align-items:center;';
        audioControls.innerHTML = `
            <button id="audio-mute-btn" class="btn-refresh" style="padding:8px 16px; font-size:14px;">
                🔊 Mute Audio
            </button>
            <span style="font-size:12px; color:#666;">
                Chunks: <span id="audio-chunk-counter">0</span>
            </span>
        `;
        videoPanel.parentElement.appendChild(audioControls);

        // Add mute button handler
        document.getElementById('audio-mute-btn').addEventListener('click', toggleMute);
    }
}

/**
 * Handle audio chunk from device
 */
function handleAudioChunk(message) {
    try {
        const { data, timestamp, size, format, sample_rate, channels, bit_depth } = message;

        if (!data) {
            logToConsole('Received empty audio chunk', 'warning');
            return;
        }

        // Update audio parameters if provided
        if (sample_rate) audioModule.sampleRate = sample_rate;
        if (channels) audioModule.channels = channels;
        if (bit_depth) audioModule.bitDepth = bit_depth;

        // Decode Base64 to binary
        const binaryData = base64ToArrayBuffer(data);

        // Convert to AudioBuffer
        const audioBuffer = pcmToAudioBuffer(binaryData);

        if (audioBuffer) {
            // Add to queue
            audioModule.audioQueue.push(audioBuffer);

            // Start playback if not already playing
            if (!audioModule.isPlaying) {
                startAudioPlayback();
            }

            // Update chunk counter
            audioModule.chunkCount++;
            updateAudioStats();

            // Log occasionally (every 100 chunks)
            if (audioModule.chunkCount % 100 === 0) {
                logToConsole(`Audio: ${audioModule.chunkCount} chunks received (${(size / 1024).toFixed(1)} KB)`, 'info');
            }
        }

    } catch (error) {
        logToConsole(`Error processing audio chunk: ${error.message}`, 'error');
        console.error('Audio chunk error:', error);
    }
}

/**
 * Convert Base64 string to ArrayBuffer
 */
function base64ToArrayBuffer(base64) {
    const binaryString = atob(base64);
    const bytes = new Uint8Array(binaryString.length);
    for (let i = 0; i < binaryString.length; i++) {
        bytes[i] = binaryString.charCodeAt(i);
    }
    return bytes.buffer;
}

/**
 * Convert PCM data to AudioBuffer
 */
function pcmToAudioBuffer(arrayBuffer) {
    try {
        // PCM 16-bit data
        const pcmData = new Int16Array(arrayBuffer);
        const numSamples = pcmData.length;

        // Create AudioBuffer
        const audioBuffer = audioModule.audioContext.createBuffer(
            audioModule.channels,
            numSamples,
            audioModule.sampleRate
        );

        // Convert Int16 to Float32 (-1.0 to 1.0)
        const channelData = audioBuffer.getChannelData(0);
        for (let i = 0; i < numSamples; i++) {
            channelData[i] = pcmData[i] / 32768.0; // Normalize to -1.0 to 1.0
        }

        return audioBuffer;

    } catch (error) {
        console.error('Failed to convert PCM to AudioBuffer:', error);
        return null;
    }
}

/**
 * Start audio playback
 */
function startAudioPlayback() {
    if (audioModule.isPlaying) {
        return;
    }

    audioModule.isPlaying = true;
    audioModule.nextPlayTime = audioModule.audioContext.currentTime;

    // Schedule initial chunks
    scheduleAudioChunks();

    logToConsole('Audio playback started', 'info');
}

/**
 * Schedule audio chunks for playback
 */
function scheduleAudioChunks() {
    if (!audioModule.isPlaying) {
        return;
    }

    const currentTime = audioModule.audioContext.currentTime;

    // Schedule chunks from queue
    while (audioModule.audioQueue.length > 0 && audioModule.nextPlayTime < currentTime + 0.5) {
        const audioBuffer = audioModule.audioQueue.shift();
        playAudioBuffer(audioBuffer);
    }

    // Continue scheduling
    setTimeout(() => scheduleAudioChunks(), 100);
}

/**
 * Play an AudioBuffer
 */
function playAudioBuffer(audioBuffer) {
    try {
        // Create buffer source
        const source = audioModule.audioContext.createBufferSource();
        source.buffer = audioBuffer;

        // Create gain node for volume control
        const gainNode = audioModule.audioContext.createGain();
        gainNode.gain.value = audioModule.isMuted ? 0 : audioModule.volume;

        // Connect nodes
        source.connect(gainNode);
        gainNode.connect(audioModule.audioContext.destination);

        // Schedule playback
        source.start(audioModule.nextPlayTime);

        // Update next play time
        audioModule.nextPlayTime += audioBuffer.duration;

    } catch (error) {
        console.error('Failed to play audio buffer:', error);
    }
}

/**
 * Toggle mute
 */
function toggleMute() {
    audioModule.isMuted = !audioModule.isMuted;

    const btn = document.getElementById('audio-mute-btn');
    if (audioModule.isMuted) {
        btn.textContent = '🔇 Unmute Audio';
        logToConsole('Audio muted', 'info');
    } else {
        btn.textContent = '🔊 Mute Audio';
        logToConsole('Audio unmuted', 'info');
    }
}

/**
 * Update audio statistics
 */
function updateAudioStats() {
    const counter = document.getElementById('audio-chunk-counter');
    if (counter) {
        counter.textContent = audioModule.chunkCount;
    }
}

/**
 * Stop audio playback
 */
function stopAudio() {
    audioModule.isPlaying = false;
    audioModule.audioQueue = [];
    audioModule.chunkCount = 0;
    audioModule.nextPlayTime = 0;
    updateAudioStats();
    logToConsole('Audio playback stopped', 'info');
}
