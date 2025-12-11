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
import com.remover.background.AI.utils.InAppReviewManager
import com.remover.background.AI.utils.InterstitialAdManager
import android.app.Activity
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
    var blurredBackgroundBitmap by mutableStateOf<Bitmap?>(null)
        private set
    
    // Store original URI for full-resolution export
    private var originalImageUri: Uri? = null
    
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
    private var reviewManager: InAppReviewManager? = null
    private var interstitialAdManager: InterstitialAdManager? = null
    private var activityRef: Activity? = null
    private var appContext: Context? = null // Store context for full-res export
    private val manualEditingProcessor = ManualEditingProcessor()
    var useDirectForeground by mutableStateOf(true)
        private set
    var isManualEditMode by mutableStateOf(false)
        private set
    var currentBrushTool by mutableStateOf(BrushTool())
        private set
    var editableMask by mutableStateOf<Bitmap?>(null)
        private set
    var isBrushProcessing by mutableStateOf(false)
        private set
    private val brushStrokes = mutableListOf<DrawingPath>()
    private val strokeMutex = Mutex() // Ensure strokes are processed sequentially

    // State to restore when canceling manual edit mode
    private var savedForegroundBeforeManualEdit: Bitmap? = null
    private var savedDisplayBitmapBeforeManualEdit: Bitmap? = null
    private var savedBackgroundBeforeManualEdit: BackgroundType? = null

    // UI State (survives configuration changes)
    var showBgPicker by mutableStateOf(false)
        internal set
    var showSaveSheet by mutableStateOf(false)
        internal set
    var showSuccessDialog by mutableStateOf(false)
        internal set
    var showExitConfirmDialog by mutableStateOf(false)
        internal set

    // ... Standard Initialization and Load Logic (Unchanged) ...
    fun initialize(context: Context) {
        if (processor == null) {
            processor = BackgroundRemovalProcessor(context)
            fileManager = FileManager(context)
            reviewManager = InAppReviewManager(context)
            interstitialAdManager = InterstitialAdManager(context)
            appContext = context.applicationContext // Store for full-res export
            // Store activity reference if context is an Activity
            if (context is Activity) {
                activityRef = context
            }
            
            // Preload interstitial ad for better UX
            interstitialAdManager?.preloadAd()
        }
    }

    fun loadImage(context: Context, uri: Uri) {
        viewModelScope.launch {
            // Reset all states first to ensure clean slate for new session
            resetStatesSync()
            
            // Store original URI for full-resolution export
            originalImageUri = uri
            
            editorState = EditorState.Loading
            isProcessing = true
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    // First, get the image dimensions without loading full bitmap
                    val options = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream, null, options)
                    }
                    
                    // Validate dimensions were read successfully
                    if (options.outWidth <= 0 || options.outHeight <= 0) {
                        // Failed to read dimensions, try loading directly with a safe sample size
                        val fallbackOptions = BitmapFactory.Options().apply {
                            inSampleSize = 2 // Safe default
                            inPreferredConfig = Bitmap.Config.ARGB_8888
                        }
                        return@withContext context.contentResolver.openInputStream(uri)?.use { stream ->
                            BitmapFactory.decodeStream(stream, null, fallbackOptions)
                        }
                    }
                    
                    // Calculate sample size to avoid loading huge images into memory
                    val maxDimension = 4096
                    var sampleSize = 1
                    while (options.outWidth / sampleSize > maxDimension || 
                           options.outHeight / sampleSize > maxDimension) {
                        sampleSize *= 2
                    }
                    
                    // Now decode with the calculated sample size
                    val decodeOptions = BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                    
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream, null, decodeOptions)
                    }
                }
                
                if (bitmap == null) {
                    editorState = EditorState.Error("Failed to load image. Please try another image.")
                    return@launch
                }
                
                val resized = imageProcessor.resizeIfNeeded(bitmap)
                if (resized != bitmap) bitmap.recycle()
                originalBitmap = resized
                processBackground(resized)
            } catch (e: Exception) {
                editorState = EditorState.Error("Error loading image: ${e.message}")
            } finally {
                isProcessing = false
            }
        }
    }
    
    // Synchronous reset for use within coroutines
    private fun resetStatesSync() {
        // Reset image state
        originalBitmap = null
        foregroundBitmap = null
        maskBitmap = null
        editableMask = null
        currentBackground = BackgroundType.Transparent
        blurredBackgroundBitmap = null
        
        // Reset transform state
        subjectScale = 1f
        subjectPosition = Offset.Zero
        subjectRotation = 0f
        
        // Reset manual edit state
        isManualEditMode = false
        brushStrokes.clear()
        savedForegroundBeforeManualEdit = null
        savedDisplayBitmapBeforeManualEdit = null
        savedBackgroundBeforeManualEdit = null
        
        // Reset undo/redo
        undoStack.clear()
        redoStack.clear()
        updateUndoRedoState()
        
        // Reset UI states
        showBgPicker = false
        showSaveSheet = false
        showSuccessDialog = false
        showExitConfirmDialog = false
        
        // Reset processing flags
        isProcessing = false
        isSaving = false
    }

    private suspend fun processBackground(bitmap: Bitmap) {
        try {
            // Use fast MLKit by default for instant results
            val result = processor?.removeBackground(bitmap)
            if (result?.isSuccess == true) {
                val fg = result.getOrNull()
                if (fg != null) {
                    foregroundBitmap = fg
                    applyBackgroundToForeground(BackgroundType.Transparent)
                } else {
                    // ML Kit returned success but no bitmap - show original as fallback
                    foregroundBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                    applyBackgroundToForeground(BackgroundType.Transparent)
                }
            } else {
                // Background removal failed - show original image as fallback
                foregroundBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                applyBackgroundToForeground(BackgroundType.Original)
            }
        } catch(e: Exception) { 
            // On any error, show original image as fallback
            foregroundBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            applyBackgroundToForeground(BackgroundType.Original)
        }
    }

    // ... Background Application Logic (Unchanged) ...
    private var backgroundJob: Job? = null

    private fun applyBackgroundToForeground(background: BackgroundType) {
        backgroundJob?.cancel()
        backgroundJob = viewModelScope.launch {
            isProcessing = true
            try {
                val orig = originalBitmap
                val fg = foregroundBitmap
                
                // Handle null cases with proper error messages
                if (orig == null) {
                    editorState = EditorState.Error("Image not loaded. Please try again.")
                    isProcessing = false
                    return@launch
                }
                
                if (fg == null) {
                    // If no foreground, display original image
                    editorState = EditorState.Success(orig)
                    isProcessing = false
                    return@launch
                }
                
                val precomputedBlur = if (background is BackgroundType.Blur) {
                     withContext(Dispatchers.Default) {
                         imageProcessor.fastBlur(orig, background.intensity.toInt())
                     }
                } else null
                
                blurredBackgroundBitmap = precomputedBlur

                val result = imageProcessor.composeFinalImage(
                    fg, 
                    background, 
                    orig.width, 
                    orig.height, 
                    orig,
                    subjectPosition.x,
                    subjectPosition.y,
                    subjectScale,
                    subjectRotation,
                    precomputedBlur
                )
                currentBackground = background
                editorState = EditorState.Success(result)
            } catch (e: Exception) {
                // On error, try to show something instead of blank screen
                originalBitmap?.let { 
                    editorState = EditorState.Success(it) 
                } ?: run {
                    editorState = EditorState.Error("Failed to process image")
                }
            } finally {
                isProcessing = false
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
                val uri = originalImageUri
                val ctx = appContext

                if (currentFg != null && orig != null) {
                    val finalBitmap = withContext(Dispatchers.Default) {
                        // Try to load full-resolution original for export
                        var fullResOriginal: Bitmap? = null
                        if (uri != null && ctx != null) {
                            try {
                                ctx.contentResolver.openInputStream(uri)?.use { stream ->
                                    // Load at full resolution (no sampling)
                                    fullResOriginal = BitmapFactory.decodeStream(stream)
                                }
                            } catch (e: Exception) {
                                // Failed to load full-res, fall back to working resolution
                                fullResOriginal = null
                            }
                        }
                        
                        // If we have full-res original and it's larger than working version
                        if (fullResOriginal != null && 
                            (fullResOriginal!!.width > orig.width || fullResOriginal!!.height > orig.height)) {
                            
                            val fullWidth = fullResOriginal!!.width
                            val fullHeight = fullResOriginal!!.height
                            
                            // Scale up foreground to match full resolution
                            val scaledFg = Bitmap.createScaledBitmap(currentFg, fullWidth, fullHeight, true)
                            
                            // Calculate scale ratio for transform adjustment
                            val scaleRatio = fullWidth.toFloat() / orig.width.toFloat()
                            
                            // Load full-resolution custom background if applicable
                            var exportBackground = currentBg
                            if (currentBg is BackgroundType.CustomImage && currentBg.originalUri != null && ctx != null) {
                                try {
                                    ctx.contentResolver.openInputStream(currentBg.originalUri)?.use { bgStream ->
                                        val fullResBg = BitmapFactory.decodeStream(bgStream)
                                        if (fullResBg != null) {
                                            exportBackground = BackgroundType.CustomImage(fullResBg, currentBg.originalUri)
                                        }
                                    }
                                } catch (e: Exception) {
                                    // Failed to load full-res background, use existing
                                }
                            }
                            
                            val result = imageProcessor.composeFinalImage(
                                foreground = scaledFg,
                                background = exportBackground,
                                originalWidth = fullWidth,
                                originalHeight = fullHeight,
                                originalBitmap = fullResOriginal,
                                subjectX = subjectPosition.x * scaleRatio,
                                subjectY = subjectPosition.y * scaleRatio,
                                subjectScale = subjectScale,
                                subjectRotation = subjectRotation
                            )
                            
                            // Cleanup
                            scaledFg.recycle()
                            fullResOriginal!!.recycle()
                            if (exportBackground is BackgroundType.CustomImage && exportBackground != currentBg) {
                                (exportBackground as BackgroundType.CustomImage).bitmap.recycle()
                            }
                            
                            result
                        } else {
                            // Use working resolution (full-res not available or same size)
                            fullResOriginal?.recycle()
                            
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
                    }

                    val fileName = "edited_${System.currentTimeMillis()}.png"
                    val result = fileManager?.saveBitmapToGallery(finalBitmap, fileName, format)
                    
                    // Recycle the final bitmap after saving
                    finalBitmap.recycle()
                    
                    if (result != null) {
                        onComplete(result)
                        
                        // Show interstitial ad and request review after successful save
                        if (result.isSuccess) {
                            activityRef?.let { activity ->
                                // Show interstitial ad first
                                interstitialAdManager?.showAd(activity) {
                                    // After ad is dismissed (or not shown), request review
                                    viewModelScope.launch {
                                        reviewManager?.requestReviewIfEligible(activity)
                                    }
                                }
                            }
                        }
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
                            
                            // Show interstitial ad and request review after successful save
                            if (result.isSuccess) {
                                activityRef?.let { activity ->
                                    interstitialAdManager?.showAd(activity) {
                                        viewModelScope.launch {
                                            reviewManager?.requestReviewIfEligible(activity)
                                        }
                                    }
                                }
                            }
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
            hasPendingUndoSave = false
            strokeApplyJob?.cancel()

            // Clear saved state
            savedForegroundBeforeManualEdit = null
            savedDisplayBitmapBeforeManualEdit = null
            savedBackgroundBeforeManualEdit = null
        }
    }

    // Debounce job for applying strokes
    private var strokeApplyJob: Job? = null
    private var lastStrokeTime = 0L
    private var hasPendingUndoSave = false
    
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

            // Save undo state ONCE before first stroke in a drawing session
            if (!hasPendingUndoSave) {
                saveToUndoStack()
                hasPendingUndoSave = true
            }

            // Add the stroke to pending list
            brushStrokes.add(transformedPath)
            lastStrokeTime = System.currentTimeMillis()
            
            // Cancel previous debounce job
            strokeApplyJob?.cancel()
            
            // Apply strokes with debouncing - wait 100ms after last stroke before processing
            strokeApplyJob = viewModelScope.launch {
                kotlinx.coroutines.delay(100)
                applyPendingStrokesNonBlocking()
            }
        }
    }

    private suspend fun applyPendingStrokesNonBlocking() {
        // Use mutex to ensure strokes are processed sequentially
        strokeMutex.withLock {
            if (brushStrokes.isEmpty()) return@withLock
            
            // Show processing indicator
            isBrushProcessing = true
            
            // Make a copy of strokes to process
            val strokesToApply = brushStrokes.toList()
            brushStrokes.clear()
            
            val mask = editableMask ?: run {
                isBrushProcessing = false
                return@withLock
            }
            val orig = originalBitmap ?: run {
                isBrushProcessing = false
                return@withLock
            }

            try {
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
            } finally {
                // Always hide processing indicator
                isBrushProcessing = false
            }
        }
    }

    private suspend fun applyPendingStrokes() {
        // Legacy function - just calls the non-blocking version
        applyPendingStrokesNonBlocking()
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
            resetStatesSync()
            editorState = EditorState.Idle
        }
    }
}