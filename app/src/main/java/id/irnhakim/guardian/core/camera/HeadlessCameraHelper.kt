package id.irnhakim.guardian.core.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

class HeadlessCameraHelper(private val context: Context) {

    private val tag = "HeadlessCamera"

    @SuppressLint("MissingPermission")
    suspend fun capturePhoto(useFrontCamera: Boolean): String? = suspendCancellableCoroutine { continuation ->
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        if (cameraManager == null) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }

        val targetFacing = if (useFrontCamera) {
            CameraCharacteristics.LENS_FACING_FRONT
        } else {
            CameraCharacteristics.LENS_FACING_BACK
        }

        var selectedCameraId: String? = null
        for (id in cameraManager.cameraIdList) {
            val characteristics = cameraManager.getCameraCharacteristics(id)
            val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
            if (facing == targetFacing) {
                selectedCameraId = id
                break
            }
        }

        if (selectedCameraId == null) {
            Log.e(tag, "Camera not found for target facing: $targetFacing")
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }

        val handlerThread = HandlerThread("CameraBackground").apply { start() }
        val backgroundHandler = Handler(handlerThread.looper)

        // Capture resolution 1280x720 (ringan, cepat upload)
        val imageReader = ImageReader.newInstance(1280, 720, ImageFormat.JPEG, 2)
        var cameraDevice: CameraDevice? = null
        var captureSession: CameraCaptureSession? = null
        val isFinished = AtomicBoolean(false)

        fun cleanup() {
            try {
                captureSession?.close()
                cameraDevice?.close()
                imageReader.close()
                handlerThread.quitSafely()
            } catch (e: Exception) {
                Log.e(tag, "Error closing camera resources", e)
            }
        }

        // Timeout 8 detik agar tidak hang jika kamera gagal
        backgroundHandler.postDelayed({
            if (isFinished.compareAndSet(false, true)) {
                Log.e(tag, "Camera capture timeout")
                cleanup()
                continuation.resume(null)
            }
        }, 8000)

        imageReader.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage()
            if (image != null) {
                try {
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    if (isFinished.compareAndSet(false, true)) {
                        cleanup()
                        continuation.resume(base64)
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Failed to read image buffer", e)
                    if (isFinished.compareAndSet(false, true)) {
                        cleanup()
                        continuation.resume(null)
                    }
                } finally {
                    image.close()
                }
            }
        }, backgroundHandler)

        try {
            cameraManager.openCamera(selectedCameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    try {
                        val surface = imageReader.surface
                        val surfaces = listOf(surface)

                        camera.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(session: CameraCaptureSession) {
                                captureSession = session
                                try {
                                    val captureBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                                        addTarget(surface)
                                        set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                                        set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                                    }
                                    session.capture(captureBuilder.build(), null, backgroundHandler)
                                } catch (e: Exception) {
                                    Log.e(tag, "Failed to trigger still capture", e)
                                    if (isFinished.compareAndSet(false, true)) {
                                        cleanup()
                                        continuation.resume(null)
                                    }
                                }
                            }

                            override fun onConfigureFailed(session: CameraCaptureSession) {
                                Log.e(tag, "Failed to configure capture session")
                                if (isFinished.compareAndSet(false, true)) {
                                    cleanup()
                                    continuation.resume(null)
                                }
                            }
                        }, backgroundHandler)

                    } catch (e: Exception) {
                        Log.e(tag, "Exception during session creation", e)
                        if (isFinished.compareAndSet(false, true)) {
                            cleanup()
                            continuation.resume(null)
                        }
                    }
                }

                override fun onDisconnected(camera: CameraDevice) {
                    Log.w(tag, "Camera disconnected")
                    if (isFinished.compareAndSet(false, true)) {
                        cleanup()
                        continuation.resume(null)
                    }
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    Log.e(tag, "Camera error code: $error")
                    if (isFinished.compareAndSet(false, true)) {
                        cleanup()
                        continuation.resume(null)
                    }
                }
            }, backgroundHandler)

        } catch (e: Exception) {
            Log.e(tag, "Failed to open camera", e)
            if (isFinished.compareAndSet(false, true)) {
                cleanup()
                continuation.resume(null)
            }
        }
    }
}
