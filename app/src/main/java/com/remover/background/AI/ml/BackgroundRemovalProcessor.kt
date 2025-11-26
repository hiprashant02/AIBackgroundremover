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
import kotlin.math.pow

class BackgroundRemovalProcessor(context: Context) {

    private val options = SubjectSegmenterOptions.Builder()
        .enableForegroundBitmap()
        .enableForegroundConfidenceMask()
        .build()

    private val segmenter = SubjectSegmentation.getClient(options)

    /**
     * Removes background using ML Kit's foreground bitmap (RECOMMENDED)
     * This is the most accurate method as ML Kit does the heavy lifting
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

    /**
     * Gets a refined mask with improved edge quality
     * Multiple quality levels available
     */
    suspend fun getMask(
        bitmap: Bitmap,
        quality: MaskQuality = MaskQuality.BALANCED
    ): Result<Bitmap> = withContext(Dispatchers.Default) {
        return@withContext try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val result = segmenter.process(inputImage).await()

            val confidenceMask = result.foregroundConfidenceMask

            if (confidenceMask != null) {
                val maskBitmap = when (quality) {
                    MaskQuality.HIGH_PRECISION -> createHighPrecisionMask(confidenceMask, bitmap.width, bitmap.height)
                    MaskQuality.BALANCED -> createBalancedMask(confidenceMask, bitmap.width, bitmap.height)
                    MaskQuality.SOFT_EDGES -> createSoftEdgeMask(confidenceMask, bitmap.width, bitmap.height)
                    MaskQuality.AGGRESSIVE -> createAggressiveMask(confidenceMask, bitmap.width, bitmap.height)
                }
                Result.success(maskBitmap)
            } else {
                Result.failure(Exception("Failed to get confidence mask"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * HIGH PRECISION: Keeps more of the subject, less aggressive removal
     * Best for: Portraits, detailed subjects, keeping body parts
     * Threshold: 0.3 (30% confidence)
     */
    private fun createHighPrecisionMask(mask: FloatBuffer, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)

        mask.rewind()
        for (i in 0 until width * height) {
            val confidence = mask.get()

            // Lower threshold = keep more pixels
            val alpha = if (confidence > 0.3f) {
                // Smooth transition from 0.3 to 1.0
                val normalized = (confidence - 0.3f) / 0.7f
                (normalized * 255).toInt().coerceIn(0, 255)
            } else {
                0
            }

            pixels[i] = Color.argb(alpha, 255, 255, 255)
        }

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    /**
     * BALANCED: Good balance between precision and clean removal
     * Best for: Most use cases
     * Threshold: 0.5 (50% confidence)
     */
    private fun createBalancedMask(mask: FloatBuffer, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)

        mask.rewind()
        for (i in 0 until width * height) {
            val confidence = mask.get()

            val alpha = if (confidence > 0.5f) {
                // Map 0.5-1.0 to 0-255 for smooth edges
                ((confidence - 0.5f) * 2 * 255).toInt().coerceIn(0, 255)
            } else {
                0
            }

            pixels[i] = Color.argb(alpha, 255, 255, 255)
        }

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    /**
     * SOFT EDGES: Smooth, feathered edges
     * Best for: Natural blending, hair details
     * Uses gradient alpha with power curve
     */
    private fun createSoftEdgeMask(mask: FloatBuffer, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)

        mask.rewind()
        for (i in 0 until width * height) {
            val confidence = mask.get()

            // Apply smooth curve for softer edges
            val alpha = if (confidence > 0.2f) {
                // Use power function for smoother transition
                val normalized = (confidence - 0.2f) / 0.8f
                (normalized.pow(0.7f) * 255).toInt().coerceIn(0, 255)
            } else {
                0
            }

            pixels[i] = Color.argb(alpha, 255, 255, 255)
        }

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    /**
     * AGGRESSIVE: More aggressive background removal
     * Best for: Clean backgrounds, product photos, removing extra background
     * Threshold: 0.6 (60% confidence)
     */
    private fun createAggressiveMask(mask: FloatBuffer, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)

        mask.rewind()
        for (i in 0 until width * height) {
            val confidence = mask.get()

            // Higher threshold = remove more
            val alpha = if (confidence > 0.6f) {
                // Sharp cutoff for clean edges
                if (confidence > 0.75f) {
                    255  // Fully opaque for high confidence
                } else {
                    // Smooth transition from 0.6 to 0.75
                    ((confidence - 0.6f) / 0.15f * 255).toInt()
                }
            } else {
                0
            }

            pixels[i] = Color.argb(alpha, 255, 255, 255)
        }

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    fun close() {
        segmenter.close()
    }
}

enum class MaskQuality {
    HIGH_PRECISION,  // Keep more subject details (30% threshold) - prevents cutting body parts
    BALANCED,        // Default (50% threshold) - good for most cases
    SOFT_EDGES,      // Smooth, feathered edges (20% threshold with curve) - best for hair
    AGGRESSIVE       // Cleaner removal (60% threshold) - removes extra background
}

