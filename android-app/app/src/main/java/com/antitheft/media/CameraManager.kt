package com.antitheft.media

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import androidx.camera.core.*
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.antitheft.utils.Constants
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Manages camera capture using CameraX
 * Captures video frames at 15 fps and compresses them to JPEG
 */
class CameraManager(private val context: Context) {

    private var imageAnalyzer: ImageAnalysis? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var frameCallback: ((ByteArray) -> Unit)? = null
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    /**
     * Starts camera capture
     * @param lifecycleOwner Lifecycle owner (usually Service or Activity)
     * @param callback Function called when new frame is captured
     */
    fun startCapture(lifecycleOwner: LifecycleOwner, callback: (ByteArray) -> Unit) {
        if (!checkPermissions()) {
            Log.e(TAG, "Camera permission not granted")
            return
        }

        frameCallback = callback

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases(lifecycleOwner)
                Log.i(TAG, "Camera capture started")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start camera", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * Binds camera use cases to lifecycle
     */
    private fun bindCameraUseCases(lifecycleOwner: LifecycleOwner) {
        val cameraProvider = cameraProvider ?: run {
            Log.e(TAG, "Camera provider is null")
            return
        }

        // Select back camera
        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        // Create image analyzer for frame capture
        val resolutionSelector = ResolutionSelector.Builder()
            .setResolutionStrategy(
                ResolutionStrategy(
                    android.util.Size(Constants.VIDEO_WIDTH, Constants.VIDEO_HEIGHT),
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
                )
            )
            .build()

        imageAnalyzer = ImageAnalysis.Builder()
            .setResolutionSelector(resolutionSelector)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build()
            .also {
                it.setAnalyzer(cameraExecutor, FrameAnalyzer())
            }

        try {
            // Unbind all use cases before rebinding
            cameraProvider.unbindAll()

            // Bind use cases to camera
            camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                imageAnalyzer
            )

            Log.i(TAG, "Camera use cases bound successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind camera use cases", e)
        }
    }

    /**
     * Stops camera capture
     */
    fun stopCapture() {
        try {
            cameraProvider?.unbindAll()
            imageAnalyzer = null
            camera = null
            frameCallback = null
            Log.i(TAG, "Camera capture stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping camera", e)
        }
    }

    /**
     * Checks if camera permission is granted
     */
    fun checkPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Releases camera resources
     */
    fun release() {
        stopCapture()
        cameraExecutor.shutdown()
    }

    /**
     * Image analyzer that processes each frame
     */
    private inner class FrameAnalyzer : ImageAnalysis.Analyzer {
        private var lastAnalyzedTimestamp = 0L
        private val frameIntervalMs = 1000L / Constants.VIDEO_FPS // ~66ms for 15 fps

        override fun analyze(imageProxy: ImageProxy) {
            val currentTimestamp = System.currentTimeMillis()

            // Throttle frame rate
            if (currentTimestamp - lastAnalyzedTimestamp >= frameIntervalMs) {
                lastAnalyzedTimestamp = currentTimestamp

                try {
                    // Convert ImageProxy to JPEG byte array
                    val jpegBytes = imageProxyToJpeg(imageProxy)
                    jpegBytes?.let { frameCallback?.invoke(it) }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing frame", e)
                }
            }

            imageProxy.close()
        }

        /**
         * Converts ImageProxy (YUV format) to JPEG byte array
         */
        private fun imageProxyToJpeg(imageProxy: ImageProxy): ByteArray? {
            return try {
                val yBuffer = imageProxy.planes[0].buffer
                val uBuffer = imageProxy.planes[1].buffer
                val vBuffer = imageProxy.planes[2].buffer

                val ySize = yBuffer.remaining()
                val uSize = uBuffer.remaining()
                val vSize = vBuffer.remaining()

                val nv21 = ByteArray(ySize + uSize + vSize)

                // Copy Y plane
                yBuffer.get(nv21, 0, ySize)

                // Copy UV planes (convert YUV to NV21)
                val uvPixelStride = imageProxy.planes[1].pixelStride
                if (uvPixelStride == 1) {
                    // Tightly packed UV
                    vBuffer.get(nv21, ySize, vSize)
                    uBuffer.get(nv21, ySize + vSize, uSize)
                } else {
                    // Interleaved UV
                    val uvRowStride = imageProxy.planes[1].rowStride
                    val uvWidth = imageProxy.width / 2
                    val uvHeight = imageProxy.height / 2

                    var pos = ySize
                    for (row in 0 until uvHeight) {
                        for (col in 0 until uvWidth) {
                            val vuPos = row * uvRowStride + col * uvPixelStride
                            nv21[pos++] = vBuffer.get(vuPos)
                            nv21[pos++] = uBuffer.get(vuPos)
                        }
                    }
                }

                // Convert NV21 to JPEG
                val yuvImage = YuvImage(
                    nv21,
                    ImageFormat.NV21,
                    imageProxy.width,
                    imageProxy.height,
                    null
                )

                val outputStream = ByteArrayOutputStream()
                yuvImage.compressToJpeg(
                    Rect(0, 0, imageProxy.width, imageProxy.height),
                    Constants.JPEG_QUALITY,
                    outputStream
                )

                outputStream.toByteArray()

            } catch (e: Exception) {
                Log.e(TAG, "Failed to convert image to JPEG", e)
                null
            }
        }
    }

    companion object {
        private const val TAG = "CameraManager"
    }
}
