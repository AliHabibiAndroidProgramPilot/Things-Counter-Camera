package com.example.ali.thingscounter

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.media.MediaActionSound
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.example.ali.thingscounter.databinding.ActivityMainBinding
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Objects
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var cameraInfo: CameraInfo
    private lateinit var cameraControl: CameraControl
    private val mediaActionSound = MediaActionSound()
    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        if (allPermissionGranted())
            startCamera()
        else
            requestPermissions()
        val scaleGestureDetector = ScaleGestureDetector(
            this,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    val currentZoomRatio: Float = cameraInfo.zoomState.value?.linearZoom ?: 0f
                    val newZoomRatio =
                        (currentZoomRatio + (detector.scaleFactor - 1)).coerceIn(0f, 1f)
                    cameraControl.setLinearZoom(newZoomRatio)
                    return true
                }
            })
        binding.viewFinder.setOnTouchListener { view, event ->
            scaleGestureDetector.onTouchEvent(event)
            if (event.action == MotionEvent.ACTION_UP)
                view.performClick()
            return@setOnTouchListener true
        }
        binding.imageCaptureButton.setOnClickListener { captureImage() }
        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun captureImage() {
        val imageCapture = imageCapture ?: return
        // Using TimeStamp As Unique Saving Methode For Display Name In MediaStore
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis())
        val contentValue = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Things Counter")
            }
        }
        val outputFile = ImageCapture.OutputFileOptions.Builder(
            contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValue
        ).build()

        imageCapture.takePicture(
            outputFile,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    outputFileResults.savedUri?.let { runObjectDetection(it) }
                    Log.i("CameraX", outputFileResults.savedUri.toString())
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.i("CameraX", exception.toString())
                }
            }).run {
            lifecycleScope.launch { withContext(Dispatchers.IO) { playShutterSound() } }
            binding.viewFlashOverlay.apply {
                visibility = View.VISIBLE
                animate()
                    .alpha(0f)
                    .setDuration(300)
                    .withEndAction {
                        visibility = View.GONE
                        alpha = 1f
                    }
            }
            binding.circle.apply {
                visibility = View.VISIBLE
                animate()
                    .alpha(0f)
                    .setDuration(400)
                    .withEndAction {
                        visibility = View.GONE
                        alpha = 1f
                    }
            }
        }
    }

    private fun runObjectDetection(filePath: Uri) {
        try {
            val inputImage = InputImage.fromFilePath(this, filePath)
            val options = ObjectDetectorOptions.Builder()
                .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
                .enableClassification()
                .build()
            ObjectDetection.getClient(options).process(inputImage)
                .addOnSuccessListener {
                    Log.i("MLKit_ODT", "Successful")
                }
                .addOnFailureListener {
                    Log.e("MLKit_ODT", it.message.toString())
                }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun startCamera() {
        val surfaceProvider = binding.viewFinder.surfaceProvider
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            // Build Camera Preview
            val preview = Preview.Builder()
                .build()
                .also { preview ->
                    preview.surfaceProvider = surfaceProvider
                }
            var currentFlashState = FlashState.OFF
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()
            val flashButton: ImageButton = binding.icFlash
            flashButton.setOnClickListener {
                currentFlashState = currentFlashState.next()
                flashButton.animate()
                    .alpha(0f)
                    .setDuration(100)
                    .withEndAction {
                        flashButton.setImageResource(currentFlashState.iconResId)
                        flashButton.animate()
                            .alpha(1f)
                            .setDuration(100)
                            .start()
                    }.start()
                imageCapture!!.flashMode = currentFlashState.flashMode
            }
            var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            binding.changeCamera.setOnClickListener {
                when (cameraSelector) {
                    CameraSelector.DEFAULT_BACK_CAMERA -> {
                        cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
                        it.animate().rotationY(180f).duration = 400
                    }

                    CameraSelector.DEFAULT_FRONT_CAMERA -> {
                        cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                        it.animate().rotationY(-180f).duration = 400
                    }

                    else -> {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    }
                }
                try {
                    cameraProvider.unbindAll()
                    val camera =
                        cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
                    cameraInfo = camera.cameraInfo
                    cameraControl = camera.cameraControl
                } catch (exception: Exception) {
                    Log.e("Use case binding failed", "${exception.cause} ${exception.message}")
                }
            }

            try {
                cameraProvider.unbindAll()
                val camera =
                    cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
                cameraInfo = camera.cameraInfo
                cameraControl = camera.cameraControl
            } catch (exception: Exception) {
                Log.e("Use case binding failed", "${exception.cause} ${exception.message}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun playShutterSound() {
        mediaActionSound.load(MediaActionSound.SHUTTER_CLICK)
        mediaActionSound.play(MediaActionSound.SHUTTER_CLICK)
    }

    private fun requestPermissions() {
        activityResultLauncher.launch(REQUIRED_PERMISSIONS)
    }

    private fun allPermissionGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext,
            it
        ) == PackageManager.PERMISSION_GRANTED
    }

    private val activityResultLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            var isPermissionGranted = true
            permissions.entries.forEach {
                if (it.key in REQUIRED_PERMISSIONS && !it.value)
                    isPermissionGranted = false
            }
            if (!isPermissionGranted) {
                val toastMessage = "Camera permission is disabled. Please allow it to continue."
                Toast.makeText(baseContext, toastMessage, Toast.LENGTH_LONG).show()
            } else
                startCamera()
        }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        mediaActionSound.release()
    }

    companion object {
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private val REQUIRED_PERMISSIONS: Array<String> =
            mutableListOf(Manifest.permission.CAMERA).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }
}