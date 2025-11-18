package com.remover.background.AI.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.remover.background.AI.ml.BackgroundRemovalProcessor
import com.remover.background.AI.model.BackgroundType
import com.remover.background.AI.utils.FileManager
import com.remover.background.AI.utils.ImageProcessor
import kotlinx.coroutines.launch
import java.io.InputStream

sealed class EditorState {
    object Idle : EditorState()
    object Loading : EditorState()
    data class Success(val bitmap: Bitmap) : EditorState()
    data class Error(val message: String) : EditorState()
}

data class UndoRedoState(
    val bitmap: Bitmap,
    val backgroundType: BackgroundType
)

class EditorViewModel : ViewModel() {

    var editorState by mutableStateOf<EditorState>(EditorState.Idle)
        private set

    var originalBitmap by mutableStateOf<Bitmap?>(null)
        private set

    var maskBitmap by mutableStateOf<Bitmap?>(null)
        private set

    var currentBackground by mutableStateOf<BackgroundType>(BackgroundType.Transparent)
        private set

    var isProcessing by mutableStateOf(false)
        private set

    var isSaving by mutableStateOf(false)
        private set

    private val undoStack = mutableListOf<UndoRedoState>()
    private val redoStack = mutableListOf<UndoRedoState>()

    var canUndo by mutableStateOf(false)
        private set

    var canRedo by mutableStateOf(false)
        private set

    private var processor: BackgroundRemovalProcessor? = null
    private val imageProcessor = ImageProcessor()
    private var fileManager: FileManager? = null

    fun initialize(context: Context) {
        if (processor == null) {
            processor = BackgroundRemovalProcessor(context)
            fileManager = FileManager(context)
        }
    }

    fun loadImage(context: Context, uri: Uri) {
        viewModelScope.launch {
            editorState = EditorState.Loading
            isProcessing = true

            try {
                val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                if (bitmap == null) {
                    editorState = EditorState.Error("Failed to load image")
                    isProcessing = false
                    return@launch
                }

                // Resize if needed
                val resizedBitmap = imageProcessor.resizeIfNeeded(bitmap)
                if (resizedBitmap != bitmap) {
                    bitmap.recycle()
                }

                originalBitmap = resizedBitmap

                // Process with MLKit
                processBackground(resizedBitmap)
            } catch (e: Exception) {
                editorState = EditorState.Error("Error loading image: ${e.message}")
                isProcessing = false
            }
        }
    }

    private suspend fun processBackground(bitmap: Bitmap) {
        try {
            // Get the mask from MLKit
            val maskResult = processor?.getMask(bitmap)

            if (maskResult?.isSuccess == true) {
                maskBitmap = maskResult.getOrNull()

                // Apply transparent background by default
                applyBackground(BackgroundType.Transparent)
            } else {
                editorState = EditorState.Error("Failed to process background: ${maskResult?.exceptionOrNull()?.message}")
            }
        } catch (e: Exception) {
            editorState = EditorState.Error("Error processing: ${e.message}")
        } finally {
            isProcessing = false
        }
    }

    fun applyBackground(background: BackgroundType) {
        viewModelScope.launch {
            isProcessing = true

            try {
                val original = originalBitmap
                val mask = maskBitmap

                if (original == null || mask == null) {
                    editorState = EditorState.Error("No image loaded")
                    isProcessing = false
                    return@launch
                }

                // Save current state to undo stack before making changes
                if (editorState is EditorState.Success) {
                    val currentBitmap = (editorState as EditorState.Success).bitmap
                    undoStack.add(UndoRedoState(currentBitmap.copy(currentBitmap.config ?: Bitmap.Config.ARGB_8888, true), currentBackground))
                    redoStack.clear()
                    updateUndoRedoState()
                }

                val processedBitmap = imageProcessor.applyMaskToBitmap(original, mask, background)

                currentBackground = background
                editorState = EditorState.Success(processedBitmap)
            } catch (e: Exception) {
                editorState = EditorState.Error("Error applying background: ${e.message}")
            } finally {
                isProcessing = false
            }
        }
    }

    fun undo() {
        if (undoStack.isEmpty()) return

        viewModelScope.launch {
            val currentState = editorState
            if (currentState is EditorState.Success) {
                redoStack.add(UndoRedoState(currentState.bitmap.copy(currentState.bitmap.config ?: Bitmap.Config.ARGB_8888, true), currentBackground))
            }

            val previousState = undoStack.removeAt(undoStack.lastIndex)
            editorState = EditorState.Success(previousState.bitmap)
            currentBackground = previousState.backgroundType

            updateUndoRedoState()
        }
    }

    fun redo() {
        if (redoStack.isEmpty()) return

        viewModelScope.launch {
            val currentState = editorState
            if (currentState is EditorState.Success) {
                undoStack.add(UndoRedoState(currentState.bitmap.copy(currentState.bitmap.config ?: Bitmap.Config.ARGB_8888, true), currentBackground))
            }

            val nextState = redoStack.removeAt(redoStack.lastIndex)
            editorState = EditorState.Success(nextState.bitmap)
            currentBackground = nextState.backgroundType

            updateUndoRedoState()
        }
    }

    private fun updateUndoRedoState() {
        canUndo = undoStack.isNotEmpty()
        canRedo = redoStack.isNotEmpty()
    }

    fun saveBitmap(format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG, onComplete: (Result<Uri>) -> Unit) {
        val state = editorState
        if (state !is EditorState.Success) {
            onComplete(Result.failure(Exception("No image to save")))
            return
        }

        viewModelScope.launch {
            isSaving = true

            val fileName = "BG_Removed_${System.currentTimeMillis()}.${
                when (format) {
                    Bitmap.CompressFormat.PNG -> "png"
                    Bitmap.CompressFormat.JPEG -> "jpg"
                    else -> "png"
                }
            }"

            val result = fileManager?.saveBitmapToGallery(state.bitmap, fileName, format)

            isSaving = false

            if (result != null) {
                onComplete(result)
            } else {
                onComplete(Result.failure(Exception("File manager not initialized")))
            }
        }
    }

    fun reset() {
        editorState = EditorState.Idle
        originalBitmap = null
        maskBitmap = null
        currentBackground = BackgroundType.Transparent
        undoStack.clear()
        redoStack.clear()
        updateUndoRedoState()
    }

    override fun onCleared() {
        super.onCleared()
        processor?.close()
        originalBitmap?.recycle()
        maskBitmap?.recycle()

        if (editorState is EditorState.Success) {
            (editorState as EditorState.Success).bitmap.recycle()
        }

        undoStack.forEach { it.bitmap.recycle() }
        redoStack.forEach { it.bitmap.recycle() }
    }
}

