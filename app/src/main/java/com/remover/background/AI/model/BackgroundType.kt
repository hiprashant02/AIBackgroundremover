package com.remover.background.AI.model

import android.net.Uri
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
    // Store both display bitmap and original URI for full-res export
    data class CustomImage(
        val bitmap: android.graphics.Bitmap,
        val originalUri: Uri? = null
    ) : BackgroundType()
}
