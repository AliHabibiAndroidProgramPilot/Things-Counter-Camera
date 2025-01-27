package com.example.ali.thingscounter

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.ali.thingscounter.databinding.ActivityCountingResultBinding

class CountingResultActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCountingResultBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCountingResultBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }
}