package com.remover.background.AI.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer

class BackgroundRemovalProcessor(context: Context) {

    private val options = SubjectSegmenterOptions.Builder()
        .enableForegroundBitmap()
        .enableForegroundConfidenceMask()
        .build()

    private val segmenter = SubjectSegmentation.getClient(options)

    suspend fun removeBackground(bitmap: Bitmap): Result<Bitmap> = withContext(Dispatchers.Default) {
        return@withContext try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)

            val result = segmenter.process(inputImage).await()

            val foregroundBitmap = result.foregroundBitmap

            if (foregroundBitmap != null) {
                Result.success(foregroundBitmap)
            } else {
                Result.failure(Exception("Failed to extract foreground"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getMask(bitmap: Bitmap): Result<Bitmap> = withContext(Dispatchers.Default) {
        return@withContext try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)

            val result = segmenter.process(inputImage).await()

            val confidenceMask = result.foregroundConfidenceMask

            if (confidenceMask != null) {
                // Convert confidence mask to bitmap
                val maskBitmap = confidenceMaskToBitmap(confidenceMask, bitmap.width, bitmap.height)
                Result.success(maskBitmap)
            } else {
                Result.failure(Exception("Failed to get confidence mask"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun confidenceMaskToBitmap(mask: FloatBuffer, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)

        mask.rewind()
        for (i in 0 until width * height) {
            val confidence = mask.get()
            val alpha = (confidence * 255).toInt().coerceIn(0, 255)
            pixels[i] = android.graphics.Color.argb(alpha, 255, 255, 255)
        }

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    fun close() {
        segmenter.close()
    }
}

