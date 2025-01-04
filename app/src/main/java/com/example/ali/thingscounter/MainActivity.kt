package com.example.ali.thingscounter

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
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
        val permission: String = Manifest.permission.CAMERA
        val cameraPermission =
            ContextCompat.checkSelfPermission(this, permission)
        if (cameraPermission == PackageManager.PERMISSION_DENIED) {
            requestPermissionLauncher.launch(permission)
        }
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            when (isGranted) {
                false -> {
                    val message = "App needs camera Permission to work properly"
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                }
                true -> isCameraPermissionGranted = true
            }
        }
}