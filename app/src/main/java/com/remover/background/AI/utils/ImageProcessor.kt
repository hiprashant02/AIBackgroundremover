package com.remover.background.AI.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import com.remover.background.AI.model.BackgroundType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ImageProcessor {

    companion object {
        private const val MAX_IMAGE_DIMENSION = 2048
    }

    suspend fun resizeIfNeeded(bitmap: Bitmap): Bitmap = withContext(Dispatchers.Default) {
        val width = bitmap.width
        val height = bitmap.height

        if (width <= MAX_IMAGE_DIMENSION && height <= MAX_IMAGE_DIMENSION) {
            return@withContext bitmap
        }

        val ratio = Math.min(
            MAX_IMAGE_DIMENSION.toFloat() / width,
            MAX_IMAGE_DIMENSION.toFloat() / height
        )

        val newWidth = (width * ratio).toInt()
        val newHeight = (height * ratio).toInt()

        Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    suspend fun applyMaskToBitmap(
        originalBitmap: Bitmap,
        maskBitmap: Bitmap,
        background: BackgroundType
    ): Bitmap = withContext(Dispatchers.Default) {
        val width = originalBitmap.width
        val height = originalBitmap.height

        // Create output bitmap
        val outputBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(outputBitmap)

        // Draw background first
        when (background) {
            is BackgroundType.Transparent -> {
                // Transparent background - do nothing
            }
            is BackgroundType.SolidColor -> {
                canvas.drawColor(background.color.toArgb())
            }
            is BackgroundType.Gradient -> {
                drawGradientBackground(canvas, width, height, background)
            }
            is BackgroundType.Blur -> {
                drawBlurredBackground(canvas, originalBitmap, background.intensity)
            }
            is BackgroundType.Original -> {
                canvas.drawBitmap(originalBitmap, 0f, 0f, null)
                return@withContext outputBitmap
            }
        }

        // Apply mask to extract subject
        val subject = extractSubject(originalBitmap, maskBitmap)
        canvas.drawBitmap(subject, 0f, 0f, null)

        subject.recycle()

        outputBitmap
    }

    private fun extractSubject(originalBitmap: Bitmap, maskBitmap: Bitmap): Bitmap {
        val width = originalBitmap.width
        val height = originalBitmap.height

        val scaledMask = if (maskBitmap.width != width || maskBitmap.height != height) {
            Bitmap.createScaledBitmap(maskBitmap, width, height, true)
        } else {
            maskBitmap
        }

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val originalPixels = IntArray(width * height)
        val maskPixels = IntArray(width * height)

        originalBitmap.getPixels(originalPixels, 0, width, 0, 0, width, height)
        scaledMask.getPixels(maskPixels, 0, width, 0, 0, width, height)

        val resultPixels = IntArray(width * height)

        for (i in originalPixels.indices) {
            val alpha = Color.alpha(maskPixels[i])
            val originalColor = originalPixels[i]

            // Apply mask alpha to original pixel
            resultPixels[i] = Color.argb(
                alpha,
                Color.red(originalColor),
                Color.green(originalColor),
                Color.blue(originalColor)
            )
        }

        result.setPixels(resultPixels, 0, width, 0, 0, width, height)

        if (scaledMask != maskBitmap) {
            scaledMask.recycle()
        }

        return result
    }

    private fun drawGradientBackground(
        canvas: Canvas,
        width: Int,
        height: Int,
        gradient: BackgroundType.Gradient
    ) {
        val paint = Paint().apply {
            shader = LinearGradient(
                0f, 0f,
                width * Math.cos(Math.toRadians(gradient.angle.toDouble())).toFloat(),
                height * Math.sin(Math.toRadians(gradient.angle.toDouble())).toFloat(),
                gradient.startColor.toArgb(),
                gradient.endColor.toArgb(),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }

    private fun drawBlurredBackground(canvas: Canvas, originalBitmap: Bitmap, intensity: Float) {
        // Create a blurred version of the original
        val blurred = fastBlur(originalBitmap, intensity.toInt().coerceIn(1, 25))
        canvas.drawBitmap(blurred, 0f, 0f, null)
        blurred.recycle()
    }

    private fun fastBlur(bitmap: Bitmap, radius: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        val blurred = bitmap.copy(Bitmap.Config.ARGB_8888, true)

        val pixels = IntArray(width * height)
        blurred.getPixels(pixels, 0, width, 0, 0, width, height)

        // Simple box blur
        val tempPixels = pixels.clone()

        // Horizontal pass
        for (y in 0 until height) {
            for (x in 0 until width) {
                var r = 0
                var g = 0
                var b = 0
                var count = 0

                for (dx in -radius..radius) {
                    val nx = (x + dx).coerceIn(0, width - 1)
                    val pixel = pixels[y * width + nx]
                    r += Color.red(pixel)
                    g += Color.green(pixel)
                    b += Color.blue(pixel)
                    count++
                }

                tempPixels[y * width + x] = Color.rgb(r / count, g / count, b / count)
            }
        }

        // Vertical pass
        for (x in 0 until width) {
            for (y in 0 until height) {
                var r = 0
                var g = 0
                var b = 0
                var count = 0

                for (dy in -radius..radius) {
                    val ny = (y + dy).coerceIn(0, height - 1)
                    val pixel = tempPixels[ny * width + x]
                    r += Color.red(pixel)
                    g += Color.green(pixel)
                    b += Color.blue(pixel)
                    count++
                }

                pixels[y * width + x] = Color.rgb(r / count, g / count, b / count)
            }
        }

        blurred.setPixels(pixels, 0, width, 0, 0, width, height)
        return blurred
    }

    private fun androidx.compose.ui.graphics.Color.toArgb(): Int {
        return android.graphics.Color.argb(
            (this.alpha * 255).toInt(),
            (this.red * 255).toInt(),
            (this.green * 255).toInt(),
            (this.blue * 255).toInt()
        )
    }
}

