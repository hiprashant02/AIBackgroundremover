package com.remover.background.AI.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.google.android.play.core.assetpacks.AssetPackManagerFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.FloatBuffer

/**
 * Background removal processor using a custom converted IS-Net (DIS) quantized ONNX model.
 * Runs on the ONNX Runtime Mobile SDK.
 */
class OnnxBackgroundRemovalProcessor(private val context: Context) {

    private var ortEnv: OrtEnvironment? = null
    private var ortSession: OrtSession? = null
    private val modelInputSize = 1024 // IS-Net input size

    init {
        initializeSession()
    }

    private fun initializeSession() {
        Log.d("OnnxProcessor", "initializeSession: Starting ONNX session initialization...")
        try {
            ortEnv = OrtEnvironment.getEnvironment()
            
            val options = OrtSession.SessionOptions().apply {
                try {
                    addNnapi()
                    Log.d("OnnxProcessor", "initializeSession: NNAPI acceleration enabled successfully.")
                } catch (e: Exception) {
                    Log.w("OnnxProcessor", "initializeSession: NNAPI acceleration not supported on this device. Fallback to CPU.")
                }
            }

            try {
                // Try reading model directly from assets (works for merged install-time asset pack and debug sourceSet)
                Log.d("OnnxProcessor", "initializeSession: Attempting to load model from assets...")
                context.assets.open("isnet_general_quantized.onnx").use { inputStream ->
                    val modelBytes = inputStream.readBytes()
                    ortSession = ortEnv?.createSession(modelBytes, options)
                    Log.i("OnnxProcessor", "initializeSession: Successfully loaded and initialized model from assets.")
                }
            } catch (assetException: Exception) {
                Log.w("OnnxProcessor", "initializeSession: Failed to load from assets: ${assetException.message}. Trying Play Asset Delivery fallback...")
                
                // Fallback: Resolve path via Play Asset Delivery if not merged
                val assetPackManager = AssetPackManagerFactory.getInstance(context)
                val location = assetPackManager.getPackLocation("birefnet-model")
                val modelPath = if (location != null) {
                    File(location.assetsPath(), "isnet_general_quantized.onnx").absolutePath
                } else {
                    File(context.filesDir, "isnet_general_quantized.onnx").absolutePath
                }

                val modelFile = File(modelPath)
                if (modelFile.exists()) {
                    ortSession = ortEnv?.createSession(modelPath, options)
                    Log.i("OnnxProcessor", "initializeSession: Successfully loaded and initialized model from Play Asset Delivery path: $modelPath")
                } else {
                    Log.e("OnnxProcessor", "initializeSession: Model file not found at path: $modelPath. Loading will fail.")
                }
            }
        } catch (e: Exception) {
            Log.e("OnnxProcessor", "initializeSession: Critical initialization error: ${e.message}", e)
        }
    }

