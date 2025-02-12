package com.example.ali.thingscounter

import android.Manifest
import android.content.ContentResolver
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.RectF
import android.media.MediaActionSound
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.MediaStore.Images.Media
import android.util.Log
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
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
import com.example.ali.thingscounter.ml.SsdMobilenetV11Metadata1
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var cameraInfo: CameraInfo
    private lateinit var cameraControl: CameraControl
    private val mediaActionSound = MediaActionSound()
    private val paint = Paint()
    private lateinit var bitmapImage: Bitmap
    private lateinit var model: SsdMobilenetV11Metadata1
    private lateinit var labels: MutableList<String>
    private val imageProcessor = ImageProcessor.Builder()
        .add(ResizeOp(300, 300, ResizeOp.ResizeMethod.BILINEAR))
        .build()
    private val pickVisualMedia =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
            if (uri != null) {
                runObjectDetection(uri)
                Log.d("PHOTO_PICKER", "Selected URI: $uri")
            } else
                Log.d("PHOTO_PICKER", "No media selected")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val visualMediaPickerButton = binding.btnVisualMediaPicker
        visualMediaPickerButton.setImageURI(getLatestGalleryImage())
        visualMediaPickerButton.setOnClickListener {
            pickVisualMedia.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
        if (allPermissionGranted()) {
            model = SsdMobilenetV11Metadata1.newInstance(this)
            labels = FileUtil.loadLabels(this, "labels.txt")
            startCamera()
        } else
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
                put(Media.RELATIVE_PATH, "Pictures/Things Counter")
            }
        }
        val outputFile = ImageCapture.OutputFileOptions.Builder(
            contentResolver,
            Media.EXTERNAL_CONTENT_URI,
            contentValue
        ).build()

        imageCapture.takePicture(
            outputFile,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    Log.i("CameraX", outputFileResults.savedUri.toString())
                    outputFileResults.savedUri?.let { runObjectDetection(it) }
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

    private fun runObjectDetection(uri: Uri) {
        bitmapImage = getBitmapFromUri(this.contentResolver, uri) ?: Bitmap.createBitmap(
            1,
            1,
            Bitmap.Config.ARGB_8888
        )
        var image = TensorImage.fromBitmap(bitmapImage)
        image = imageProcessor.process(image)
        // TensorFlow Main Process On Image Occurs Here!
        val outputs = model.process(image)
        val locations = outputs.locationsAsTensorBuffer.floatArray
        val classes = outputs.classesAsTensorBuffer.floatArray
        val scores = outputs.scoresAsTensorBuffer.floatArray
        val numberOfDetections = outputs.numberOfDetectionsAsTensorBuffer.floatArray
        val height = bitmapImage.height
        val width = bitmapImage.width
        paint.textSize = 65f
        paint.strokeWidth = 10f
        // Draw Box On Detected Objects
        val canvas = Canvas(bitmapImage)
        var x: Int
        scores.forEachIndexed { index, fl ->
            x = index
            x *= 4
            if (fl > 0.3) {
                paint.style = Paint.Style.STROKE
                canvas.drawRect(
                    RectF(
                        locations[x + 1] * width,
                        locations[x] * height,
                        locations[x + 3] * width,
                        locations[x + 2] * height
                    ), paint
                )
                paint.style = Paint.Style.FILL
                canvas.drawText(
                    labels[classes[index].toInt()] + " " + fl.toString(),
                    locations[x + 1] * width,
                    locations[x] * height,
                    paint
                )
            }
        }
        /*val imageView = binding.resultImageView
        imageView.visibility = View.VISIBLE
        imageView.setImageBitmap(bitmapImage)*/
    }

    private fun getBitmapFromUri(contentResolver: ContentResolver, uri: Uri): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    // Canvas Requires Mutable Bitmap
                    decoder.isMutableRequired = true
                }
            } else {
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    // Need To Copy Bitmap In Order To Making A Mutable Bitmap For Canvas
                    BitmapFactory.decodeStream(inputStream)
                        .copy(Bitmap.Config.ARGB_8888, true)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun getLatestGalleryImage(): Uri? {
        val contentUri: Uri =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            else
                Media.EXTERNAL_CONTENT_URI
        contentResolver.query(
            contentUri,
            arrayOf(Media._ID),
            null,
            null,
            "${Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idColumn = cursor.getColumnIndex(Media._ID)
                return Uri.withAppendedPath(
                    Media.EXTERNAL_CONTENT_URI,
                    cursor.getLong(idColumn).toString()
                )
            }
        }
        return null
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
                val toastMessage = "Permissions are disabled. Please allow it to continue."
                Toast.makeText(baseContext, toastMessage, Toast.LENGTH_LONG).show()
            } else
                startCamera()
        }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        mediaActionSound.release()
        model.close()
    }

    companion object {
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private val REQUIRED_PERMISSIONS: Array<String> =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                mutableListOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ).apply {}.toTypedArray()
            } else {
                mutableListOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ).apply {}.toTypedArray()
            }
    }
}