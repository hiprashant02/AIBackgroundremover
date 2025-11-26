package com.remover.background.AI.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

class FileManager(private val context: Context) {

    suspend fun saveBitmapToGallery(
        bitmap: Bitmap,
        fileName: String,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG,
        quality: Int = 100
    ): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            // Automatically detect if PNG should be used for transparency
            val shouldUsePNG = bitmap.hasAlpha() || format == Bitmap.CompressFormat.PNG
            val finalFormat = if (shouldUsePNG) Bitmap.CompressFormat.PNG else format

            // Ensure quality is 100 for best results
            val finalQuality = 100

            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, when (finalFormat) {
                    Bitmap.CompressFormat.PNG -> "image/png"
                    Bitmap.CompressFormat.JPEG -> "image/jpeg"
                    Bitmap.CompressFormat.WEBP -> "image/webp"
                    else -> "image/png"
                })

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AIBackgroundRemover")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val uri = context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            ) ?: return@withContext Result.failure(Exception("Failed to create media store entry"))

            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                // Use maximum quality and flush the stream
                bitmap.compress(finalFormat, finalQuality, outputStream)
                outputStream.flush()
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, contentValues, null, null)
            }

            Result.success(uri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveBitmapToCache(bitmap: Bitmap, fileName: String): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val cacheDir = File(context.cacheDir, "images")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }

            val file = File(cacheDir, fileName)
            FileOutputStream(file).use { outputStream ->
                // Always use PNG with maximum quality for cache (lossless)
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                outputStream.flush()
            }

            Result.success(Uri.fromFile(file))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun clearCache() {
        val cacheDir = File(context.cacheDir, "images")
        if (cacheDir.exists()) {
            cacheDir.listFiles()?.forEach { it.delete() }
        }
    }
}

