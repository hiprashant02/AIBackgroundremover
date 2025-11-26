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

    // Use ML Kit's direct foreground bitmap (recommended)
    var useDirectForeground by mutableStateOf(true)
        private set

    // Manual editing state
    var isManualEditMode by mutableStateOf(false)
        private set

    var currentBrushTool by mutableStateOf(BrushTool())
        private set

    var editableMask by mutableStateOf<Bitmap?>(null)
        private set

    private val brushStrokes = mutableListOf<DrawingPath>()

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
            if (useDirectForeground) {
                // RECOMMENDED: Use ML Kit's foreground bitmap directly (most accurate)
                val foregroundResult = processor?.removeBackground(bitmap)

                if (foregroundResult?.isSuccess == true) {
                    foregroundBitmap = foregroundResult.getOrNull()

                    // Apply transparent background by default
                    applyBackgroundToForeground(BackgroundType.Transparent)
                } else {
                    editorState = EditorState.Error("Failed to process background: ${foregroundResult?.exceptionOrNull()?.message}")
                }
            } else {
                // Alternative: Use mask-based approach with quality control
                val maskResult = processor?.getMask(bitmap, currentMaskQuality)

                if (maskResult?.isSuccess == true) {
                    maskBitmap = maskResult.getOrNull()

                    // Apply transparent background by default
                    applyBackground(BackgroundType.Transparent)
                } else {
                    editorState = EditorState.Error("Failed to process background: ${maskResult?.exceptionOrNull()?.message}")
                }
            }
        } catch (e: Exception) {
            editorState = EditorState.Error("Error processing: ${e.message}")
        } finally {
            isProcessing = false
        }
    }

    /**
     * Apply background using ML Kit's direct foreground bitmap (RECOMMENDED)
     */
    private fun applyBackgroundToForeground(background: BackgroundType) {
        viewModelScope.launch {
            isProcessing = true

            try {
                val original = originalBitmap
                val foreground = foregroundBitmap

                if (original == null || foreground == null) {
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

                val processedBitmap = imageProcessor.composeFinalImage(foreground, background, original.width, original.height, original)

                currentBackground = background
                editorState = EditorState.Success(processedBitmap)
            } catch (e: Exception) {
                editorState = EditorState.Error("Error applying background: ${e.message}")
            } finally {
                isProcessing = false
            }
        }
    }

    /**
     * Apply background using mask-based approach
     */
    fun applyBackground(background: BackgroundType) {
        if (useDirectForeground) {
            applyBackgroundToForeground(background)
            return
        }

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

    /**
     * Change mask quality and reprocess (only for mask-based approach)
     */
    fun changeMaskQuality(quality: MaskQuality) {
        if (useDirectForeground) return // Not applicable for direct foreground

        val original = originalBitmap ?: return

        viewModelScope.launch {
            isProcessing = true
            currentMaskQuality = quality

            try {
                val maskResult = processor?.getMask(original, quality)

                if (maskResult?.isSuccess == true) {
                    maskBitmap = maskResult.getOrNull()
                    applyBackground(currentBackground)
                } else {
                    editorState = EditorState.Error("Failed to reprocess: ${maskResult?.exceptionOrNull()?.message}")
                    isProcessing = false
                }
            } catch (e: Exception) {
                editorState = EditorState.Error("Error reprocessing: ${e.message}")
                isProcessing = false
            }
        }
    }

    /**
     * Toggle between direct foreground and mask-based approach
     */
    fun toggleProcessingMode() {
        val original = originalBitmap ?: return

        useDirectForeground = !useDirectForeground

        viewModelScope.launch {
            isProcessing = true
            processBackground(original)
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

            // Automatically determine best format based on transparency
            val hasTransparency = currentBackground is BackgroundType.Transparent || state.bitmap.hasAlpha()
            val bestFormat = if (hasTransparency) {
                Bitmap.CompressFormat.PNG  // Use PNG for transparency (lossless)
            } else {
                format  // Use requested format for opaque images
            }

            val extension = when (bestFormat) {
                Bitmap.CompressFormat.PNG -> "png"
                Bitmap.CompressFormat.JPEG -> "jpg"
                else -> "png"
            }

            val fileName = "BG_Removed_${System.currentTimeMillis()}.$extension"

            // Save with maximum quality (100 is handled in FileManager)
            val result = fileManager?.saveBitmapToGallery(state.bitmap, fileName, bestFormat)

            isSaving = false

            if (result != null) {
                onComplete(result)
            } else {
                onComplete(Result.failure(Exception("File manager not initialized")))
            }
        }
    }

    // ========== Manual Editing Functions ==========

    /**
     * Enter manual editing mode
     */
    fun enterManualEditMode() {
        viewModelScope.launch {
            isProcessing = true

            try {
                val foreground = foregroundBitmap
                if (foreground == null) {
                    editorState = EditorState.Error("No foreground available for editing")
                    isProcessing = false
                    return@launch
                }

                // Create editable mask from foreground
                editableMask = manualEditingProcessor.createMaskFromForeground(foreground)

                // Initialize brush size based on image dimensions
                val optimalSize = manualEditingProcessor.calculateOptimalBrushSize(
                    foreground.width,
                    foreground.height
                )
                currentBrushTool = currentBrushTool.copy(size = optimalSize)

                isManualEditMode = true
            } catch (e: Exception) {
                editorState = EditorState.Error("Error entering edit mode: ${e.message}")
            } finally {
                isProcessing = false
            }
        }
    }

    /**
     * Exit manual editing mode and apply changes
     */
    fun exitManualEditMode(applyChanges: Boolean = true) {
        viewModelScope.launch {
            if (applyChanges) {
                // Apply any pending strokes first
                if (brushStrokes.isNotEmpty()) {
                    applyPendingStrokes()
                }

                // Wait a bit for processing to complete
                kotlinx.coroutines.delay(100)

                val mask = editableMask

                if (mask != null) {
                    isProcessing = true

                    try {
                        val original = originalBitmap

                        if (original != null) {
                            // Save current state to undo
                            if (editorState is EditorState.Success) {
                                val currentBitmap = (editorState as EditorState.Success).bitmap
                                undoStack.add(UndoRedoState(
                                    currentBitmap.copy(currentBitmap.config ?: Bitmap.Config.ARGB_8888, true),
                                    currentBackground
                                ))
                                redoStack.clear()
                                updateUndoRedoState()
                            }

                            // Apply edited mask to original image
                            val editedForeground = manualEditingProcessor.applyEditedMask(original, mask)
                            foregroundBitmap = editedForeground

                            // Recompose with current background
                            val processedBitmap = imageProcessor.composeFinalImage(
                                editedForeground,
                                currentBackground,
                                original.width,
                                original.height,
                                original
                            )

                            editorState = EditorState.Success(processedBitmap)
                        }
                    } catch (e: Exception) {
                        editorState = EditorState.Error("Error applying edits: ${e.message}")
                    } finally {
                        isProcessing = false
                    }
                }
            }

            isManualEditMode = false
            brushStrokes.clear()
        }
    }

    /**
     * Add a brush stroke (optimized - batches strokes, applies on exit)
     */
    fun addBrushStroke(path: DrawingPath) {
        // Just add to list - don't process in real-time for better performance
        brushStrokes.add(path)

        // Apply strokes after collecting a few for better performance
        if (brushStrokes.size >= 3) {
            applyPendingStrokes()
        }
    }

    /**
     * Force apply all pending strokes (called by user action)
     */
    fun applyStrokes() {
        applyPendingStrokes()
    }

    /**
     * Apply all pending brush strokes (called periodically or on mode changes)
     */
    private fun applyPendingStrokes() {
        if (brushStrokes.isEmpty()) return

        viewModelScope.launch {
            val mask = editableMask
            val original = originalBitmap

            if (mask == null || original == null) return@launch

            isProcessing = true

            try {
                // Apply all strokes at once
                val updatedMask = manualEditingProcessor.applyBrushStrokes(
                    mask,
                    brushStrokes.toList(),
                    mask.width,
                    mask.height
                )

                editableMask = updatedMask

                // Update preview
                val editedForeground = manualEditingProcessor.applyEditedMask(original, updatedMask)
                val processedBitmap = imageProcessor.composeFinalImage(
                    editedForeground,
                    currentBackground,
                    original.width,
                    original.height,
                    original
                )

                editorState = EditorState.Success(processedBitmap)

                // Clear applied strokes
                brushStrokes.clear()
            } catch (e: Exception) {
                editorState = EditorState.Error("Error applying strokes: ${e.message}")
            } finally {
                isProcessing = false
            }
        }
    }

    /**
     * Update brush tool settings
     */
    fun updateBrushTool(
        mode: BrushMode? = null,
        size: Float? = null,
        hardness: Float? = null,
        opacity: Float? = null
    ) {
        currentBrushTool = currentBrushTool.copy(
            mode = mode ?: currentBrushTool.mode,
            size = size ?: currentBrushTool.size,
            hardness = hardness ?: currentBrushTool.hardness,
            opacity = opacity ?: currentBrushTool.opacity
        )
    }

    /**
     * Clear all brush strokes
     */
    fun clearBrushStrokes() {
        viewModelScope.launch {
            val foreground = foregroundBitmap ?: return@launch

            brushStrokes.clear()

            // Reset mask to original
            editableMask = manualEditingProcessor.createMaskFromForeground(foreground)

            // Update preview
            val original = originalBitmap
            if (original != null) {
                val processedBitmap = imageProcessor.composeFinalImage(
                    foreground,
                    currentBackground,
                    original.width,
                    original.height,
                    original
                )
                editorState = EditorState.Success(processedBitmap)
            }
        }
    }

    /**
     * Smooth mask edges for better quality
     */
    fun smoothMask() {
        viewModelScope.launch {
            val mask = editableMask ?: return@launch
            val original = originalBitmap ?: return@launch

            isProcessing = true

            try {
                val smoothedMask = manualEditingProcessor.smoothMask(mask, radius = 3)
                editableMask = smoothedMask

                // Update preview
                val editedForeground = manualEditingProcessor.applyEditedMask(original, smoothedMask)
                val processedBitmap = imageProcessor.composeFinalImage(
                    editedForeground,
                    currentBackground,
                    original.width,
                    original.height,
                    original
                )

                editorState = EditorState.Success(processedBitmap)
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
        undoStack.clear()
        redoStack.clear()
        updateUndoRedoState()
    }

    override fun onCleared() {
        super.onCleared()
        processor?.close()
        originalBitmap?.recycle()
        foregroundBitmap?.recycle()
        maskBitmap?.recycle()

        if (editorState is EditorState.Success) {
            (editorState as EditorState.Success).bitmap.recycle()
        }

        undoStack.forEach { it.bitmap.recycle() }
        redoStack.forEach { it.bitmap.recycle() }
    }
}

