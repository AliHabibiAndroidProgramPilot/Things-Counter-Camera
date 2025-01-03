package com.example.ali.thingscounter

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.ali.thingscounter.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var isCameraPermissionGranted = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }

    private fun requestCameraPermission(): Boolean {
        val permission: String = Manifest.permission.CAMERA
        val cameraPermission = ContextCompat.checkSelfPermission(this, permission)
        if (cameraPermission == PackageManager.PERMISSION_GRANTED)
            return true
        else {
            requestPermissionLauncher.launch(permission)
            return isCameraPermissionGranted
        }
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            isCameraPermissionGranted = when (isGranted) {
                true -> true
                false -> false
            }
        }
}