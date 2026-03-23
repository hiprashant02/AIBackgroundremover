package com.remover.background.AI.ml

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Background removal processor using ML Kit Subject Segmentation.
 * Uses foregroundBitmap directly from MLKit for best results.
 * 
 * Recommended: Image should be at least 512x512 pixels for best quality.
 */
class BackgroundRemovalProcessor {

    private val options = SubjectSegmenterOptions.Builder()
        .enableForegroundBitmap()
        .build()

    private val segmenter = SubjectSegmentation.getClient(options)

    /**
     * Removes background using ML Kit Subject Segmentation.
     * Returns the foreground bitmap with transparent background.
     */
    suspend fun removeBackground(bitmap: Bitmap): Result<Bitmap> = withContext(Dispatchers.Default) {
        return@withContext try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val result = segmenter.process(inputImage).await()

            val foreground = result.foregroundBitmap

            if (foreground != null) {
                Result.success(foreground)
            } else {
                Result.failure(Exception("Could not generate foreground bitmap"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
