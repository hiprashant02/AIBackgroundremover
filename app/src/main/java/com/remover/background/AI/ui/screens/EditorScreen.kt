//package com.remover.background.AI.ui.screens
//
//import android.graphics.Bitmap
//import android.widget.Toast
//import androidx.compose.animation.*
//import androidx.compose.animation.core.*
//import androidx.compose.foundation.Image
//import androidx.compose.foundation.background
//import androidx.compose.foundation.border
//import androidx.compose.foundation.clickable
//import androidx.compose.foundation.layout.*
//import androidx.compose.foundation.lazy.LazyRow
//import androidx.compose.foundation.lazy.items
//import androidx.compose.foundation.shape.CircleShape
//import androidx.compose.foundation.shape.RoundedCornerShape
//import androidx.compose.material.icons.Icons
//import androidx.compose.material.icons.filled.*
//import androidx.compose.material3.*
//import androidx.compose.runtime.*
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.draw.clip
//import androidx.compose.ui.graphics.Brush
//import androidx.compose.ui.graphics.Color
//import androidx.compose.ui.graphics.asImageBitmap
//import androidx.compose.ui.layout.ContentScale
//import androidx.compose.ui.platform.LocalContext
//import androidx.compose.ui.text.font.FontWeight
//import androidx.compose.ui.unit.dp
//import com.remover.background.AI.model.BackgroundType
//import com.remover.background.AI.model.BrushMode
//import com.remover.background.AI.ui.components.BrushControlPanel
//import com.remover.background.AI.ui.components.DrawingCanvas
//import com.remover.background.AI.ui.theme.*
//import com.remover.background.AI.viewmodel.EditorState
//import com.remover.background.AI.viewmodel.EditorViewModel
//
//@OptIn(ExperimentalMaterial3Api::class)
//@Composable
//fun EditorScreen(
//    viewModel: EditorViewModel,
//    onBackClick: () -> Unit
//) {
//    val context = LocalContext.current
//    val editorState = viewModel.editorState
//    val isProcessing = viewModel.isProcessing
//    val isSaving = viewModel.isSaving
//    val currentBackground = viewModel.currentBackground
//    val canUndo = viewModel.canUndo
//    val canRedo = viewModel.canRedo
//    val isManualEditMode = viewModel.isManualEditMode
//    val currentBrushTool = viewModel.currentBrushTool
//
//    var showBackgroundPicker by remember { mutableStateOf(false) }
//    var showSaveDialog by remember { mutableStateOf(false) }
//
//    Scaffold(
//        topBar = {
//            TopAppBar(
//                title = {
//                    Text(
//                        "Edit Image",
//                        fontWeight = FontWeight.Bold
//                    )
//                },
//                navigationIcon = {
//                    IconButton(onClick = onBackClick) {
//                        Icon(Icons.Default.ArrowBack, "Back")
//                    }
//                },
//                actions = {
//                    // Undo
//                    IconButton(
//                        onClick = { viewModel.undo() },
//                        enabled = canUndo && !isProcessing
//                    ) {
//                        Icon(
//                            Icons.Default.Undo,
//                            "Undo",
//                            tint = if (canUndo && !isProcessing)
//                                MaterialTheme.colorScheme.onSurface
//                            else
//                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
//                        )
//                    }
//
//                    // Redo
//                    IconButton(
//                        onClick = { viewModel.redo() },
//                        enabled = canRedo && !isProcessing
//                    ) {
//                        Icon(
//                            Icons.Default.Redo,
//                            "Redo",
//                            tint = if (canRedo && !isProcessing)
//                                MaterialTheme.colorScheme.onSurface
//                            else
//                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
//                        )
//                    }
//
//                    // Manual Edit
//                    IconButton(
//                        onClick = {
//                            if (isManualEditMode) {
//                                viewModel.exitManualEditMode(applyChanges = true)
//                            } else {
//                                viewModel.enterManualEditMode()
//                            }
//                        },
//                        enabled = editorState is EditorState.Success && !isProcessing
//                    ) {
//                        Icon(
//                            if (isManualEditMode) Icons.Default.Check else Icons.Default.Edit,
//                            if (isManualEditMode) "Apply Edits" else "Manual Edit",
//                            tint = if (editorState is EditorState.Success && !isProcessing) {
//                                if (isManualEditMode) Color.Green else MaterialTheme.colorScheme.onSurface
//                            } else {
//                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
//                            }
//                        )
//                    }
//
//                    // Save
//                    IconButton(
//                        onClick = { showSaveDialog = true },
//                        enabled = editorState is EditorState.Success && !isSaving && !isManualEditMode
//                    ) {
//                        if (isSaving) {
//                            CircularProgressIndicator(
//                                modifier = Modifier.size(24.dp),
//                                strokeWidth = 2.dp
//                            )
//                        } else {
//                            Icon(
//                                Icons.Default.Save,
//                                "Save",
//                                tint = if (editorState is EditorState.Success)
//                                    MaterialTheme.colorScheme.onSurface
//                                else
//                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
//                            )
//                        }
//                    }
//                },
//                colors = TopAppBarDefaults.topAppBarColors(
//                    containerColor = MaterialTheme.colorScheme.surface
//                )
//            )
//        },
//        bottomBar = {
//            if (editorState is EditorState.Success) {
//                if (isManualEditMode) {
//                    // Show brush control panel in manual edit mode
//                    BrushControlPanel(
//                        brushTool = currentBrushTool,
//                        onBrushToolChange = { newTool ->
//                            viewModel.updateBrushTool(
//                                mode = newTool.mode,
//                                size = newTool.size,
//                                hardness = newTool.hardness,
//                                opacity = newTool.opacity
//                            )
//                        },
//                        onClearStrokes = { viewModel.clearBrushStrokes() },
//                        onSmoothMask = { viewModel.smoothMask() },
//                        onApplyStrokes = { viewModel.applyStrokes() },
//                        onDone = { viewModel.exitManualEditMode(applyChanges = true) },
//                        onCancel = { viewModel.exitManualEditMode(applyChanges = false) }
//                    )
//                } else {
//                    // Show background picker button normally
//                    BottomAppBar(
//                        containerColor = MaterialTheme.colorScheme.surface,
//                        contentPadding = PaddingValues(16.dp)
//                    ) {
//                        Button(
//                            onClick = { showBackgroundPicker = true },
//                            modifier = Modifier.fillMaxWidth(),
//                            enabled = !isProcessing,
//                            colors = ButtonDefaults.buttonColors(
//                                containerColor = Primary
//                            ),
//                            shape = RoundedCornerShape(12.dp)
//                        ) {
//                            Icon(Icons.Default.ColorLens, null)
//                            Spacer(Modifier.width(8.dp))
//                            Text("Change Background")
//                        }
//                    }
//                }
//            }
//        }
//    ) { padding ->
//        Box(
//            modifier = Modifier
//                .fillMaxSize()
//                .padding(padding)
//        ) {
//            when (editorState) {
//                is EditorState.Idle -> {
//                    // This shouldn't happen in editor screen
//                    Box(
//                        modifier = Modifier.fillMaxSize(),
//                        contentAlignment = Alignment.Center
//                    ) {
//                        Text("No image loaded")
//                    }
//                }
//
//                is EditorState.Loading, EditorState.Idle -> {
//                    Box(
//                        modifier = Modifier.fillMaxSize(),
//                        contentAlignment = Alignment.Center
//                    ) {
//                        Column(
//                            horizontalAlignment = Alignment.CenterHorizontally,
//                            verticalArrangement = Arrangement.spacedBy(16.dp)
//                        ) {
//                            CircularProgressIndicator(
//                                modifier = Modifier.size(64.dp),
//                                color = Primary,
//                                strokeWidth = 6.dp
//                            )
//                            Text(
//                                "Processing image with AI...",
//                                style = MaterialTheme.typography.titleMedium,
//                                color = MaterialTheme.colorScheme.onSurface
//                            )
//                            Text(
//                                "This may take a few seconds",
//                                style = MaterialTheme.typography.bodyMedium,
//                                color = MaterialTheme.colorScheme.onSurfaceVariant
//                            )
//                        }
//                    }
//                }
//
//                is EditorState.Success -> {
//                    Column(
//                        modifier = Modifier.fillMaxSize()
//                    ) {
//                        // Image Preview with Drawing Canvas
//                        Box(
//                            modifier = Modifier
//                                .fillMaxWidth()
//                                .weight(1f)
//                                .padding(16.dp)
//                                .clip(RoundedCornerShape(20.dp))
//                                .background(
//                                    brush = Brush.linearGradient(
//                                        colors = listOf(
//                                            Color(0xFFEEEEEE),
//                                            Color(0xFFCCCCCC)
//                                        )
//                                    )
//                                )
//                                .border(
//                                    width = 1.dp,
//                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
//                                    shape = RoundedCornerShape(20.dp)
//                                ),
//                            contentAlignment = Alignment.Center
//                        ) {
//                            if (isManualEditMode) {
//                                // Drawing canvas for manual editing
//                                DrawingCanvas(
//                                    bitmap = editorState.bitmap,
//                                    brushTool = currentBrushTool,
//                                    isEnabled = !isProcessing,
//                                    onDrawingPath = { path ->
//                                        viewModel.addBrushStroke(path)
//                                    },
//                                    modifier = Modifier.fillMaxSize()
//                                )
//
//                                // Brush mode indicator
//                                Box(
//                                    modifier = Modifier
//                                        .align(Alignment.TopStart)
//                                        .padding(16.dp)
//                                ) {
//                                    Surface(
//                                        color = when (currentBrushTool.mode) {
//                                            BrushMode.ERASE -> Color.Red.copy(alpha = 0.9f)
//                                            BrushMode.RESTORE -> Color.Green.copy(alpha = 0.9f)
//                                        },
//                                        shape = RoundedCornerShape(8.dp)
//                                    ) {
//                                        Row(
//                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
//                                            verticalAlignment = Alignment.CenterVertically,
//                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
//                                        ) {
//                                            Icon(
//                                                when (currentBrushTool.mode) {
//                                                    BrushMode.ERASE -> Icons.Default.Delete
//                                                    BrushMode.RESTORE -> Icons.Default.Brush
//                                                },
//                                                contentDescription = null,
//                                                tint = Color.White,
//                                                modifier = Modifier.size(20.dp)
//                                            )
//                                            Text(
//                                                when (currentBrushTool.mode) {
//                                                    BrushMode.ERASE -> "Erase"
//                                                    BrushMode.RESTORE -> "Restore"
//                                                },
//                                                color = Color.White,
//                                                style = MaterialTheme.typography.labelMedium,
//                                                fontWeight = FontWeight.Bold
//                                            )
//                                        }
//                                    }
//                                }
//                            } else {
//                                // Normal image preview
//                                Image(
//                                    bitmap = editorState.bitmap.asImageBitmap(),
//                                    contentDescription = "Edited Image",
//                                    modifier = Modifier.fillMaxSize(),
//                                    contentScale = ContentScale.Fit
//                                )
//                            }
//
//                            // Processing overlay
//                            if (isProcessing) {
//                                Box(
//                                    modifier = Modifier
//                                        .fillMaxSize()
//                                        .background(Color.Black.copy(alpha = 0.5f)),
//                                    contentAlignment = Alignment.Center
//                                ) {
//                                    CircularProgressIndicator(
//                                        color = Color.White,
//                                        strokeWidth = 4.dp
//                                    )
//                                }
//                            }
//                        }
//                    }
//                }
//
//                is EditorState.Error -> {
//                    Box(
//                        modifier = Modifier.fillMaxSize(),
//                        contentAlignment = Alignment.Center
//                    ) {
//                        Column(
//                            horizontalAlignment = Alignment.CenterHorizontally,
//                            verticalArrangement = Arrangement.spacedBy(16.dp)
//                        ) {
//                            Icon(
//                                Icons.Default.Error,
//                                contentDescription = null,
//                                modifier = Modifier.size(64.dp),
//                                tint = MaterialTheme.colorScheme.error
//                            )
//                            Text(
//                                "Error",
//                                style = MaterialTheme.typography.titleLarge,
//                                fontWeight = FontWeight.Bold
//                            )
//                            Text(
//                                editorState.message,
//                                style = MaterialTheme.typography.bodyMedium,
//                                color = MaterialTheme.colorScheme.onSurfaceVariant
//                            )
//                            Button(onClick = onBackClick) {
//                                Text("Go Back")
//                            }
//                        }
//                    }
//                }
//            }
//        }
//    }
//
//    // Background Picker Bottom Sheet
//    if (showBackgroundPicker) {
//        BackgroundPickerSheet(
//            currentBackground = currentBackground,
//            onBackgroundSelected = { background ->
//                viewModel.applyBackground(background)
//                showBackgroundPicker = false
//            },
//            onDismiss = { showBackgroundPicker = false }
//        )
//    }
//
//    // Save Dialog
//    if (showSaveDialog) {
//        SaveDialog(
//            onSave = { format ->
//                viewModel.saveBitmap(format) { result ->
//                    result.fold(
//                        onSuccess = {
//                            Toast.makeText(context, "Image saved successfully!", Toast.LENGTH_SHORT).show()
//                        },
//                        onFailure = { error ->
//                            Toast.makeText(context, "Failed to save: ${error.message}", Toast.LENGTH_SHORT).show()
//                        }
//                    )
//                }
//                showSaveDialog = false
//            },
//            onDismiss = { showSaveDialog = false }
//        )
//    }
//}
//

