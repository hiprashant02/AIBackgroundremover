// ... imports ...
package com.remover.background.AI.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.remover.background.AI.ml.BackgroundRemovalProcessor

import com.remover.background.AI.model.BackgroundType
import com.remover.background.AI.model.BrushMode
import com.remover.background.AI.model.BrushTool
import com.remover.background.AI.model.DrawingPath
import com.remover.background.AI.utils.FileManager
import com.remover.background.AI.utils.ImageProcessor
import com.remover.background.AI.utils.ManualEditingProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.InputStream

// ... EditorState and UndoRedoState classes ...
sealed class EditorState {
    object Idle : EditorState()
    object Loading : EditorState()
    data class Success(val bitmap: Bitmap) : EditorState()
    data class Error(val message: String) : EditorState()
}

data class UndoRedoState(val foregroundBitmap: Bitmap, val maskBitmap: Bitmap)

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
    
    // Subject Transform State
    var subjectScale by mutableStateOf(1f)
        private set
    var subjectPosition by mutableStateOf(Offset.Zero)
        private set
    var subjectRotation by mutableStateOf(0f)
        private set
    var displayScaleFactor by mutableFloatStateOf(1f)

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
    private val strokeMutex = Mutex() // Ensure strokes are processed sequentially

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
            // Use fast MLKit by default for instant results
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
    private var backgroundJob: Job? = null

    private fun applyBackgroundToForeground(background: BackgroundType) {
        backgroundJob?.cancel()
        backgroundJob = viewModelScope.launch {
            try {
                val orig = originalBitmap ?: return@launch
                val fg = foregroundBitmap ?: return@launch
                val result = imageProcessor.composeFinalImage(
                    fg, 
                    background, 
                    orig.width, 
                    orig.height, 
                    orig,
                    subjectPosition.x,
                    subjectPosition.y,
                    subjectScale,
                    subjectRotation
                )
                currentBackground = background
                editorState = EditorState.Success(result)
            } catch (e: Exception) {
                // Handle error or cancellation
            }
        }
    }

    fun setCustomImageBackground(imageBitmap: Bitmap) {
        val customBackground = BackgroundType.CustomImage(imageBitmap)
        applyBackgroundToForeground(customBackground)
    }

    fun applyBackground(background: BackgroundType) { applyBackgroundToForeground(background) }
    

    
    fun saveBitmap(format: Bitmap.CompressFormat, onComplete: (Result<Uri>) -> Unit) {
        viewModelScope.launch {
            isSaving = true
            try {
                val currentFg = foregroundBitmap
                val currentBg = currentBackground
                val orig = originalBitmap

                if (currentFg != null && orig != null) {
                    // Recompose final image with latest transform to ensure WYSIWYG
                    val finalBitmap = withContext(Dispatchers.Default) {
                        imageProcessor.composeFinalImage(
                            foreground = currentFg,
                            background = currentBg,
                            originalWidth = orig.width,
                            originalHeight = orig.height,
                            originalBitmap = orig,
                            subjectX = subjectPosition.x,
                            subjectY = subjectPosition.y,
                            subjectScale = subjectScale,
                            subjectRotation = subjectRotation
                        )
                    }

                    val fileName = "edited_${System.currentTimeMillis()}.png"
                    val result = fileManager?.saveBitmapToGallery(finalBitmap, fileName, format)
                    
                    if (result != null) {
                        onComplete(result)
                    } else {
                        onComplete(Result.failure(Exception("FileManager not initialized")))
                    }
                } else {
                    // Fallback to existing state if something is missing
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
                }
            } catch (e: Exception) {
                onComplete(Result.failure(e))
            } finally {
                isSaving = false
            }
        }
    }
    
    fun applyStrokes() { 
        viewModelScope.launch {
            applyPendingStrokes()
        }
    }

    fun updateSubjectTransform(pan: Offset, zoom: Float, rotation: Float) {
        subjectScale = (subjectScale * zoom).coerceIn(0.1f, 5f)
        // Convert screen pan to bitmap pan
        subjectPosition += pan / displayScaleFactor
        subjectRotation += rotation
    }

    fun isSubjectHit(contentPoint: Offset): Boolean {
        val fg = foregroundBitmap ?: return false
        val width = fg.width.toFloat()
        val height = fg.height.toFloat()
        val cx = width / 2f
        val cy = height / 2f
        
        // Inverse Transform (Content -> Subject Local)
        // Convert content point (screen) to bitmap coords
        val bitmapPoint = contentPoint / displayScaleFactor

        // 1. Untranslate
        val tx = bitmapPoint.x - subjectPosition.x
        val ty = bitmapPoint.y - subjectPosition.y
        
        // 2. Unrotate
        val rad = Math.toRadians(-subjectRotation.toDouble())
        val cos = Math.cos(rad)
        val sin = Math.sin(rad)
        val dx = tx - cx
        val dy = ty - cy
        val rx = cx + (dx * cos - dy * sin).toFloat()
        val ry = cy + (dx * sin + dy * cos).toFloat()
        
        // 3. Unscale
        val sx = cx + (rx - cx) / subjectScale
        val sy = cy + (ry - cy) / subjectScale
        
        // Check bounds
        return sx in 0f..width && sy in 0f..height
    }

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
                orig,
                subjectPosition.x,
                subjectPosition.y,
                subjectScale,
                subjectRotation
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
            
            // Force update of editorState.bitmap with current transform
            // This ensures the DrawingCanvas shows the subject in the correct (moved) position
            val orig = originalBitmap
            if (orig != null) {
                 val result = imageProcessor.composeFinalImage(
                    fg,
                    currentBackground,
                    orig.width,
                    orig.height,
                    orig,
                    subjectPosition.x,
                    subjectPosition.y,
                    subjectScale,
                    subjectRotation
                )
                editorState = EditorState.Success(result)
            }

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
                    val result = imageProcessor.composeFinalImage(
                    editedFg, 
                    currentBackground, 
                    orig.width, 
                    orig.height, 
                    orig,
                    subjectPosition.x,
                    subjectPosition.y,
                    subjectScale,
                    subjectRotation
                )
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
        viewModelScope.launch {
            // Transform path if subject is moved/scaled/rotated
            val transformedPath = if (subjectScale != 1f || subjectPosition != Offset.Zero || subjectRotation != 0f) {
                val fg = foregroundBitmap
                if (fg != null) {
                    val width = fg.width.toFloat()
                    val height = fg.height.toFloat()
                    val cx = width / 2f
                    val cy = height / 2f
                    
                    // Precompute rotation math
                    val rad = Math.toRadians(-subjectRotation.toDouble())
                    val cos = Math.cos(rad)
                    val sin = Math.sin(rad)

                    val newPoints = path.points.map { point ->
                        // 0. Denormalize (Normalized -> Pixels)
                        val px = point.x * width
                        val py = point.y * height

                        // 1. Untranslate
                        val tx = px - subjectPosition.x
                        val ty = py - subjectPosition.y
                        
                        // 2. Unrotate around (cx, cy)
                        val dx = tx - cx
                        val dy = ty - cy
                        val rx = cx + (dx * cos - dy * sin).toFloat()
                        val ry = cy + (dx * sin + dy * cos).toFloat()
                        
                        // 3. Unscale around center
                        val sx = cx + (rx - cx) / subjectScale
                        val sy = cy + (ry - cy) / subjectScale
                        
                        // 4. Renormalize (Pixels -> Normalized)
                        point.copy(x = sx / width, y = sy / height)
                    }
                    path.copy(points = newPoints)
                } else path
            } else path

            // Add the stroke
            brushStrokes.add(transformedPath)
            
            // Apply and WAIT for it to complete before allowing next stroke
            applyPendingStrokes()
        }
    }

    private suspend fun applyPendingStrokes() {
        // Use mutex to ensure strokes are processed sequentially, not skipped
        strokeMutex.withLock {
            if (brushStrokes.isEmpty()) return@withLock
            
            // Save current state to undo stack BEFORE applying changes
            saveToUndoStack()
            
            // Make a copy of strokes to process
            val strokesToApply = brushStrokes.toList()
            brushStrokes.clear() // Clear immediately to avoid race conditions with new strokes
            
            val mask = editableMask ?: return@withLock
            val orig = originalBitmap ?: return@withLock

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
                    orig,
                    subjectPosition.x,
                    subjectPosition.y,
                    subjectScale,
                    subjectRotation
                )
                
                Triple(newMask, newFg, finalResult)
            }
            
            // Update state on Main thread
            editableMask = updatedMask
            foregroundBitmap = editedFg
            editorState = EditorState.Success(result)
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
            val result = imageProcessor.composeFinalImage(
                foregroundBitmap!!, 
                currentBackground, 
                orig.width, 
                orig.height, 
                orig,
                subjectPosition.x,
                subjectPosition.y,
                subjectScale,
                subjectRotation
            )
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