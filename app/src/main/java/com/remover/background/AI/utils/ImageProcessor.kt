package com.remover.background.AI.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import com.remover.background.AI.model.BackgroundType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

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

    /**
     * Apply mask-based approach (alternative method)
     */
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
            is BackgroundType.CustomImage -> {
                val scaledBg = Bitmap.createScaledBitmap(background.bitmap, width, height, true)
                canvas.drawBitmap(scaledBg, 0f, 0f, null)
                if (scaledBg != background.bitmap) {
                    scaledBg.recycle()
                }
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

    private fun drawCheckerboard(canvas: Canvas, width: Int, height: Int) {
        val squareSize = 40 // 40 pixels for checkerboard squares
        val lightColor = Color.rgb(42, 42, 42) // 0xFF2A2A2A
        val darkColor = Color.rgb(26, 26, 26)  // 0xFF1A1A1A
        
        val paint = Paint()
        
        val numCols = (width / squareSize) + 1
        val numRows = (height / squareSize) + 1
        
        for (row in 0..numRows) {
            for (col in 0..numCols) {
                val isLightSquare = (row + col) % 2 == 0
                paint.color = if (isLightSquare) lightColor else darkColor
                
                canvas.drawRect(
                    (col * squareSize).toFloat(),
                    (row * squareSize).toFloat(),
                    ((col + 1) * squareSize).toFloat(),
                    ((row + 1) * squareSize).toFloat(),
                    paint
                )
            }
        }
    }


    private fun drawBlurredBackground(canvas: Canvas, originalBitmap: Bitmap, intensity: Float) {
        // Create a blurred version of the original
        val blurred = fastBlur(originalBitmap, intensity.toInt().coerceIn(1, 25))
        canvas.drawBitmap(blurred, 0f, 0f, null)
        blurred.recycle()
    }

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

    /**
     * Save bitmap to file with best quality settings
     * Uses PNG for transparency and JPEG for opaque images
     */
    suspend fun saveBitmapToFile(
        bitmap: Bitmap,
        outputFile: File,
        hasTransparency: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            outputFile.parentFile?.mkdirs()

            outputFile.outputStream().use { out ->
                if (hasTransparency) {
                    // Use PNG for images with transparency (lossless)
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                } else {
                    // Use JPEG with maximum quality for opaque images
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Save bitmap to MediaStore with high quality
     * Automatically selects format based on background type
     */
    suspend fun saveBitmapHighQuality(
        bitmap: Bitmap,
        context: Context,
        fileName: String,
        backgroundType: BackgroundType
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val hasTransparency = backgroundType is BackgroundType.Transparent

            val format = if (hasTransparency) {
                Bitmap.CompressFormat.PNG
            } else {
                Bitmap.CompressFormat.JPEG
            }

            val extension = if (hasTransparency) "png" else "jpg"
            val mimeType = if (hasTransparency) "image/png" else "image/jpeg"

            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "$fileName.$extension")
                put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AIBackgroundRemover")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

            uri?.let {
                resolver.openOutputStream(it)?.use { out ->
                    // Compress with maximum quality (100 for both PNG and JPEG)
                    bitmap.compress(format, 100, out)
                    out.flush()
                }

                // Mark as not pending anymore
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(it, contentValues, null, null)

                it
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Save bitmap with automatic format detection and best quality
     */
    suspend fun saveImageWithBestQuality(
        bitmap: Bitmap,
        context: Context,
        fileName: String = "bg_removed_${System.currentTimeMillis()}",
        hasAlpha: Boolean = bitmap.hasAlpha()
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val format = if (hasAlpha) {
                Bitmap.CompressFormat.PNG
            } else {
                Bitmap.CompressFormat.JPEG
            }

            val extension = if (hasAlpha) "png" else "jpg"
            val mimeType = if (hasAlpha) "image/png" else "image/jpeg"

            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "$fileName.$extension")
                put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AIBackgroundRemover")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

            uri?.let {
                resolver.openOutputStream(it)?.use { out ->
                    bitmap.compress(format, 100, out)
                    out.flush()
                }

                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(it, contentValues, null, null)

                it
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