//


//
package com.remover.background.AI.ui.screens

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import com.remover.background.AI.model.BackgroundType
import com.remover.background.AI.model.BrushMode
import com.remover.background.AI.ui.components.BrushControlPanel
import com.remover.background.AI.ui.components.DrawingCanvas
import com.remover.background.AI.ui.theme.*
import com.remover.background.AI.viewmodel.EditorState
import com.remover.background.AI.viewmodel.EditorViewModel


@Composable
fun ZoomableBox(
    modifier: Modifier = Modifier,
    viewModel: EditorViewModel,
    content: @Composable BoxScope.() -> Unit
) {
    val fg = viewModel.foregroundBitmap
    
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val constraintsWidth = constraints.maxWidth.toFloat()
        val constraintsHeight = constraints.maxHeight.toFloat()
        
        // Calculate Layout Info
        var displayScale = 1f
        var drawW = 0f
        var drawH = 0f
        var imageOffsetX = 0f
        var imageOffsetY = 0f
        
        if (fg != null && fg.width > 0 && fg.height > 0) {
            val imageAspectRatio = fg.width.toFloat() / fg.height.toFloat()
            val constraintsAspectRatio = constraintsWidth / constraintsHeight
            
            displayScale = if (imageAspectRatio > constraintsAspectRatio) {
                constraintsWidth / fg.width
            } else {
                constraintsHeight / fg.height
            }
            
            drawW = fg.width * displayScale
            drawH = fg.height * displayScale
            
            imageOffsetX = (constraintsWidth - drawW) / 2f
            imageOffsetY = (constraintsHeight - drawH) / 2f
        }

        // Update ViewModel
        SideEffect {
            if (displayScale > 0 && displayScale.isFinite()) {
                viewModel.displayScaleFactor = displayScale
            }
        }

        // Gesture State
        var scale by remember { mutableFloatStateOf(1f) }
        var offsetX by remember { mutableFloatStateOf(0f) }
        var offsetY by remember { mutableFloatStateOf(0f) }
        var isSubjectInteraction by remember { mutableStateOf(false) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.changes.any { it.changedToDown() }) {
                                val down = event.changes.first { it.changedToDown() }
                                // 1. Untranslate View Pan/Zoom
                                val viewPoint = (down.position - Offset(offsetX, offsetY)) / scale
                                // 2. Untranslate Centering Offset
                                val imagePoint = viewPoint - Offset(imageOffsetX, imageOffsetY)
                                
                                isSubjectInteraction = viewModel.isSubjectHit(imagePoint)
                            }
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, rotation ->
                        if (isSubjectInteraction) {
                            viewModel.updateSubjectTransform(pan / scale, zoom, rotation)
                        } else {
                            val newScale = (scale * zoom).coerceIn(1f, 10f)
                            if (newScale > 1f || zoom > 1f) {
                                offsetX += pan.x
                                offsetY += pan.y
                            }
                            scale = newScale
                            if (scale <= 1f) {
                                scale = 1f; offsetX = 0f; offsetY = 0f
                            }
                        }
                    }
                }
        ) {
            // View Transform Layer
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale, scaleY = scale,
                        translationX = offsetX, translationY = offsetY
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Image Frame Layer
                Box(
                    modifier = Modifier
                        .size(with(density) { androidx.compose.ui.unit.DpSize(drawW.toDp(), drawH.toDp()) })
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
fun CheckerboardBackground(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val squareSize = 20.dp.toPx()
        val numCols = (size.width / squareSize).toInt() + 1
        val numRows = (size.height / squareSize).toInt() + 1
        
        for (row in 0..numRows) {
            for (col in 0..numCols) {
                val isLightSquare = (row + col) % 2 == 0
                drawRect(
                    color = if (isLightSquare) Color(0xFF2A2A2A) else Color(0xFF1A1A1A),
                    topLeft = Offset(col * squareSize, row * squareSize),
                    size = Size(squareSize, squareSize)
                )
            }
        }
    }
}

@Composable
fun ImageWithCheckerboard(
    bitmap: android.graphics.Bitmap,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit
) {
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val imageAspectRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
        val containerAspectRatio = maxWidth / maxHeight
        
        // Calculate the actual size the image will occupy with ContentScale.Fit
        val (imageWidth, imageHeight) = if (imageAspectRatio > containerAspectRatio) {
            // Image is wider - width fills, height scales
            maxWidth to (maxWidth / imageAspectRatio)
        } else {
            // Image is taller - height fills, width scales
            (maxHeight * imageAspectRatio) to maxHeight
        }
        
        // Checkerboard only in the exact image area
        CheckerboardBackground(
            modifier = Modifier.size(imageWidth, imageHeight)
        )
        
        // Image on top
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = contentScale
        )
    }
}

