package com.remover.background.AI.ml

import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Advanced post-processing to refine MLKit segmentation masks
 * Implements edge refinement, morphological operations, and guided filtering
 */
class MaskRefinementProcessor {

    /**
     * Complete refinement pipeline - applies all improvements
     */
    suspend fun refineMask(
        mask: Bitmap,
        originalImage: Bitmap,
        config: RefinementConfig = RefinementConfig()
    ): Bitmap = withContext(Dispatchers.Default) {
        var refined = mask.copy(Bitmap.Config.ARGB_8888, true)

        // Step 1: Morphological operations (remove noise, fill holes)
        if (config.enableMorphology) {
            refined = applyMorphologicalOperations(refined, config.morphologyRadius)
        }

        // Step 2: Guided filter (edge-aware smoothing using original image)
        if (config.enableGuidedFilter) {
            refined = applyGuidedFilter(refined, originalImage, config.guidedFilterRadius, config.guidedFilterEps)
        }

        // Step 3: Edge feathering (soft, natural transitions)
        if (config.enableFeathering) {
            refined = applyAdaptiveFeathering(refined, config.featherRadius)
        }

        // Step 4: Color decontamination (remove background color bleed)
        if (config.enableDecontamination) {
            refined = removeColorContamination(refined, originalImage)
        }

        return@withContext refined
    }

    /**
     * Morphological operations: Remove noise and fill small holes
     */
    private fun applyMorphologicalOperations(mask: Bitmap, radius: Int): Bitmap {
        val width = mask.width
        val height = mask.height
        
        // Step 1: Closing (dilation then erosion) - fills small holes
        var result = dilate(mask, radius)
        result = erode(result, radius)
        
        // Step 2: Opening (erosion then dilation) - removes small noise
        result = erode(result, radius)
        result = dilate(result, radius)
        
        return result
    }

    /**
     * Dilation: Expand white areas (foreground)
     */
    private fun dilate(bitmap: Bitmap, radius: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val output = IntArray(width * height)

        for (y in 0 until height) {
            for (x in 0 until width) {
                var maxAlpha = 0
                
                // Check neighborhood
                for (dy in -radius..radius) {
                    for (dx in -radius..radius) {
                        val nx = (x + dx).coerceIn(0, width - 1)
                        val ny = (y + dy).coerceIn(0, height - 1)
                        val alpha = Color.alpha(pixels[ny * width + nx])
                        maxAlpha = max(maxAlpha, alpha)
                    }
                }
                
                output[y * width + x] = Color.argb(maxAlpha, 255, 255, 255)
            }
        }

        result.setPixels(output, 0, width, 0, 0, width, height)
        return result
    }

    /**
     * Erosion: Shrink white areas (foreground)
     */
    private fun erode(bitmap: Bitmap, radius: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val output = IntArray(width * height)

        for (y in 0 until height) {
            for (x in 0 until width) {
                var minAlpha = 255
                
                // Check neighborhood
                for (dy in -radius..radius) {
                    for (dx in -radius..radius) {
                        val nx = (x + dx).coerceIn(0, width - 1)
                        val ny = (y + dy).coerceIn(0, height - 1)
                        val alpha = Color.alpha(pixels[ny * width + nx])
                        minAlpha = min(minAlpha, alpha)
                    }
                }
                
                output[y * width + x] = Color.argb(minAlpha, 255, 255, 255)
            }
        }

        result.setPixels(output, 0, width, 0, 0, width, height)
        return result
    }

