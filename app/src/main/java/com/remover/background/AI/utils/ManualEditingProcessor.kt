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
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Optimized processor for manual brush editing
 * FIX: Proper alpha handling, better performance, accurate brush application
 */
class ManualEditingProcessor {

    /**
     * Apply brush strokes to mask bitmap
     * OPTIMIZED: Reuses single canvas, proper paint configuration
     */
    suspend fun applyBrushStrokes(
        originalMask: Bitmap,
        paths: List<DrawingPath>,
        imageWidth: Int,
        imageHeight: Int
    ): Bitmap = withContext(Dispatchers.Default) {
        if (paths.isEmpty()) return@withContext originalMask

        val editedMask = originalMask.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(editedMask)

        paths.forEach { path ->
            applyPath(canvas, path, imageWidth, imageHeight)
        }

        return@withContext editedMask
    }

    /**
     * Apply single path with optimized rendering
     * FIX: Proper alpha for both ERASE and RESTORE modes
     */
    private fun applyPath(canvas: Canvas, path: DrawingPath, w: Int, h: Int) {
        val points = path.points
        if (points.isEmpty()) return

        val brush = path.brushTool
        val targetAlpha = (brush.opacity * 255).toInt()

        val paint = Paint().apply {
            isAntiAlias = true
            isDither = true
            style = Paint.Style.FILL
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND

            when (brush.mode) {
                BrushMode.ERASE -> {
                    // Erase mode: remove alpha from existing pixels
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
                    alpha = targetAlpha
                }
                BrushMode.RESTORE -> {
                    // Restore mode: add white pixels with alpha
                    // FIX: Use SRC_OVER to properly blend with existing content
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
                    color = Color.WHITE
                    alpha = targetAlpha
                }
            }
        }

        // Draw all points with interpolation
        drawPoint(canvas, points[0], brush, paint, w, h)
        for (i in 1 until points.size) {
            drawSegment(canvas, points[i - 1], points[i], brush, paint, w, h)
        }
    }

    /**
     * Draw single point
     */
    private fun drawPoint(
        canvas: Canvas,
        p: DrawingPoint,
        brush: BrushTool,
        paint: Paint,
        w: Int,
        h: Int
    ) {
        val x = p.x * w
        val y = p.y * h
        val size = brush.size * p.pressure
        drawBrushCircle(canvas, x, y, size, brush.hardness, brush.mode, paint)
    }

    /**
     * Draw interpolated segment between two points
     * OPTIMIZED: Adaptive spacing based on brush size
     */
    private fun drawSegment(
        canvas: Canvas,
        p1: DrawingPoint,
        p2: DrawingPoint,
        brush: BrushTool,
        paint: Paint,
        w: Int,
        h: Int
    ) {
        val startX = p1.x * w
        val startY = p1.y * h
        val endX = p2.x * w
        val endY = p2.y * h

        val distance = hypot(endX - startX, endY - startY)
        val brushSize = brush.size * max(p1.pressure, p2.pressure)

        // Adaptive spacing: smaller brushes need tighter spacing
        val spacing = max(1f, brushSize * 0.15f)
        val steps = max(1, (distance / spacing).toInt())

        for (i in 1..steps) {
            val t = i.toFloat() / steps
            val x = startX + (endX - startX) * t
            val y = startY + (endY - startY) * t
            val pressure = p1.pressure + (p2.pressure - p1.pressure) * t
            val size = brush.size * pressure

            drawBrushCircle(canvas, x, y, size, brush.hardness, brush.mode, paint)
        }
    }

    /**
     * Draw brush circle with proper shader and alpha handling
     * FIX: Correct alpha preservation for both modes
     */
    private fun drawBrushCircle(
        canvas: Canvas,
        x: Float,
        y: Float,
        size: Float,
        hardness: Float,
        mode: BrushMode,
        paint: Paint
    ) {
        val radius = size / 2

        if (hardness < 1.0f) {
            // Soft brush: use radial gradient
            val colorArray = when (mode) {
                BrushMode.ERASE -> {
                    // For erase, gradient from current alpha to transparent
                    intArrayOf(
                        Color.argb(paint.alpha, 255, 255, 255),
                        Color.TRANSPARENT
                    )
                }
                BrushMode.RESTORE -> {
                    // For restore, gradient from white to transparent
                    intArrayOf(
                        Color.argb(paint.alpha, 255, 255, 255),
                        Color.TRANSPARENT
                    )
                }
            }

            paint.shader = RadialGradient(
                x, y, radius,
                colorArray,
                floatArrayOf(hardness, 1.0f),
                Shader.TileMode.CLAMP
            )
        } else {
            // Hard brush: solid color
            paint.shader = null
        }

        canvas.drawCircle(x, y, radius, paint)
    }

    /**
     * Create mask from foreground bitmap by extracting alpha channel
     * OPTIMIZED: Direct pixel manipulation
     */
    suspend fun createMaskFromForeground(foregroundBitmap: Bitmap): Bitmap = withContext(Dispatchers.Default) {
        val width = foregroundBitmap.width
        val height = foregroundBitmap.height
        val mask = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val pixels = IntArray(width * height)
        foregroundBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val maskPixels = IntArray(width * height)
        for (i in pixels.indices) {
            val alpha = Color.alpha(pixels[i])
            maskPixels[i] = Color.argb(alpha, 255, 255, 255)
        }

        mask.setPixels(maskPixels, 0, width, 0, 0, width, height)
        return@withContext mask
    }

    /**
     * Apply edited mask to original bitmap
     * OPTIMIZED: Handles scaling and proper alpha blending
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
     * Smooth mask edges using box blur
     * OPTIMIZED: Only processes center area, preserves edges
     */
    suspend fun smoothMask(mask: Bitmap, radius: Int = 2): Bitmap = withContext(Dispatchers.Default) {
        val width = mask.width
        val height = mask.height
        val smoothed = mask.copy(Bitmap.Config.ARGB_8888, true)

        val pixels = IntArray(width * height)
        smoothed.getPixels(pixels, 0, width, 0, 0, width, height)

        val tempPixels = pixels.clone()

        // Only smooth alpha channel
        for (y in radius until height - radius) {
            for (x in radius until width - radius) {
                var alphaSum = 0
                var count = 0

                for (dy in -radius..radius) {
                    for (dx in -radius..radius) {
                        alphaSum += Color.alpha(pixels[(y + dy) * width + (x + dx)])
                        count++
                    }
                }

                val avgAlpha = alphaSum / count
                val p = pixels[y * width + x]
                tempPixels[y * width + x] = Color.argb(
                    avgAlpha,
                    Color.red(p),
                    Color.green(p),
                    Color.blue(p)
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
        val maxDimension = max(imageWidth, imageHeight)
        return (maxDimension * 0.05f).coerceIn(20f, 100f)
    }
}