@Composable
fun SaveDialog(
    onSave: (Bitmap.CompressFormat) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save Image") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Choose format:")
                Button(
                    onClick = { onSave(Bitmap.CompressFormat.PNG) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("PNG (Transparent)")
                }
                Button(
                    onClick = { onSave(Bitmap.CompressFormat.JPEG) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("JPEG (Smaller size)")
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun BackgroundLayer(
    backgroundType: BackgroundType,
    originalBitmap: android.graphics.Bitmap? = null,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        when (backgroundType) {
            is BackgroundType.Transparent -> {
                CheckerboardBackground(modifier = Modifier.fillMaxSize())
            }
            is BackgroundType.SolidColor -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(backgroundType.color)
                )
            }
            is BackgroundType.Gradient -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(backgroundType.startColor, backgroundType.endColor),
                                start = Offset.Zero,
                                end = Offset.Infinite
                            )
                        )
                )
            }
            is BackgroundType.CustomImage -> {
                Image(
                    bitmap = backgroundType.bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            is BackgroundType.Original -> {
                if (originalBitmap != null) {
                    Image(
                        bitmap = originalBitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            is BackgroundType.Blur -> {
                if (originalBitmap != null) {
                    // Note: Modifier.blur requires API 31+. For lower APIs, we might need a different approach.
                    // For now, we show the original image.
                    Image(
                        bitmap = originalBitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
fun EditorScreen(viewModel: EditorViewModel, onBackClick: () -> Unit) {
    val context = LocalContext.current
    val editorState = viewModel.editorState
    val isManual = viewModel.isManualEditMode
    var showBgPicker by remember { mutableStateOf(false) }
    var showSaveSheet by remember { mutableStateOf(false) }

    val customImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                if (bitmap != null) {
                    viewModel.applyBackground(BackgroundType.CustomImage(bitmap))
                    showBgPicker = false
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
    ) {
        // 1. IMAGE/CANVAS LAYER (Full Screen)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 110.dp) // Avoid Top Bar overlap
                .padding(bottom = if (isManual) 280.dp else 130.dp), // Reduced padding for slimmer menu
            contentAlignment = Alignment.Center
        ) {
            if (editorState is EditorState.Success) {
                // Container with solid pure black background
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .shadow(
                            elevation = 8.dp,
                            shape = RoundedCornerShape(16.dp),
                            clip = false
                        )
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black) // Pure black - no checkerboard possible
                        .border(
                            width = 2.dp,
                            color = Color.White.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(16.dp)
                        )
                )


                // Display the image - background including checkerboard is already composited into bitmap
                if (isManual) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (viewModel.currentBackground is BackgroundType.Transparent) {
                            CheckerboardBackground(modifier = Modifier.fillMaxSize())
                        }
                        DrawingCanvas(
                            bitmap = editorState.bitmap,
                            brushTool = viewModel.currentBrushTool,
                            isEnabled = !viewModel.isProcessing,
                            onDrawingPath = { viewModel.addBrushStroke(it) },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    ZoomableBox(
                        modifier = Modifier
                            .fillMaxSize(),
                        viewModel = viewModel
                    ) {
                        // Background
                        BackgroundLayer(
                            backgroundType = viewModel.currentBackground,
                            originalBitmap = viewModel.originalBitmap,
                            modifier = Modifier.fillMaxSize()
                        )
                        
                        // Foreground (Subject)
                        val fg = viewModel.foregroundBitmap
                        if (fg != null) {
                            Image(
                                bitmap = fg.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer(
                                        scaleX = viewModel.subjectScale,
                                        scaleY = viewModel.subjectScale,
                                        rotationZ = viewModel.subjectRotation,
                                        translationX = viewModel.subjectPosition.x * viewModel.displayScaleFactor,
                                        translationY = viewModel.subjectPosition.y * viewModel.displayScaleFactor
                                    ),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                }
            } else if (viewModel.isProcessing) {
                CircularProgressIndicator(
                    color = Primary,
                    modifier = Modifier.size(48.dp)
                )
            }
        }

        // 2. TOP BAR (Floating)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.4f))
                    .border(1.dp, Color.White.copy(0.1f), CircleShape)
            ) {
                Icon(Icons.Default.ArrowBack, null, tint = Color.White)
            }

            if (isManual) {
                // Undo/Redo Group
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(50))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = { viewModel.undo() },
                        enabled = viewModel.canUndo
                    ) {
                        Icon(
                            Icons.Default.Undo,
                            "Undo",
                            tint = if (viewModel.canUndo) Color.White else Color.White.copy(0.3f)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(24.dp)
                            .background(Color.White.copy(0.1f))
                            .align(Alignment.CenterVertically)
                    )
                    IconButton(
                        onClick = { viewModel.redo() },
                        enabled = viewModel.canRedo
                    ) {
                        Icon(
                            Icons.Default.Redo,
                            "Redo",
                            tint = if (viewModel.canRedo) Color.White else Color.White.copy(0.3f)
                        )
                    }
                }
            } else {
                Button(
                    onClick = {
                        showSaveSheet = true
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Primary,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                    shape = RoundedCornerShape(50)
                ) {
                    Text("Save", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // 3. BOTTOM CONTROLS (Floating)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            if (isManual) {
                // Manual Edit Controls
                BrushControlPanel(
                    brushTool = viewModel.currentBrushTool,
                    onBrushToolChange = { viewModel.updateBrushTool(it.mode, it.size, it.hardness, it.opacity) },
                    onClearStrokes = { viewModel.clearBrushStrokes() },
                    onSmoothMask = { viewModel.smoothMask() },
                    onApplyStrokes = { viewModel.applyStrokes() },
                    onDone = { viewModel.exitManualEditMode(true) },
                    onCancel = { viewModel.exitManualEditMode(false) }
                )
            } else {
                // Main Menu - Clean Transparent Row (Reverted & Refined)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        EditorMenuItem(
                            icon = Icons.Default.ColorLens,
                            label = "Background",
                            onClick = { showBgPicker = true }
                        )
                        

                        
                        EditorMenuItem(
                            icon = Icons.Default.Delete,
                            label = "Erase",
                            onClick = {
                                viewModel.updateBrushTool(mode = BrushMode.ERASE, null, null, null)
                                viewModel.enterManualEditMode()
                            }
                        )
                        
                        EditorMenuItem(
                            icon = Icons.Default.Brush,
                            label = "Restore",
                            onClick = {
                                viewModel.updateBrushTool(mode = BrushMode.RESTORE, null, null, null)
                                viewModel.enterManualEditMode()
                            }
                        )
                    }
                }
            }
        }
    }

    if (showBgPicker) {
        BackgroundPickerSheet(
            currentBackground = viewModel.currentBackground,
            onBackgroundSelected = { viewModel.applyBackground(it) },
            onPickCustomImage = { customImagePickerLauncher.launch("image/*") },
            onDismiss = { showBgPicker = false }
        )
    }

    if (showSaveSheet) {
        SaveOptionsSheet(
            currentBackground = viewModel.currentBackground,
            onSave = { format ->
                showSaveSheet = false
                viewModel.saveBitmap(format) {
                    Toast.makeText(context, "Image Saved to Gallery", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { showSaveSheet = false }
        )
    }
}

@Composable
fun EditorMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean = false,
    isProcessing: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = !isProcessing, onClick = onClick)
            .padding(12.dp) // Comfortable touch area
    ) {
        if (isProcessing) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = Color.White,
                strokeWidth = 2.dp
            )
        } else {
            Icon(
                icon,
                contentDescription = label,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                modifier = Modifier.size(22.dp) // Reduced Icon Size
            )
        }
        
        Spacer(Modifier.height(4.dp))
        
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White
        )
    }
}


@Composable
fun CompactEditBtn(
    icon: androidx.compose.ui.graphics.vector.ImageVector, 
    text: String, 
    isProcessing: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = !isProcessing, onClick = onClick)
            .background(Color.White.copy(if (isProcessing) 0.05f else 0.08f))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier.size(24.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isProcessing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Primary,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    icon, 
                    contentDescription = null, 
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Text(
            text, 
            color = Color.White.copy(if (isProcessing) 0.5f else 1f),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun VerticalDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(24.dp)
            .background(Color.White.copy(0.15f))
    )
}

@Composable
fun EditBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        Box(
            modifier = Modifier.size(56.dp).clip(CircleShape).background(Color.White.copy(0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = Color.White)
        }
        Spacer(Modifier.height(8.dp))
        Text(text, color = Color.White, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun EditOption(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        Box(
            modifier = Modifier.size(50.dp).clip(CircleShape).background(Color.White.copy(0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = Color.White)
        }
        Spacer(Modifier.height(8.dp))
        Text(label, color = Color.White, style = MaterialTheme.typography.labelSmall)
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackgroundPickerSheet(
    currentBackground: BackgroundType,
    onBackgroundSelected: (BackgroundType) -> Unit,
    onPickCustomImage: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Colors", "Gradients", "Image", "Blur")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E1E),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(0.2f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Text(
                "Background",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )

            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = Color.White,
                edgePadding = 16.dp,
                indicator = { tabPositions ->
                    if (selectedTab < tabPositions.size) {
                        TabRowDefaults.Indicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = Primary,
                            height = 3.dp
                        )
                    }
                },
                divider = {
                    HorizontalDivider(color = Color.White.copy(0.1f))
                }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { 
                            Text(
                                title, 
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium,
                                color = if (selectedTab == index) Color.White else Color.Gray
                            ) 
                        }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Box(modifier = Modifier.height(280.dp).padding(horizontal = 16.dp)) {
                when (selectedTab) {
                    0 -> ColorsGrid(currentBackground, onBackgroundSelected)
                    1 -> GradientsGrid(currentBackground, onBackgroundSelected)
                    2 -> ImagesGrid(currentBackground, onBackgroundSelected, onPickCustomImage)
                    3 -> BlurControl(currentBackground, onBackgroundSelected)
                }
            }
        }
    }
}

@Composable
fun ColorsGrid(current: BackgroundType, onSelect: (BackgroundType) -> Unit) {
    val colors = listOf(
        Color.White, Color.Black, Color(0xFFF44336), Color(0xFFE91E63),
        Color(0xFF9C27B0), Color(0xFF673AB7), Color(0xFF3F51B5), Color(0xFF2196F3),
        Color(0xFF03A9F4), Color(0xFF00BCD4), Color(0xFF009688), Color(0xFF4CAF50),
        Color(0xFF8BC34A), Color(0xFFCDDC39), Color(0xFFFFEB3B), Color(0xFFFFC107),
        Color(0xFFFF9800), Color(0xFFFF5722), Color(0xFF795548), Color(0xFF9E9E9E)
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(5),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            // Transparent Option
            SelectionItem(
                isSelected = current is BackgroundType.Transparent,
                onClick = { onSelect(BackgroundType.Transparent) }
            ) {
                CheckerboardBackground(Modifier.fillMaxSize())
            }
        }
        items(colors) { color ->
            SelectionItem(
                isSelected = current is BackgroundType.SolidColor && current.color == color,
                onClick = { onSelect(BackgroundType.SolidColor(color)) }
            ) {
                Box(Modifier.fillMaxSize().background(color))
            }
        }
    }
}

