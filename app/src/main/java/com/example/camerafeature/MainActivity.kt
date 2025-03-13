package com.example.camerafeature

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.media.Image
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Size
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import coil.compose.rememberAsyncImagePainter
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private lateinit var cameraManager: CameraManager
    private lateinit var cameraDevice: CameraDevice
    private lateinit var captureRequestBuilder: CaptureRequest.Builder
    private lateinit var cameraCaptureSession: CameraCaptureSession
    private lateinit var imageReader: ImageReader
    private var cameraId: String = ""

    private var currentFilter = 0 // Filter ID (0 = none, 1 = grayscale, 2 = sepia, 3 = invert)
    private var capturedBitmap by mutableStateOf<Bitmap?>(null)
    private var cameraReady by mutableStateOf(false) // Camera ready state
    private var isImageCaptured by mutableStateOf(false) // To toggle between camera feed and captured image

    // Permission launcher
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            cameraReady = true
        } else {
            Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CaptureImageFromCamera()
        }
    }

    @Composable
    fun CaptureImageFromCamera() {
        val context = this

        // Explicitly check if the camera permission is granted
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            cameraReady = true
        } else {
            // If not, request the permission
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }

        // Initialize Camera if ready
        if (cameraReady) {
            AndroidView(
                factory = {
                    val frameLayout = FrameLayout(context).apply {
                        val textureView = TextureView(context).apply {
                            surfaceTextureListener = createSurfaceTextureListener(context)
                        }
                        addView(textureView)

                        // Create an ImageView to display the captured image over the TextureView
                        val imageView = ImageView(context).apply {
                            visibility = if (isImageCaptured) View.VISIBLE else View.GONE
                            capturedBitmap?.let {
                                setImageBitmap(it) // Update the ImageView with the captured bitmap
                            }
                            layoutParams = FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.MATCH_PARENT
                            )
                        }
                        addView(imageView) // Add the ImageView on top of the TextureView
                    }
                    frameLayout
                },
                modifier = Modifier.fillMaxSize().padding(16.dp)
            )
        } else {
            // Display a message when the camera is not ready
            Toast.makeText(context, "Waiting for permission to access the camera", Toast.LENGTH_SHORT).show()
        }

        Column(
            Modifier.fillMaxSize().padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            // Capture Image Button
            Button(onClick = {
                if (cameraReady && !isImageCaptured) {
                    captureImage()
                } else if (isImageCaptured) {
                    // Release camera resources and reset state
                    releaseCamera()
                    isImageCaptured = false
                } else {
                    Toast.makeText(context, "Camera not ready", Toast.LENGTH_SHORT).show()
                }
            }) {
                Text(text = if (isImageCaptured) "Back to Camera Feed" else "Capture Image")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Show the captured image with selected filter or show camera feed
            if (isImageCaptured && capturedBitmap != null) {
                val filteredBitmap = applyImageFilter(capturedBitmap) // Apply the selected filter
                Image(painter = rememberAsyncImagePainter(filteredBitmap), contentDescription = null)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Filter Buttons
            if (isImageCaptured) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(onClick = {
                        currentFilter = 1
                        capturedBitmap = applyImageFilter(capturedBitmap) // Apply grayscale filter
                    }) { Text(text = "Grayscale") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        currentFilter = 2
                        capturedBitmap = applyImageFilter(capturedBitmap) // Apply sepia filter
                    }) { Text(text = "Sepia") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        currentFilter = 3
                        capturedBitmap = applyImageFilter(capturedBitmap) // Apply invert filter
                    }) { Text(text = "Invert") }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Save Image Button
                Button(onClick = {
                    capturedBitmap?.let { saveImageToGallery(it) }
                }) {
                    Text(text = "Save Image")
                }
            }
        }
    }

    private fun applyImageFilter(bitmap: Bitmap?): Bitmap? {
        if (bitmap == null) return null

        val width = bitmap.width
        val height = bitmap.height
        val filteredBitmap = Bitmap.createBitmap(width, height, bitmap.config!!)

        val paint = Paint()

        when (currentFilter) {
            1 -> { // Grayscale
                val colorMatrix = ColorMatrix().apply { setSaturation(0f) }
                paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
            }

            2 -> { // Sepia
                val colorMatrix = ColorMatrix().apply { setScale(1f, 0.8f, 0.5f, 1f) }
                paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
            }

            3 -> { // Invert colors
                val colorMatrix = ColorMatrix(
                    floatArrayOf(
                        -1f, 0f, 0f, 0f, 255f,
                        0f, -1f, 0f, 0f, 255f,
                        0f, 0f, -1f, 0f, 255f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
            }

            else -> return bitmap // No filter
        }

        Canvas(filteredBitmap).drawBitmap(bitmap, 0f, 0f, paint)
        return filteredBitmap
    }

    private fun processImage(image: Image) {
        val planes = image.planes
        val buffer: ByteBuffer = planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        // Convert byte array to Bitmap
        capturedBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

        // Resize captured image to match ImageView and TextureView size
        capturedBitmap = capturedBitmap?.let { resizeBitmapToMatchView(it) }

        // Trigger UI update
        isImageCaptured = true // Update the flag to toggle between camera feed and captured image
    }

    private fun resizeBitmapToMatchView(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        // Resizing the captured bitmap to fit the same size as ImageView and TextureView
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, width, height, false)
        return resizedBitmap
    }

    private fun createSurfaceTextureListener(context: Context): TextureView.SurfaceTextureListener {
        return object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                // Only initialize the camera if permission is granted
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(context, "Camera permission not granted", Toast.LENGTH_SHORT).show()
                    return
                }
                initializeCamera(texture, width, height, context)
            }

            override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {}
            override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean = false
            override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {}
        }
    }

    private fun initializeCamera(texture: SurfaceTexture, width: Int, height: Int, context: Context) {
        cameraManager = context.getSystemService(CAMERA_SERVICE) as CameraManager
        cameraId = cameraManager.cameraIdList[0]
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        val previewSize = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?.getOutputSizes(SurfaceTexture::class.java)?.get(0) ?: Size(width, height)

        texture.setDefaultBufferSize(previewSize.width, previewSize.height)
        val previewSurface = Surface(texture)

        // Create ImageReader for capturing still images
        imageReader = ImageReader.newInstance(previewSize.width, previewSize.height, android.graphics.ImageFormat.JPEG, 1)

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            // Camera permission not granted, return
            return
        }
        cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                cameraDevice = camera
                captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                    addTarget(previewSurface)
                }

                // Create capture session with preview surface
                cameraDevice.createCaptureSession(listOf(previewSurface, imageReader.surface), object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        cameraCaptureSession = session
                        updateCameraPreview()
                        cameraReady = true // Camera is ready for capturing
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Toast.makeText(context, "Failed to configure camera", Toast.LENGTH_SHORT).show()
                    }
                }, null)
            }

            override fun onDisconnected(camera: CameraDevice) {
                camera.close()
            }

            override fun onError(camera: CameraDevice, error: Int) {
                camera.close()
                Toast.makeText(context, "Error opening camera", Toast.LENGTH_SHORT).show()
            }
        }, null)
    }

    private fun updateCameraPreview() {
        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
        cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, Handler(Looper.getMainLooper()))
    }

    private fun captureImage() {
        // Capture image logic
        val captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
        captureRequestBuilder.addTarget(imageReader.surface)

        // Set capture time and orientation
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        val fileName = "IMG_" + dateFormat.format(Date()) + ".jpg"

        val captureCallback = object : CameraCaptureSession.CaptureCallback() {
            override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                // Handle captured image
                imageReader.acquireLatestImage()?.let { processImage(it) }
            }
        }

        cameraCaptureSession.capture(captureRequestBuilder.build(), captureCallback, Handler(Looper.getMainLooper()))
    }

    private fun releaseCamera() {
        cameraCaptureSession.close()
        cameraDevice.close()
        imageReader.close()
        cameraReady = false
    }

    private fun saveImageToGallery(bitmap: Bitmap) {
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "IMG_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        }

        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        uri?.let { uri ->
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                Toast.makeText(this, "Image saved to gallery", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