    /**
     * Removes the background from the provided bitmap.
     * Returns a new Bitmap containing only the foreground subject with a transparent background.
     */
    suspend fun removeBackground(bitmap: Bitmap): Result<Bitmap> = withContext(Dispatchers.Default) {
        Log.d("OnnxProcessor", "removeBackground: Starting background removal for bitmap ${bitmap.width}x${bitmap.height}")
        val env = ortEnv
        if (env == null) {
            Log.e("OnnxProcessor", "removeBackground: ONNX Environment not initialized")
            return@withContext Result.failure<Bitmap>(Exception("ONNX Environment not initialized"))
        }
        
        val session = ortSession
        if (session == null) {
            Log.d("OnnxProcessor", "removeBackground: Session is null, attempting re-initialization")
            initializeSession()
            if (ortSession == null) {
                Log.e("OnnxProcessor", "removeBackground: ONNX Session not initialized after re-init attempt")
                return@withContext Result.failure<Bitmap>(Exception("ONNX Session not initialized. Check if isnet_general_quantized.onnx is in the asset pack."))
            }
        }

        return@withContext try {
            val width = bitmap.width
            val height = bitmap.height

            // 1. Preprocess: Resize original bitmap to model input size (1024x1024)
            Log.d("OnnxProcessor", "removeBackground: Resizing bitmap to ${modelInputSize}x${modelInputSize}")
            val resizedBitmap = Bitmap.createScaledBitmap(bitmap, modelInputSize, modelInputSize, true)
            
            // 2. Preprocess: Convert Bitmap to Planar FloatBuffer [1, 3, 1024, 1024] with IS-Net normalization (mean=0.5, std=1.0)
            Log.d("OnnxProcessor", "removeBackground: Converting bitmap to planar float buffer")
            val floatBuffer = convertBitmapToPlanarBuffer(resizedBitmap)
            val inputTensor = OnnxTensor.createTensor(
                env, 
                floatBuffer, 
                longArrayOf(1, 3, modelInputSize.toLong(), modelInputSize.toLong())
            )

            // 3. Run Inference
            val inputName = ortSession?.inputNames?.firstOrNull() ?: "input"
            Log.d("OnnxProcessor", "removeBackground: Running session inference with input name: $inputName")
            val outputs = ortSession?.run(mapOf(inputName to inputTensor))
            val outputTensor = outputs?.get(0) as? OnnxTensor

            if (outputTensor != null) {
                Log.d("OnnxProcessor", "removeBackground: Inference completed successfully. Post-processing mask...")
                // 4. Postprocess: Map output logits through Sigmoid to alpha mask, and resize back to original resolution
                val maskBitmap = generateMaskBitmap(outputTensor, width, height)
                
                // 5. Blend Mask with Original Bitmap to extract transparent foreground
                Log.d("OnnxProcessor", "removeBackground: Blending mask with original bitmap")
                val resultBitmap = applyMaskToBitmap(bitmap, maskBitmap)
                
                resizedBitmap.recycle()
                Log.d("OnnxProcessor", "removeBackground: Background removal completed successfully!")
                Result.success(resultBitmap)
            } else {
                resizedBitmap.recycle()
                Log.e("OnnxProcessor", "removeBackground: Model execution returned null or non-OnnxTensor output")
                Result.failure(Exception("Model execution returned empty output"))
            }
        } catch (e: Exception) {
            Log.e("OnnxProcessor", "removeBackground: Exception during background removal execution: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Converts a bitmap to a FloatBuffer in Planar format [3, H, W] with IS-Net normalization (mean=0.5, std=1.0).
     */
    private fun convertBitmapToPlanarBuffer(bitmap: Bitmap): FloatBuffer {
        val size = modelInputSize * modelInputSize
        val floatBuffer = FloatBuffer.allocate(size * 3)
        val intValues = IntArray(size)
        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        // IS-Net Normalization Stats: mean = 0.5, std = 1.0 (ranges from -0.5 to 0.5)
        val mean = 0.5f
        val std = 1.0f

        // Write R plane
        for (i in 0 until size) {
            val color = intValues[i]
            val r = ((color shr 16 and 0xFF) / 255.0f - mean) / std
            floatBuffer.put(i, r)
        }
        // Write G plane
        for (i in 0 until size) {
            val color = intValues[i]
            val g = ((color shr 8 and 0xFF) / 255.0f - mean) / std
            floatBuffer.put(size + i, g)
        }
        // Write B plane
        for (i in 0 until size) {
            val color = intValues[i]
            val b = ((color and 0xFF) / 255.0f - mean) / std
            floatBuffer.put(2 * size + i, b)
        }

        floatBuffer.rewind()
        return floatBuffer
    }

    /**
     * Parses the output probability tensor and generates a scaled mask.
     */
    private fun generateMaskBitmap(tensor: OnnxTensor, targetWidth: Int, targetHeight: Int): Bitmap {
        val floatBuffer = tensor.floatBuffer
        Log.d("OnnxProcessor", "generateMaskBitmap: FloatBuffer capacity: ${floatBuffer.capacity()}, remaining: ${floatBuffer.remaining()}, isDirect: ${floatBuffer.isDirect}")
        
        val size = modelInputSize * modelInputSize
        val floatArray = FloatArray(size)
        floatBuffer.rewind() // Reset position to 0
        floatBuffer.get(floatArray) // Safe copy of direct buffer data to heap array

        // Log stats of output logits to verify they are valid
        var minVal = Float.MAX_VALUE
        var maxVal = -Float.MAX_VALUE
        for (f in floatArray) {
            if (f < minVal) minVal = f
            if (f > maxVal) maxVal = f
        }
        Log.d("OnnxProcessor", "generateMaskBitmap: Probability stats: min=$minVal, max=$maxVal. First 10: ${floatArray.take(10).joinToString()}")

        val maskPixels = IntArray(size)
        for (i in 0 until size) {
            val score = floatArray[i]
            // Map probability directly to alpha channel byte (0 to 255)
            val alpha = (score * 255).toInt().coerceIn(0, 255)
            // ARGB format: Grayscale white mask with alpha
            maskPixels[i] = (alpha shl 24) or 0x00FFFFFF
        }

        val tempMask = Bitmap.createBitmap(modelInputSize, modelInputSize, Bitmap.Config.ARGB_8888)
        tempMask.setPixels(maskPixels, 0, modelInputSize, 0, 0, modelInputSize, modelInputSize)
        
        // Resize mask back to match original image dimensions
        val finalMask = Bitmap.createScaledBitmap(tempMask, targetWidth, targetHeight, true)
        tempMask.recycle()
        return finalMask
    }

    /**
     * Merges original bitmap and alpha mask using Porter-Duff DST_IN.
     */
    private fun applyMaskToBitmap(original: Bitmap, mask: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(original.width, original.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(result)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        
        canvas.drawBitmap(original, 0f, 0f, paint)
        paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN)
        canvas.drawBitmap(mask, 0f, 0f, paint)
        
        mask.recycle()
        return result
    }

    fun close() {
        try {
            ortSession?.close()
            ortEnv?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