@Composable
fun GradientsGrid(current: BackgroundType, onSelect: (BackgroundType) -> Unit) {
    val gradients = listOf(
        Color(0xFFFF9A9E) to Color(0xFFFECFEF),
        Color(0xFFA18CD1) to Color(0xFFFBC2EB),
        Color(0xFF84FAB0) to Color(0xFF8FD3F4),
        Color(0xFFE0C3FC) to Color(0xFF8EC5FC),
        Color(0xFF43E97B) to Color(0xFF38F9D7),
        Color(0xFFFA709A) to Color(0xFFFEE140),
        Color(0xFF30CFD0) to Color(0xFF330867),
        Color(0xFF667EEA) to Color(0xFF764BA2)
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(gradients) { (start, end) ->
            SelectionItem(
                isSelected = current is BackgroundType.Gradient && current.startColor == start,
                onClick = { onSelect(BackgroundType.Gradient(start, end)) },
                aspectRatio = 1.5f
            ) {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.linearGradient(listOf(start, end))
                    )
                )
            }
        }
    }
}

@Composable
fun ImagesGrid(
    current: BackgroundType, 
    onSelect: (BackgroundType) -> Unit, 
    onPickCustom: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        // Original
        SelectionItem(
            isSelected = current is BackgroundType.Original,
            onClick = { onSelect(BackgroundType.Original) },
            label = "Original",
            aspectRatio = 1f,
            modifier = Modifier.weight(1f)
        ) {
            Box(Modifier.fillMaxSize().background(Color.DarkGray), contentAlignment = Alignment.Center) {
                Text("ORIG", color = Color.White.copy(0.5f), fontWeight = FontWeight.Bold)
            }
        }

        // Custom
        SelectionItem(
            isSelected = current is BackgroundType.CustomImage,
            onClick = onPickCustom,
            label = "Custom",
            aspectRatio = 1f,
            modifier = Modifier.weight(1f)
        ) {
            Box(Modifier.fillMaxSize().background(Color(0xFF2A2A2A)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Add, null, tint = Color.White)
            }
        }
    }
}

