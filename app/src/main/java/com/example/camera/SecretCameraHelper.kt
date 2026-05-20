package com.example.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object SecretCameraHelper {
    private const val TAG = "SecretCameraHelper"

    @SuppressLint("MissingPermission")
    fun takeSecretSelfie(
        context: Context,
        onPhotoCaptured: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) 
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            onError("Camera permission not granted")
            return
        }

        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        var frontCameraId: String? = null
        try {
            for (id in cameraManager.cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    frontCameraId = id
                    break
                }
            }
        } catch (e: Exception) {
            onError("Failed to query cameras: ${e.message}")
            return
        }

        if (frontCameraId == null) {
            onError("No front camera found")
            return
        }

        val handlerThread = HandlerThread("SecretCameraThread").apply { start() }
        val backgroundHandler = Handler(handlerThread.looper)

        val imageReader = ImageReader.newInstance(640, 480, ImageFormat.JPEG, 2)
        var cameraDevice: CameraDevice? = null
        var captureSession: CameraCaptureSession? = null

        fun cleanup() {
            try {
                captureSession?.close()
                cameraDevice?.close()
                imageReader.close()
                handlerThread.quitSafely()
            } catch (e: Exception) {
                Log.e(TAG, "Error cleaning up", e)
            }
        }

        imageReader.setOnImageAvailableListener({ reader ->
            try {
                val image = reader.acquireLatestImage()
                if (image != null) {
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    image.close()

                    val targetDir = File(context.filesDir, "intruder_photos").apply { mkdirs() }
                    val photoFile = File(targetDir, "intruder_${UUID.randomUUID()}.jpg")
                    FileOutputStream(photoFile).use { fos ->
                        fos.write(bytes)
                    }
                    onPhotoCaptured(photoFile.absolutePath)
                } else {
                    onError("Image was null")
                }
            } catch (e: Exception) {
                onError("Failed to read captured photo: ${e.message}")
            } finally {
                cleanup()
            }
        }, backgroundHandler)

        try {
            cameraManager.openCamera(frontCameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    val surfaces = listOf(imageReader.surface)
                    
                    try {
                        camera.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(session: CameraCaptureSession) {
                                captureSession = session
                                try {
                                    val builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                                        addTarget(imageReader.surface)
                                        set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
                                        set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                                    }
                                    session.capture(builder.build(), null, backgroundHandler)
                                } catch (e: Exception) {
                                    onError("Failed to capture: ${e.message}")
                                    cleanup()
                                }
                            }

                            override fun onConfigureFailed(session: CameraCaptureSession) {
                                onError("Capture session configuration failed")
                                cleanup()
                            }
                        }, backgroundHandler)
                    } catch (e: Exception) {
                        onError("Failed to create capture session: ${e.message}")
                        cleanup()
                    }
                }

                override fun onDisconnected(camera: CameraDevice) {
                    onError("Camera disconnected")
                    cleanup()
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    onError("Camera error: $error")
                    cleanup()
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            onError("Failed to open camera: ${e.message}")
            cleanup()
        }
    }
}
