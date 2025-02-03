package com.example.ali.thingscounter

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.ali.thingscounter.databinding.ActivityCountingResultBinding

class CountingResultActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCountingResultBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCountingResultBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }
}