package com.example.ali.thingscounter

import androidx.camera.core.ImageCapture

enum class FlashState(val iconResId: Int, val flashMode: Int) {
    OFF(R.drawable.ic_flash_off, ImageCapture.FLASH_MODE_OFF),
    AUTO(R.drawable.ic_flash_auto, ImageCapture.FLASH_MODE_AUTO),
    ON(R.drawable.ic_flash_on, ImageCapture.FLASH_MODE_ON);

    fun next(): FlashState = entries[(ordinal + 1) % entries.size]
}