    /**
     * Guided Filter: Edge-aware smoothing using the original image as guide
     * Preserves edges while smoothing the mask
     */
    private fun applyGuidedFilter(
        mask: Bitmap,
        guide: Bitmap,
        radius: Int,
        eps: Float
    ): Bitmap {
        val width = mask.width
        val height = mask.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        val maskPixels = IntArray(width * height)
        val guidePixels = IntArray(width * height)
        mask.getPixels(maskPixels, 0, width, 0, 0, width, height)
        guide.getPixels(guidePixels, 0, width, 0, 0, width, height)
        
        val output = IntArray(width * height)
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                var sumMask = 0f
                var sumGuide = 0f
                var sumGuideMask = 0f
                var sumGuideSquare = 0f
                var count = 0
                
                // Box filter in neighborhood
                for (dy in -radius..radius) {
                    for (dx in -radius..radius) {
                        val nx = (x + dx).coerceIn(0, width - 1)
                        val ny = (y + dy).coerceIn(0, height - 1)
                        val idx = ny * width + nx
                        
                        val maskVal = Color.alpha(maskPixels[idx]) / 255f
                        val guideVal = Color.red(guidePixels[idx]) / 255f // Use red channel as guide
                        
                        sumMask += maskVal
                        sumGuide += guideVal
                        sumGuideMask += guideVal * maskVal
                        sumGuideSquare += guideVal * guideVal
                        count++
                    }
                }
                
                // Compute local linear coefficients
                val meanMask = sumMask / count
                val meanGuide = sumGuide / count
                val meanGuideMask = sumGuideMask / count
                val meanGuideSquare = sumGuideSquare / count
                
                val variance = meanGuideSquare - meanGuide * meanGuide
                val a = (meanGuideMask - meanGuide * meanMask) / (variance + eps)
                val b = meanMask - a * meanGuide
                
                // Apply filter
                val guideCenter = Color.red(guidePixels[y * width + x]) / 255f
                val filtered = (a * guideCenter + b).coerceIn(0f, 1f)
                val alpha = (filtered * 255).toInt()
                
                output[y * width + x] = Color.argb(alpha, 255, 255, 255)
            }
        }
        
        result.setPixels(output, 0, width, 0, 0, width, height)
        return result
    }

    /**
     * Adaptive Feathering: Blur edges based on edge strength
     * Stronger edges get less blur, weaker edges get more blur
     */
    private fun applyAdaptiveFeathering(mask: Bitmap, radius: Int): Bitmap {
        val width = mask.width
        val height = mask.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        val pixels = IntArray(width * height)
        mask.getPixels(pixels, 0, width, 0, 0, width, height)
        
        // Detect edges
        val edgeStrength = FloatArray(width * height)
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val idx = y * width + x
                val center = Color.alpha(pixels[idx])
                val left = Color.alpha(pixels[idx - 1])
                val right = Color.alpha(pixels[idx + 1])
                val top = Color.alpha(pixels[idx - width])
                val bottom = Color.alpha(pixels[idx + width])
                
                val gradX = (right - left) / 2f
                val gradY = (bottom - top) / 2f
                edgeStrength[idx] = sqrt(gradX * gradX + gradY * gradY)
            }
        }
        
        // Apply adaptive Gaussian blur
        val output = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                val strength = edgeStrength[idx]
                
                // Adaptive radius: weak edges get more blur
                val adaptiveRadius = if (strength > 50) radius / 2 else radius
                
                var sum = 0f
                var weightSum = 0f
                
                for (dy in -adaptiveRadius..adaptiveRadius) {
                    for (dx in -adaptiveRadius..adaptiveRadius) {
                        val nx = (x + dx).coerceIn(0, width - 1)
                        val ny = (y + dy).coerceIn(0, height - 1)
                        
                        val dist = sqrt((dx * dx + dy * dy).toFloat())
                        val weight = exp(-dist * dist / (2 * adaptiveRadius * adaptiveRadius))
                        
                        sum += Color.alpha(pixels[ny * width + nx]) * weight
                        weightSum += weight
                    }
                }
                
                val alpha = (sum / weightSum).toInt().coerceIn(0, 255)
                output[idx] = Color.argb(alpha, 255, 255, 255)
            }
        }
        
        result.setPixels(output, 0, width, 0, 0, width, height)
        return result
    }

    /**
     * Color Decontamination: Remove background color bleeding from edges
     */
    private fun removeColorContamination(mask: Bitmap, original: Bitmap): Bitmap {
        // This is a placeholder - full implementation would analyze edge colors
        // and remove background color influence from semi-transparent pixels
        // For now, we just return the mask as-is
        // A full implementation would require modifying the foreground image, not just the mask
        return mask
    }
}

/**
 * Configuration for mask refinement
 */
data class RefinementConfig(
    val enableMorphology: Boolean = true,
    val morphologyRadius: Int = 1, // Reduced from 2 for speed
    
    val enableGuidedFilter: Boolean = true,
    val guidedFilterRadius: Int = 3, // Reduced from 4 for speed
    val guidedFilterEps: Float = 0.02f, // Increased for smoother results
    
    val enableFeathering: Boolean = true,
    val featherRadius: Int = 2, // Reduced from 3 for speed
    
    val enableDecontamination: Boolean = false
) {
    companion object {
        /**
         * Fast preset - Quick refinement with minimal processing
         */
        fun fast() = RefinementConfig(
            enableMorphology = true,
            morphologyRadius = 1,
            enableGuidedFilter = false, // Skip for speed
            enableFeathering = true,
            featherRadius = 1
        )
        
        /**
         * Balanced preset - Good quality/speed trade-off (default)
         */
        fun balanced() = RefinementConfig()
        
        /**
         * Quality preset - Best quality, slower
         */
        fun quality() = RefinementConfig(
            enableMorphology = true,
            morphologyRadius = 2,
            enableGuidedFilter = true,
            guidedFilterRadius = 5,
            guidedFilterEps = 0.01f,
            enableFeathering = true,
            featherRadius = 3
        )
    }
}
