// ... imports ...
package com.remover.background.AI.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.remover.background.AI.ml.BackgroundRemovalProcessor
import com.remover.background.AI.ml.MaskQuality
import com.remover.background.AI.model.BackgroundType
import com.remover.background.AI.model.BrushMode
import com.remover.background.AI.model.BrushTool
import com.remover.background.AI.model.DrawingPath
import com.remover.background.AI.utils.FileManager
import com.remover.background.AI.utils.ImageProcessor
import com.remover.background.AI.utils.ManualEditingProcessor
import kotlinx.coroutines.launch
import java.io.InputStream

// ... EditorState and UndoRedoState classes ...
sealed class EditorState {
    object Idle : EditorState()
    object Loading : EditorState()
    data class Success(val bitmap: Bitmap) : EditorState()
    data class Error(val message: String) : EditorState()
}

data class UndoRedoState(val bitmap: Bitmap, val backgroundType: BackgroundType)

class EditorViewModel : ViewModel() {
    // ... all existing properties ...
    var editorState by mutableStateOf<EditorState>(EditorState.Idle)
        private set
    var originalBitmap by mutableStateOf<Bitmap?>(null)
        private set
    var foregroundBitmap by mutableStateOf<Bitmap?>(null)
        private set
    var maskBitmap by mutableStateOf<Bitmap?>(null)
        private set
    var currentBackground by mutableStateOf<BackgroundType>(BackgroundType.Transparent)
        private set
    var currentMaskQuality by mutableStateOf<MaskQuality>(MaskQuality.BALANCED)
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
    private val manualEditingProcessor = ManualEditingProcessor()
    var useDirectForeground by mutableStateOf(true)
        private set
    var isManualEditMode by mutableStateOf(false)
        private set
    var currentBrushTool by mutableStateOf(BrushTool())
        private set
    var editableMask by mutableStateOf<Bitmap?>(null)
        private set
    private val brushStrokes = mutableListOf<DrawingPath>()

    // ... Standard Initialization and Load Logic (Unchanged) ...
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
                val inputStream = context.contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                if (bitmap == null) {
                    editorState = EditorState.Error("Failed to load image")
                    return@launch
                }
                val resized = imageProcessor.resizeIfNeeded(bitmap)
                if (resized != bitmap) bitmap.recycle()
                originalBitmap = resized
                processBackground(resized)
            } catch (e: Exception) {
                editorState = EditorState.Error("Error: ${e.message}")
            } finally {
                isProcessing = false
            }
        }
    }

    private suspend fun processBackground(bitmap: Bitmap) {
        try {
            val result = processor?.removeBackground(bitmap)
            if (result?.isSuccess == true) {
                foregroundBitmap = result.getOrNull()
                applyBackgroundToForeground(BackgroundType.Transparent)
            } else {
                editorState = EditorState.Error("Failed background removal")
            }
        } catch(e: Exception) { editorState = EditorState.Error(e.message ?: "Error") }
    }

    // ... Background Application Logic (Unchanged) ...
    private fun applyBackgroundToForeground(background: BackgroundType) {
        viewModelScope.launch {
            isProcessing = true
            try {
                val orig = originalBitmap ?: return@launch
                val fg = foregroundBitmap ?: return@launch
                val result = imageProcessor.composeFinalImage(fg, background, orig.width, orig.height, orig)
                currentBackground = background
                editorState = EditorState.Success(result)
            } finally { isProcessing = false }
        }
    }

    fun applyBackground(background: BackgroundType) { applyBackgroundToForeground(background) }

    fun undo() { /* existing logic */ }
    fun redo() { /* existing logic */ }
    fun saveBitmap(format: Bitmap.CompressFormat, onComplete: (Result<Uri>) -> Unit) { /* existing logic */ }

    // ... Manual Editing Mode ...
    fun enterManualEditMode() {
        viewModelScope.launch {
            val fg = foregroundBitmap ?: return@launch
            editableMask = manualEditingProcessor.createMaskFromForeground(fg)
            currentBrushTool = currentBrushTool.copy(size = manualEditingProcessor.calculateOptimalBrushSize(fg.width, fg.height))
            isManualEditMode = true
        }
    }

    fun exitManualEditMode(apply: Boolean) {
        viewModelScope.launch {
            if (apply) {
                if (brushStrokes.isNotEmpty()) applyPendingStrokes()
                val mask = editableMask
                val orig = originalBitmap
                if (mask != null && orig != null) {
                    isProcessing = true
                    val editedFg = manualEditingProcessor.applyEditedMask(orig, mask)
                    foregroundBitmap = editedFg
                    val result = imageProcessor.composeFinalImage(editedFg, currentBackground, orig.width, orig.height, orig)
                    editorState = EditorState.Success(result)
                    isProcessing = false
                }
            }
            isManualEditMode = false
            brushStrokes.clear()
        }
    }

    // --- FIX IS HERE ---
    fun addBrushStroke(path: DrawingPath) {
        brushStrokes.add(path)
        // Draw IMMEDIATELY. Do not wait for batching.
        applyPendingStrokes()
    }

    fun applyStrokes() { applyPendingStrokes() }

    private fun applyPendingStrokes() {
        if (brushStrokes.isEmpty()) return
        viewModelScope.launch {
            val mask = editableMask ?: return@launch
            val orig = originalBitmap ?: return@launch
            // NOTE: We don't set isProcessing=true here to keep the UI responsive during drawing

            val updatedMask = manualEditingProcessor.applyBrushStrokes(mask, brushStrokes.toList(), mask.width, mask.height)
            editableMask = updatedMask

            val editedFg = manualEditingProcessor.applyEditedMask(orig, updatedMask)
            val result = imageProcessor.composeFinalImage(editedFg, currentBackground, orig.width, orig.height, orig)

            editorState = EditorState.Success(result)
            brushStrokes.clear()
        }
    }

    fun clearBrushStrokes() {
        viewModelScope.launch {
            val fg = foregroundBitmap ?: return@launch
            editableMask = manualEditingProcessor.createMaskFromForeground(fg)
            brushStrokes.clear()
            // trigger refresh...
        }
    }

    fun updateBrushTool(mode: BrushMode?, size: Float?, hardness: Float?, opacity: Float?) {
        currentBrushTool = currentBrushTool.copy(
            mode = mode ?: currentBrushTool.mode,
            size = size ?: currentBrushTool.size,
            hardness = hardness ?: currentBrushTool.hardness,
            opacity = opacity ?: currentBrushTool.opacity
        )
    }

    fun smoothMask() { /* existing logic */ }
    fun reset() { /* existing logic */ }
}