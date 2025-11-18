package com.remover.background.AI.model

import androidx.compose.ui.graphics.Color

sealed class BackgroundType {
    object Transparent : BackgroundType()
    data class SolidColor(val color: Color) : BackgroundType()
    data class Gradient(
        val startColor: Color,
        val endColor: Color,
        val angle: Float = 0f
    ) : BackgroundType()
    data class Blur(val intensity: Float = 25f) : BackgroundType()
    object Original : BackgroundType()
}

data class ProcessedImage(
    val originalBitmap: android.graphics.Bitmap,
    val processedBitmap: android.graphics.Bitmap,
    val maskBitmap: android.graphics.Bitmap?,
    val backgroundType: BackgroundType
)

