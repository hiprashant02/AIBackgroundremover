package com.remover.background.AI.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import com.remover.background.AI.model.BackgroundType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Image processing utilities for composing final images.
 */
class ImageProcessor {

    companion object {
        // 4K resolution limit - maintains quality while preventing Canvas crashes
        private const val MAX_IMAGE_DIMENSION = 4096
    }

    /**
     * Resize bitmap if it exceeds maximum dimensions
     */
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

    /**
     * Compose final image using ML Kit's direct foreground bitmap (RECOMMENDED)
     * This produces the best quality results with enhanced rendering
     */
    suspend fun composeFinalImage(
        foreground: Bitmap,
        background: BackgroundType,
        originalWidth: Int,
        originalHeight: Int,
        originalBitmap: Bitmap? = null,
        subjectX: Float = 0f,
        subjectY: Float = 0f,
        subjectScale: Float = 1f,
        subjectRotation: Float = 0f,
        precomputedBlurredBitmap: Bitmap? = null
    ): Bitmap = withContext(Dispatchers.Default) {
        val result = Bitmap.createBitmap(originalWidth, originalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // 1. Draw the selected background
        when (background) {
            is BackgroundType.Transparent -> {
                // Keep transparent (do not draw checkerboard)
            }
            is BackgroundType.SolidColor -> {
                canvas.drawColor(background.color.toArgb())
            }
            is BackgroundType.Gradient -> {
                drawGradientBackground(canvas, originalWidth, originalHeight, background)
            }
            is BackgroundType.Blur -> {
                if (precomputedBlurredBitmap != null) {
                    canvas.drawBitmap(precomputedBlurredBitmap, 0f, 0f, paint)
                } else if (originalBitmap != null) {
                    drawBlurredBackground(canvas, originalBitmap, background.intensity)
                }
            }
            is BackgroundType.Original -> {
                if (originalBitmap != null) {
                    canvas.drawBitmap(originalBitmap, 0f, 0f, paint)
                }
            }
            is BackgroundType.CustomImage -> {
                // Crop logic: Center Crop to fill canvas
                val bgWidth = background.bitmap.width
                val bgHeight = background.bitmap.height
                val canvasRatio = originalWidth.toFloat() / originalHeight
                val bgRatio = bgWidth.toFloat() / bgHeight
                
                val (drawW, drawH) = if (bgRatio > canvasRatio) {
                    // Background is wider than canvas: Fit Height, Crop Width
                    (originalHeight * bgRatio) to originalHeight.toFloat()
                } else {
                    // Background is taller than canvas: Fit Width, Crop Height
                    originalWidth.toFloat() to (originalWidth / bgRatio)
                }
                
                val left = (originalWidth - drawW) / 2f
                val top = (originalHeight - drawH) / 2f
                
                val destRect = RectF(left, top, left + drawW, top + drawH)
                canvas.drawBitmap(background.bitmap, null, destRect, paint)
            }
        }

        // 2. Draw the foreground with transform
        val matrix = android.graphics.Matrix()
        val cx = foreground.width / 2f
        val cy = foreground.height / 2f
        
        // Scale and Rotate around the center of the image
        matrix.postScale(subjectScale, subjectScale, cx, cy)
        matrix.postRotate(subjectRotation, cx, cy)
        matrix.postTranslate(subjectX, subjectY)
        
        canvas.drawBitmap(foreground, matrix, paint)

        return@withContext result
    }

    private fun drawGradientBackground(
        canvas: Canvas,
        width: Int,
        height: Int,
        gradient: BackgroundType.Gradient
    ) {
        val angleRad = Math.toRadians(gradient.angle.toDouble())
        val cx = width / 2f
        val cy = height / 2f
        val length = Math.sqrt((width * width + height * height).toDouble()).toFloat()
        
        // Calculate start and end points based on center and angle
        val dx = (Math.cos(angleRad) * length / 2).toFloat()
        val dy = (Math.sin(angleRad) * length / 2).toFloat()
        
        val paint = Paint().apply {
            shader = LinearGradient(
                cx - dx, cy - dy,
                cx + dx, cy + dy,
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

    /**
     * Fast box blur implementation for background blur effect
     */
    fun fastBlur(bitmap: Bitmap, radius: Int): Bitmap {
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