@Composable
fun BlurControl(current: BackgroundType, onSelect: (BackgroundType) -> Unit) {
    var intensity by remember { mutableFloatStateOf(10f) }
    
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Blur Intensity", color = Color.Gray, modifier = Modifier.padding(bottom = 16.dp))
        
        Slider(
            value = intensity,
            onValueChange = { 
                intensity = it
                onSelect(BackgroundType.Blur(it))
            },
            valueRange = 1f..25f,
            colors = SliderDefaults.colors(
                thumbColor = Primary,
                activeTrackColor = Primary
            )
        )
    }
}

@Composable
fun SelectionItem(
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    aspectRatio: Float = 1f,
    content: @Composable () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Box(
            modifier = Modifier
                .aspectRatio(aspectRatio)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onClick)
                .then(
                    if (isSelected) Modifier.border(2.dp, Primary, RoundedCornerShape(12.dp))
                    else Modifier.border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(12.dp))
                )
        ) {
            content()
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Primary.copy(alpha = 0.2f))
                )
                Icon(
                    Icons.Default.CheckCircle,
                    null,
                    tint = Primary,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(24.dp)
                        .background(Color.White, CircleShape)
                        .padding(2.dp)
                )
            }
        }
        if (label != null) {
            Spacer(Modifier.height(8.dp))
            Text(label, color = if (isSelected) Primary else Color.Gray, style = MaterialTheme.typography.labelSmall)
        }
    }
}
