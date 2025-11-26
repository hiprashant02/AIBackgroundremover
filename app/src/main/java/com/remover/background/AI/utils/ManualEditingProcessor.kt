package com.remover.background.AI.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import com.remover.background.AI.model.BrushMode
import com.remover.background.AI.model.BrushTool
import com.remover.background.AI.model.DrawingPath
import com.remover.background.AI.model.DrawingPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

/**
 * Processor for manual brush editing of masks
 */
class ManualEditingProcessor {

    /**
     * Apply brush strokes to the mask bitmap
     */
    suspend fun applyBrushStrokes(
        originalMask: Bitmap,
        paths: List<DrawingPath>,
        imageWidth: Int,
        imageHeight: Int
    ): Bitmap = withContext(Dispatchers.Default) {
        // Create a mutable copy of the mask
        val editedMask = originalMask.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(editedMask)

        paths.forEach { path ->
            applyPath(canvas, path, imageWidth, imageHeight)
        }

        return@withContext editedMask
    }

    /**
     * Apply a single drawing path to the canvas
     */
    private fun applyPath(
        canvas: Canvas,
        path: DrawingPath,
        imageWidth: Int,
        imageHeight: Int
    ) {
        val points = path.points
        if (points.isEmpty()) return

        val brush = path.brushTool

        // Create paint for brush
        val paint = Paint().apply {
            isAntiAlias = true
            isDither = true
            style = Paint.Style.FILL

            // Set blend mode based on brush mode
            xfermode = when (brush.mode) {
                BrushMode.ERASE -> PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
                BrushMode.RESTORE -> PorterDuffXfermode(PorterDuff.Mode.DST_OVER)
            }

            alpha = (brush.opacity * 255).toInt()
        }

        // Draw each segment of the path
        for (i in 0 until points.size) {
            val point = points[i]

            // Scale point coordinates to bitmap dimensions
            val scaledX = point.x * imageWidth
            val scaledY = point.y * imageHeight

            // Adjust brush size by pressure
            val effectiveSize = brush.size * point.pressure

            // Create gradient brush for soft edges
            val shader = if (brush.hardness < 1.0f) {
                RadialGradient(
                    scaledX, scaledY,
                    effectiveSize / 2,
                    intArrayOf(
                        Color.WHITE,
                        Color.TRANSPARENT
                    ),
                    floatArrayOf(
                        brush.hardness,
                        1.0f
                    ),
                    Shader.TileMode.CLAMP
                )
            } else {
                null
            }

            paint.shader = shader

            // Draw the brush stroke
            when (brush.mode) {
                BrushMode.ERASE -> {
                    // Erase (make transparent)
                    canvas.drawCircle(scaledX, scaledY, effectiveSize / 2, paint)
                }
                BrushMode.RESTORE -> {
                    // Restore (make opaque)
                    paint.color = Color.WHITE
                    canvas.drawCircle(scaledX, scaledY, effectiveSize / 2, paint)
                }
            }

            // Draw line between consecutive points for smooth stroke
            if (i > 0) {
                val prevPoint = points[i - 1]
                val prevX = prevPoint.x * imageWidth
                val prevY = prevPoint.y * imageHeight

                paint.strokeWidth = effectiveSize
                paint.style = Paint.Style.STROKE
                paint.strokeCap = Paint.Cap.ROUND

                canvas.drawLine(prevX, prevY, scaledX, scaledY, paint)

                paint.style = Paint.Style.FILL
            }
        }
    }

    /**
     * Create initial mask from foreground bitmap
     */
    suspend fun createMaskFromForeground(
        foregroundBitmap: Bitmap
    ): Bitmap = withContext(Dispatchers.Default) {
        val width = foregroundBitmap.width
        val height = foregroundBitmap.height

        val mask = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val pixels = IntArray(width * height)
        foregroundBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // Extract alpha channel as mask
        val maskPixels = IntArray(width * height)
        for (i in pixels.indices) {
            val alpha = Color.alpha(pixels[i])
            maskPixels[i] = Color.argb(alpha, 255, 255, 255)
        }

        mask.setPixels(maskPixels, 0, width, 0, 0, width, height)
        return@withContext mask
    }

    /**
     * Apply edited mask to original image
     */
    suspend fun applyEditedMask(
        originalBitmap: Bitmap,
        editedMask: Bitmap
    ): Bitmap = withContext(Dispatchers.Default) {
        val width = originalBitmap.width
        val height = originalBitmap.height

        val scaledMask = if (editedMask.width != width || editedMask.height != height) {
            Bitmap.createScaledBitmap(editedMask, width, height, true)
        } else {
            editedMask
        }

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val originalPixels = IntArray(width * height)
        val maskPixels = IntArray(width * height)

        originalBitmap.getPixels(originalPixels, 0, width, 0, 0, width, height)
        scaledMask.getPixels(maskPixels, 0, width, 0, 0, width, height)

        val resultPixels = IntArray(width * height)

        for (i in originalPixels.indices) {
            val maskAlpha = Color.alpha(maskPixels[i])
            val originalColor = originalPixels[i]

            // Apply mask alpha to original pixel
            resultPixels[i] = Color.argb(
                maskAlpha,
                Color.red(originalColor),
                Color.green(originalColor),
                Color.blue(originalColor)
            )
        }

        result.setPixels(resultPixels, 0, width, 0, 0, width, height)

        if (scaledMask != editedMask) {
            scaledMask.recycle()
        }

        return@withContext result
    }

    /**
     * Smooth the mask edges for better quality
     */
    suspend fun smoothMask(
        mask: Bitmap,
        radius: Int = 2
    ): Bitmap = withContext(Dispatchers.Default) {
        val width = mask.width
        val height = mask.height

        val smoothed = mask.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(width * height)
        smoothed.getPixels(pixels, 0, width, 0, 0, width, height)

        val tempPixels = pixels.clone()

        // Simple box blur on alpha channel
        for (y in radius until height - radius) {
            for (x in radius until width - radius) {
                var alphaSum = 0
                var count = 0

                for (dy in -radius..radius) {
                    for (dx in -radius..radius) {
                        val pixel = pixels[(y + dy) * width + (x + dx)]
                        alphaSum += Color.alpha(pixel)
                        count++
                    }
                }

                val avgAlpha = alphaSum / count
                val originalPixel = pixels[y * width + x]
                tempPixels[y * width + x] = Color.argb(
                    avgAlpha,
                    Color.red(originalPixel),
                    Color.green(originalPixel),
                    Color.blue(originalPixel)
                )
            }
        }

        smoothed.setPixels(tempPixels, 0, width, 0, 0, width, height)
        return@withContext smoothed
    }

    /**
     * Calculate optimal brush size based on image dimensions
     */
    fun calculateOptimalBrushSize(imageWidth: Int, imageHeight: Int): Float {
        val maxDimension = maxOf(imageWidth, imageHeight)
        return (maxDimension * 0.05f).coerceIn(20f, 100f)
    }
}

