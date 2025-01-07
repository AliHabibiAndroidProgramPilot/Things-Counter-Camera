package com.example.ali.thingscounter

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.ali.thingscounter.databinding.ActivityMainBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
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

        binding.imageCaptureButton.setOnClickListener { captureImage() }
        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun captureImage() {}

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
            // Set Default Camera To Back Camera
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            try {
                // Make Sure That Nothing Is Bound To The cameraProvider
                cameraProvider.unbindAll()
                // Bind cameraSelector and preview To The cameraProvider
                cameraProvider.bindToLifecycle(this, cameraSelector, preview)
            } catch (exception: Exception) {
                Log.e("Use case binding failed", exception.toString())
            }
        }, ContextCompat.getMainExecutor(this))
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
    }

    companion object {
        // TODO("remember to delete this tag if it is unnecessary")
        private const val TAG = "CameraXApp"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private val REQUIRED_PERMISSIONS: Array<String> =
            mutableListOf(Manifest.permission.CAMERA).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }
}