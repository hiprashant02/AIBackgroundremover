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
import com.remover.background.AI.model.BrushPreset
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

data class StrokeStats(
    val totalStrokes: Int,
    val eraseStrokes: Int,
    val restoreStrokes: Int,
    val totalPoints: Int
)

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

    // Advanced brush features
    private val strokeHistory = mutableListOf<DrawingPath>()
    private val undoneStrokes = mutableListOf<DrawingPath>()
    var canUndoStroke by mutableStateOf(false)
        private set
    var canRedoStroke by mutableStateOf(false)
        private set
    var strokeCount by mutableStateOf(0)
        private set

    // Brush presets
    val brushPresets = listOf(
        BrushPreset("Detail", "Fine details & edges", BrushTool.PRESET_DETAIL),
        BrushPreset("Soft", "Soft gradual removal", BrushTool.PRESET_SOFT),
        BrushPreset("Hair", "Hair & fur restoration", BrushTool.PRESET_HAIR),
        BrushPreset("Hard", "Sharp precise cuts", BrushTool.PRESET_HARD),
        BrushPreset("Eraser", "Large area removal", BrushTool.PRESET_ERASER)
    )

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

    fun undo() {
        // Undo not implemented for main editor (only for strokes in manual mode)
    }

    fun redo() {
        // Redo not implemented for main editor (only for strokes in manual mode)
    }

    fun saveBitmap(format: Bitmap.CompressFormat, onComplete: (Result<Uri>) -> Unit) {
        viewModelScope.launch {
            if (editorState !is EditorState.Success) {
                onComplete(Result.failure(Exception("No image to save")))
                return@launch
            }

            isSaving = true
            try {
                val bitmap = (editorState as EditorState.Success).bitmap
                val fileName = "BG_Removed_${System.currentTimeMillis()}"
                val result = fileManager?.saveBitmapToGallery(bitmap, fileName, format, 100)
                if (result != null) {
                    onComplete(result)
                } else {
                    onComplete(Result.failure(Exception("Failed to save image")))
                }
            } catch (e: Exception) {
                onComplete(Result.failure(e))
            } finally {
                isSaving = false
            }
        }
    }

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
            strokeHistory.clear()
            undoneStrokes.clear()
            updateStrokeState()
        }
    }

    /**
     * Add a brush stroke with immediate processing for better feedback
     * FIX: Process immediately to avoid red/green preview confusion
     */
    private var pendingProcessingJob: kotlinx.coroutines.Job? = null

    fun addBrushStroke(path: DrawingPath) {
        brushStrokes.add(path)
        strokeHistory.add(path)
        undoneStrokes.clear()
        updateStrokeState()

        // Cancel any pending processing
        pendingProcessingJob?.cancel()

        // Process immediately for instant feedback
        pendingProcessingJob = viewModelScope.launch {
            applyPendingStrokes()
        }
    }

    /**
     * Force apply all pending strokes immediately (called by Apply button)
     */
    fun applyStrokes() {
        pendingProcessingJob?.cancel()
        pendingProcessingJob = viewModelScope.launch {
            applyPendingStrokes()
        }
    }

    /**
     * Apply all pending brush strokes immediately
     * OPTIMIZED: Processes in background thread for smooth performance
     */
    private suspend fun applyPendingStrokes() {
        if (brushStrokes.isEmpty()) return

        // We use the outer job (pendingProcessingJob) to handle cancellation.
        // If cancelled, execution stops here and brushStrokes are NOT cleared,
        // allowing the next job to process them.

        val mask = editableMask ?: return
        val orig = originalBitmap ?: return

        try {
            // Process all pending strokes
            val updatedMask = manualEditingProcessor.applyBrushStrokes(
                mask,
                brushStrokes.toList(),
                mask.width,
                mask.height
            )

            // If we reached here, processing was successful and not cancelled
            editableMask = updatedMask

            // Update preview
            val editedFg = manualEditingProcessor.applyEditedMask(orig, updatedMask)
            val result = imageProcessor.composeFinalImage(
                editedFg,
                currentBackground,
                orig.width,
                orig.height,
                orig
            )

            editorState = EditorState.Success(result)

            // Safely clear strokes now that they are applied
            brushStrokes.clear()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            editorState = EditorState.Error("Error applying strokes: ${e.message}")
        }
    }

    /**
     * Clear all brush strokes and reset mask
     */
    fun clearBrushStrokes() {
        pendingProcessingJob?.cancel()
        brushStrokes.clear()
        strokeHistory.clear()
        undoneStrokes.clear()
        updateStrokeState()

        viewModelScope.launch {
            val fg = foregroundBitmap ?: return@launch
            val orig = originalBitmap ?: return@launch

            try {
                // Reset mask to original from foreground
                editableMask = manualEditingProcessor.createMaskFromForeground(fg)

                // Refresh display
                val result = imageProcessor.composeFinalImage(
                    fg,
                    currentBackground,
                    orig.width,
                    orig.height,
                    orig
                )
                editorState = EditorState.Success(result)
            } catch (e: Exception) {
                editorState = EditorState.Error("Error clearing strokes: ${e.message}")
            }
        }
    }

    fun updateBrushTool(mode: BrushMode? = null, size: Float? = null, hardness: Float? = null, opacity: Float? = null) {
        currentBrushTool = currentBrushTool.copy(
            mode = mode ?: currentBrushTool.mode,
            size = size ?: currentBrushTool.size,
            hardness = hardness ?: currentBrushTool.hardness,
            opacity = opacity ?: currentBrushTool.opacity
        )
    }

    /**
     * Load brush preset
     */
    fun loadBrushPreset(preset: BrushPreset) {
        currentBrushTool = preset.brushTool
    }

    /**
     * Undo last stroke
     */
    fun undoStroke() {
        if (strokeHistory.isEmpty()) return

        pendingProcessingJob?.cancel()

        val lastStroke = strokeHistory.removeAt(strokeHistory.lastIndex)
        undoneStrokes.add(lastStroke)
        updateStrokeState()

        // Reapply all remaining strokes
        viewModelScope.launch {
            val fg = foregroundBitmap ?: return@launch
            val orig = originalBitmap ?: return@launch

            try {
                // Reset to original mask
                editableMask = manualEditingProcessor.createMaskFromForeground(fg)

                // Reapply remaining strokes
                if (strokeHistory.isNotEmpty()) {
                    val updatedMask = manualEditingProcessor.applyBrushStrokes(
                        editableMask!!,
                        strokeHistory.toList(),
                        editableMask!!.width,
                        editableMask!!.height
                    )
                    editableMask = updatedMask
                }

                // Update display
                val editedFg = manualEditingProcessor.applyEditedMask(orig, editableMask!!)
                val result = imageProcessor.composeFinalImage(
                    editedFg,
                    currentBackground,
                    orig.width,
                    orig.height,
                    orig
                )
                editorState = EditorState.Success(result)
            } catch (e: Exception) {
                editorState = EditorState.Error("Error undoing stroke: ${e.message}")
            }
        }
    }

    /**
     * Redo undone stroke
     */
    fun redoStroke() {
        if (undoneStrokes.isEmpty()) return

        pendingProcessingJob?.cancel()

        val strokeToRedo = undoneStrokes.removeAt(undoneStrokes.lastIndex)
        strokeHistory.add(strokeToRedo)
        brushStrokes.add(strokeToRedo)
        updateStrokeState()

        // Apply the redone stroke
        pendingProcessingJob = viewModelScope.launch {
            applyPendingStrokes()
        }
    }

    /**
     * Update stroke state indicators
     */
    private fun updateStrokeState() {
        canUndoStroke = strokeHistory.isNotEmpty()
        canRedoStroke = undoneStrokes.isNotEmpty()
        strokeCount = strokeHistory.size
    }

    /**
     * Get stroke statistics
     */
    fun getStrokeStats(): StrokeStats {
        val eraseCount = strokeHistory.count { it.brushTool.mode == BrushMode.ERASE }
        val restoreCount = strokeHistory.count { it.brushTool.mode == BrushMode.RESTORE }
        val totalPoints = strokeHistory.sumOf { it.points.size }

        return StrokeStats(
            totalStrokes = strokeHistory.size,
            eraseStrokes = eraseCount,
            restoreStrokes = restoreCount,
            totalPoints = totalPoints
        )
    }

    /**
     * Toggle between erase and restore modes
     */
    fun toggleBrushMode() {
        currentBrushTool = currentBrushTool.copy(
            mode = if (currentBrushTool.mode == BrushMode.ERASE) BrushMode.RESTORE else BrushMode.ERASE
        )
    }

    /**
     * Adjust brush size by percentage
     */
    fun adjustBrushSize(percentage: Float) {
        val newSize = (currentBrushTool.size * (1 + percentage)).coerceIn(
            BrushTool.MIN_SIZE,
            BrushTool.MAX_SIZE
        )
        currentBrushTool = currentBrushTool.copy(size = newSize)
    }

    /**
     * Reset brush to default
     */
    fun resetBrush() {
        currentBrushTool = BrushTool()
    }

    /**
     * Smooth the mask edges
     */
    fun smoothMask() {
        viewModelScope.launch {
            val mask = editableMask ?: return@launch
            val orig = originalBitmap ?: return@launch

            try {
                isProcessing = true

                // Smooth the mask
                val smoothedMask = manualEditingProcessor.smoothMask(mask)
                editableMask = smoothedMask

                // Update display
                val editedFg = manualEditingProcessor.applyEditedMask(orig, smoothedMask)
                val result = imageProcessor.composeFinalImage(
                    editedFg,
                    currentBackground,
                    orig.width,
                    orig.height,
                    orig
                )
                editorState = EditorState.Success(result)
            } catch (e: Exception) {
                editorState = EditorState.Error("Error smoothing mask: ${e.message}")
            } finally {
                isProcessing = false
            }
        }
    }

    fun reset() {
        editorState = EditorState.Idle
        originalBitmap = null
        foregroundBitmap = null
        maskBitmap = null
        currentBackground = BackgroundType.Transparent
        isProcessing = false
    }
}