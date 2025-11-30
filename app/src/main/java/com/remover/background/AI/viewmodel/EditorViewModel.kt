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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    // State to restore when canceling manual edit mode
    private var savedForegroundBeforeManualEdit: Bitmap? = null
    private var savedDisplayBitmapBeforeManualEdit: Bitmap? = null
    private var savedBackgroundBeforeManualEdit: BackgroundType? = null

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
    
    fun saveBitmap(format: Bitmap.CompressFormat, onComplete: (Result<Uri>) -> Unit) {
        viewModelScope.launch {
            isSaving = true
            try {
                val bitmap = (editorState as? EditorState.Success)?.bitmap
                if (bitmap != null) {
                    val fileName = "edited_${System.currentTimeMillis()}.png"
                    val result = fileManager?.saveBitmapToGallery(bitmap, fileName, format)
                    if (result != null) {
                        onComplete(result)
                    } else {
                        onComplete(Result.failure(Exception("FileManager not initialized")))
                    }
                } else {
                    onComplete(Result.failure(Exception("No image to save")))
                }
            } catch (e: Exception) {
                onComplete(Result.failure(e))
            } finally {
                isSaving = false
            }
        }
    }
    
    fun applyStrokes() { applyPendingStrokes() }

    // --- Undo / Redo Logic ---
    private fun saveToUndoStack() {
        val currentFg = foregroundBitmap ?: return
        val currentMask = editableMask ?: return
        
        // We save a COPY of the bitmaps because they are mutable
        val fgCopy = currentFg.copy(currentFg.config ?: Bitmap.Config.ARGB_8888, true)
        val maskCopy = currentMask.copy(currentMask.config ?: Bitmap.Config.ARGB_8888, true)
        
        undoStack.add(UndoRedoState(fgCopy, maskCopy))
        redoStack.clear()
        updateUndoRedoState()
    }

    private fun updateUndoRedoState() {
        canUndo = undoStack.isNotEmpty()
        canRedo = redoStack.isNotEmpty()
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        
        val currentState = UndoRedoState(
            foregroundBitmap?.copy(foregroundBitmap!!.config ?: Bitmap.Config.ARGB_8888, true) ?: return,
            editableMask?.copy(editableMask!!.config ?: Bitmap.Config.ARGB_8888, true) ?: return
        )
        redoStack.add(currentState)
        
        val previousState = undoStack.removeAt(undoStack.lastIndex)
        restoreState(previousState)
        updateUndoRedoState()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        
        val currentState = UndoRedoState(
            foregroundBitmap?.copy(foregroundBitmap!!.config ?: Bitmap.Config.ARGB_8888, true) ?: return,
            editableMask?.copy(editableMask!!.config ?: Bitmap.Config.ARGB_8888, true) ?: return
        )
        undoStack.add(currentState)
        
        val nextState = redoStack.removeAt(redoStack.lastIndex)
        restoreState(nextState)
        updateUndoRedoState()
    }

    private fun restoreState(state: UndoRedoState) {
        viewModelScope.launch {
            foregroundBitmap = state.foregroundBitmap
            editableMask = state.maskBitmap
            
            val orig = originalBitmap ?: return@launch
            val result = imageProcessor.composeFinalImage(
                state.foregroundBitmap, 
                currentBackground, 
                orig.width, 
                orig.height, 
                orig
            )
            editorState = EditorState.Success(result)
        }
    }

    // --- Manual Editing Fixes ---

    fun enterManualEditMode() {
        viewModelScope.launch {
            val fg = foregroundBitmap ?: return@launch

            // Save current state so we can restore it on cancel
            savedForegroundBeforeManualEdit = fg.copy(fg.config ?: Bitmap.Config.ARGB_8888, true)
            val currentBitmap = (editorState as? EditorState.Success)?.bitmap
            if (currentBitmap != null) {
                savedDisplayBitmapBeforeManualEdit = currentBitmap.copy(currentBitmap.config ?: Bitmap.Config.ARGB_8888, true)
                savedBackgroundBeforeManualEdit = currentBackground
            }

            // Create a fresh mask from the current foreground
            editableMask = manualEditingProcessor.createMaskFromForeground(fg)
            currentBrushTool = currentBrushTool.copy(size = manualEditingProcessor.calculateOptimalBrushSize(fg.width, fg.height))
            isManualEditMode = true
            
            // Clear stacks when entering new edit session to avoid state confusion
            undoStack.clear()
            redoStack.clear()
            updateUndoRedoState()
        }
    }

    fun exitManualEditMode(apply: Boolean) {
        viewModelScope.launch {
            if (apply) {
                // Save current state to undo stack BEFORE applying final changes
                saveToUndoStack()
                
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
            } else {
                // Cancel - restore the state from when we entered manual edit mode
                val savedFg = savedForegroundBeforeManualEdit
                val savedDisplay = savedDisplayBitmapBeforeManualEdit
                val savedBg = savedBackgroundBeforeManualEdit

                if (savedFg != null && savedDisplay != null && savedBg != null) {
                    foregroundBitmap = savedFg
                    editorState = EditorState.Success(savedDisplay)
                    currentBackground = savedBg
                }
            }

            isManualEditMode = false
            brushStrokes.clear()
            editableMask = null

            // Clear saved state
            savedForegroundBeforeManualEdit = null
            savedDisplayBitmapBeforeManualEdit = null
            savedBackgroundBeforeManualEdit = null
        }
    }

    fun addBrushStroke(path: DrawingPath) {
        brushStrokes.add(path)
        applyPendingStrokes()
    }

    private fun applyPendingStrokes() {
        if (brushStrokes.isEmpty()) return
        
        // Make a copy of strokes to process
        val strokesToApply = brushStrokes.toList()
        
        viewModelScope.launch {
            val mask = editableMask ?: return@launch
            val orig = originalBitmap ?: return@launch

            // Heavy bitmap processing on background thread
            val (updatedMask, editedFg, result) = withContext(Dispatchers.Default) {
                // Apply strokes to the mask
                val newMask = manualEditingProcessor.applyBrushStrokes(
                    mask, 
                    strokesToApply, 
                    mask.width, 
                    mask.height
                )

                // Apply the new mask to the original image to get the new foreground
                val newFg = manualEditingProcessor.applyEditedMask(orig, newMask)

                // Compose final image for preview
                val finalResult = imageProcessor.composeFinalImage(
                    newFg, 
                    currentBackground, 
                    orig.width, 
                    orig.height, 
                    orig
                )
                
                Triple(newMask, newFg, finalResult)
            }
            
            // Update state on Main thread
            editableMask = updatedMask
            foregroundBitmap = editedFg
            editorState = EditorState.Success(result)
            
            // Clear only the strokes we just processed
            brushStrokes.clear()
        }
    }

    fun clearBrushStrokes() {
        viewModelScope.launch {
            // Clear means reset to the state when we entered manual edit mode
            val savedFg = savedForegroundBeforeManualEdit
            val savedDisplay = savedDisplayBitmapBeforeManualEdit

            if (savedFg != null && savedDisplay != null) {
                // Restore the saved foreground and display
                foregroundBitmap = savedFg
                editorState = EditorState.Success(savedDisplay)

                // Recreate mask from the original foreground
                editableMask = manualEditingProcessor.createMaskFromForeground(savedFg)
            }

            // Clear the strokes list
            brushStrokes.clear()
            
            // Clear undo/redo since we're resetting
            undoStack.clear()
            redoStack.clear()
            updateUndoRedoState()
        }
    }
    
    // Helper data class for Undo/Redo
    data class UndoRedoState(val foregroundBitmap: Bitmap, val maskBitmap: Bitmap)

    fun updateBrushTool(mode: BrushMode?, size: Float?, hardness: Float?, opacity: Float?) {
        currentBrushTool = currentBrushTool.copy(
            mode = mode ?: currentBrushTool.mode,
            size = size ?: currentBrushTool.size,
            hardness = hardness ?: currentBrushTool.hardness,
            opacity = opacity ?: currentBrushTool.opacity
        )
    }

    fun smoothMask() { 
        saveToUndoStack()
        viewModelScope.launch {
            val mask = editableMask ?: return@launch
            val orig = originalBitmap ?: return@launch
            
            // TODO: Implement actual smoothing logic in ManualEditingProcessor
            // For now, we'll just pretend to update to trigger a refresh
            val result = imageProcessor.composeFinalImage(foregroundBitmap!!, currentBackground, orig.width, orig.height, orig)
            editorState = EditorState.Success(result)
        }
    }
    
    fun reset() {
        viewModelScope.launch {
            originalBitmap = null
            foregroundBitmap = null
            maskBitmap = null
            editableMask = null
            currentBackground = BackgroundType.Transparent
            editorState = EditorState.Idle
            undoStack.clear()
            redoStack.clear()
            updateUndoRedoState()
        }
    }
}