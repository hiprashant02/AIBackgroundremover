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

class ManualEditingProcessor {

    suspend fun applyBrushStrokes(
        originalMask: Bitmap,
        paths: List<DrawingPath>,
        imageWidth: Int,
        imageHeight: Int
    ): Bitmap = withContext(Dispatchers.Default) {
        val editedMask = originalMask.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(editedMask)
        paths.forEach { path -> applyPath(canvas, path, imageWidth, imageHeight) }
        return@withContext editedMask
    }

    private fun applyPath(canvas: Canvas, path: DrawingPath, w: Int, h: Int) {
        val points = path.points
        if (points.isEmpty()) return

        val brush = path.brushTool
        // Calculate the target alpha from the brush settings
        val targetAlpha = (brush.opacity * 255).toInt()

        val paint = Paint().apply {
            isAntiAlias = true
            isDither = true
            style = Paint.Style.FILL
            alpha = targetAlpha

            xfermode = when (brush.mode) {
                // DST_OUT erases existing pixels (Transparent)
                BrushMode.ERASE -> PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
                // SRC_OVER (null) draws White pixels (Opaque)
                BrushMode.RESTORE -> null
            }
        }

        // Pass targetAlpha to drawing functions so they can enforce it
        drawPoint(canvas, points[0], brush, paint, w, h, targetAlpha)
        for (i in 1 until points.size) {
            val p1 = points[i - 1]
            val p2 = points[i]
            drawSegment(canvas, p1, p2, brush, paint, w, h, targetAlpha)
        }
    }

    private fun drawPoint(
        canvas: Canvas,
        p: DrawingPoint,
        brush: BrushTool,
        paint: Paint,
        w: Int,
        h: Int,
        targetAlpha: Int
    ) {
        drawBrushCircle(canvas, p.x * w, p.y * h, brush.size * p.pressure, brush.hardness, paint, targetAlpha)
    }

    private fun drawSegment(
        canvas: Canvas,
        p1: DrawingPoint,
        p2: DrawingPoint,
        brush: BrushTool,
        paint: Paint,
        w: Int,
        h: Int,
        targetAlpha: Int
    ) {
        val startX = p1.x * w; val startY = p1.y * h
        val endX = p2.x * w; val endY = p2.y * h
        val distance = hypot(endX - startX, endY - startY)
        val brushSize = brush.size * p2.pressure

        // Step size: 10% of brush size ensures smoothness without killing performance
        val spacing = max(1f, brushSize * 0.1f)
        val steps = (distance / spacing).toInt()

        for (i in 1..steps) {
            val t = i.toFloat() / steps
            drawBrushCircle(
                canvas,
                startX + (endX - startX) * t,
                startY + (endY - startY) * t,
                brushSize, brush.hardness, paint, targetAlpha
            )
        }
    }

    private fun drawBrushCircle(
        canvas: Canvas,
        x: Float,
        y: Float,
        size: Float,
        hardness: Float,
        paint: Paint,
        targetAlpha: Int
    ) {
        val radius = size / 2

        if (hardness < 1.0f) {
            val centerStop = hardness.coerceIn(0.1f, 0.95f)
            paint.shader = RadialGradient(
                x, y, radius,
                intArrayOf(Color.WHITE, Color.WHITE, Color.TRANSPARENT),
                floatArrayOf(0f, centerStop, 1.0f),
                Shader.TileMode.CLAMP
            )
            // DO NOT set paint.color here, or it will reset alpha!
            // Just ensure alpha is set correctly.
            paint.alpha = targetAlpha
        } else {
            paint.shader = null
            paint.color = Color.WHITE // This resets alpha to 255
            paint.alpha = targetAlpha // FIX: Restore the correct alpha
        }

        canvas.drawCircle(x, y, radius, paint)
    }

    // --- Helpers ---
    suspend fun createMaskFromForeground(foregroundBitmap: Bitmap): Bitmap = withContext(Dispatchers.Default) {
        val width = foregroundBitmap.width; val height = foregroundBitmap.height
        val mask = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        foregroundBitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val maskPixels = IntArray(width * height)
        for (i in pixels.indices) maskPixels[i] = Color.argb(Color.alpha(pixels[i]), 255, 255, 255)
        mask.setPixels(maskPixels, 0, width, 0, 0, width, height)
        return@withContext mask
    }

    suspend fun applyEditedMask(originalBitmap: Bitmap, editedMask: Bitmap): Bitmap = withContext(Dispatchers.Default) {
        val width = originalBitmap.width; val height = originalBitmap.height
        val scaledMask = if (editedMask.width != width || editedMask.height != height)
            Bitmap.createScaledBitmap(editedMask, width, height, true) else editedMask
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val originalPixels = IntArray(width * height)
        val maskPixels = IntArray(width * height)
        originalBitmap.getPixels(originalPixels, 0, width, 0, 0, width, height)
        scaledMask.getPixels(maskPixels, 0, width, 0, 0, width, height)
        val resultPixels = IntArray(width * height)
        for (i in originalPixels.indices) {
            resultPixels[i] = Color.argb(Color.alpha(maskPixels[i]), Color.red(originalPixels[i]), Color.green(originalPixels[i]), Color.blue(originalPixels[i]))
        }
        result.setPixels(resultPixels, 0, width, 0, 0, width, height)
        if (scaledMask != editedMask) scaledMask.recycle()
        return@withContext result
    }

    suspend fun smoothMask(mask: Bitmap, radius: Int = 2): Bitmap = withContext(Dispatchers.Default) {
        val width = mask.width; val height = mask.height
        val smoothed = mask.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(width * height)
        smoothed.getPixels(pixels, 0, width, 0, 0, width, height)
        val tempPixels = pixels.clone()
        for (y in radius until height - radius) {
            for (x in radius until width - radius) {
                var alphaSum = 0; var count = 0
                for (dy in -radius..radius) {
                    for (dx in -radius..radius) {
                        alphaSum += Color.alpha(pixels[(y + dy) * width + (x + dx)])
                        count++
                    }
                }
                val p = pixels[y * width + x]
                tempPixels[y * width + x] = Color.argb(alphaSum / count, Color.red(p), Color.green(p), Color.blue(p))
            }
        }
        smoothed.setPixels(tempPixels, 0, width, 0, 0, width, height)
        return@withContext smoothed
    }

    fun calculateOptimalBrushSize(imageWidth: Int, imageHeight: Int): Float {
        val maxDimension = maxOf(imageWidth, imageHeight)
        return (maxDimension * 0.05f).coerceIn(20f, 100f)
    }
}