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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.core.content.FileProvider
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
    enableZoom: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds() // Clip content to bounds so it doesn't overflow
            .pointerInput(enableZoom) {
                if (enableZoom) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            
                            // Handle Zoom/Pan with 2+ fingers
                            if (pressed.size >= 2) {
                                val p1 = pressed[0]
                                val p2 = pressed[1]
                                
                                if (p1.previousPressed && p2.previousPressed) {
                                    val oldDist = (p1.previousPosition - p2.previousPosition).getDistance()
                                    val newDist = (p1.position - p2.position).getDistance()
                                    
                                    // Zoom
                                    if (oldDist > 10f) {
                                        scale = (scale * (newDist / oldDist)).coerceIn(1f, 10f)
                                    }
                                    
                                    // Pan
                                    val pan = ((p1.position - p1.previousPosition) + (p2.position - p2.previousPosition)) / 2f
                                    if (scale > 1f) {
                                        offsetX += pan.x
                                        offsetY += pan.y
                                    }
                                    
                                    // Consume events
                                    pressed.forEach { it.consume() }
                                }
                            }
                            
                            // Reset if zoomed out
                            if (scale <= 1f) {
                                scale = 1f
                                offsetX = 0f
                                offsetY = 0f
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                ),
            contentAlignment = Alignment.Center
        ) {
            content()
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
    var isMoveMode by remember { mutableStateOf(false) }

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
                    DrawingCanvas(
                        bitmap = editorState.bitmap,
                        brushTool = viewModel.currentBrushTool,
                        isEnabled = !viewModel.isProcessing,
                        onDrawingPath = { viewModel.addBrushStroke(it) },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    )
                } else {
                    ZoomableBox(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        enableZoom = !isMoveMode
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
                                        translationX = viewModel.subjectPosition.x,
                                        translationY = viewModel.subjectPosition.y
                                    )
                                    .pointerInput(isMoveMode) {
                                        if (isMoveMode) {
                                            detectTransformGestures { _, pan, zoom, rotation ->
                                                viewModel.updateSubjectTransform(pan, zoom, rotation)
                                            }
                                        }
                                    },
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
                        viewModel.saveBitmap(Bitmap.CompressFormat.PNG) {
                            Toast.makeText(context, "Image Saved to Gallery", Toast.LENGTH_SHORT).show()
                        }
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
                            icon = Icons.Default.OpenWith,
                            label = "Move",
                            isSelected = isMoveMode,
                            onClick = { 
                                isMoveMode = !isMoveMode
                                if (isMoveMode) {
                                    Toast.makeText(context, "Drag to move, pinch to resize/rotate subject", Toast.LENGTH_SHORT).show()
                                }
                            }
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
            onBackgroundSelected = { viewModel.applyBackground(it); showBgPicker = false },
            onPickCustomImage = { customImagePickerLauncher.launch("image/*") },
            onDismiss = { showBgPicker = false }
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
    val context = LocalContext.current
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                "Choose Background",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Transparent
            BackgroundOption(
                title = "Transparent",
                subtitle = "No background",
                isSelected = currentBackground is BackgroundType.Transparent,
                onClick = { onBackgroundSelected(BackgroundType.Transparent) }
            ) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(Color.White, Color.LightGray)
                            )
                        )
                        .border(1.dp, Color.Gray, RoundedCornerShape(8.dp))
                )
            }

            Spacer(Modifier.height(12.dp))
            
            // Custom Image Background
            BackgroundOption(
                title = "Custom Image",
                subtitle = "Pick from gallery",
                isSelected = currentBackground is BackgroundType.CustomImage,
                onClick = onPickCustomImage
            ) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF2A2A2A))
                        .border(1.dp, Primary, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Image,
                        null,
                        tint = Primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Solid Colors
            Text(
                "Solid Colors",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                val colors = listOf(
                    Color.White to "White",
                    Color.Black to "Black",
                    Color.Red to "Red",
                    Color.Blue to "Blue",
                    Color.Green to "Green",
                    Color.Yellow to "Yellow",
                    Primary to "Primary"
                )

                items(colors) { (color, name) ->
                    ColorOption(
                        color = color,
                        name = name,
                        isSelected = currentBackground is BackgroundType.SolidColor &&
                                currentBackground.color == color,
                        onClick = { onBackgroundSelected(BackgroundType.SolidColor(color)) }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Gradients
            Text(
                "Gradients",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                val gradients = listOf(
                    Triple(Primary, PrimaryLight, "Primary"),
                    Triple(Secondary, SecondaryLight, "Secondary"),
                    Triple(Color(0xFF6A11CB), Color(0xFF2575FC), "Purple"),
                    Triple(Color(0xFFFF6B6B), Color(0xFF4ECDC4), "Sunset"),
                    Triple(Color(0xFF0F2027), Color(0xFF2C5364), "Dark")
                )

                items(gradients) { (start, end, name) ->
                    GradientOption(
                        startColor = start,
                        endColor = end,
                        name = name,
                        isSelected = currentBackground is BackgroundType.Gradient &&
                                currentBackground.startColor == start,
                        onClick = {
                            onBackgroundSelected(
                                BackgroundType.Gradient(start, end, 45f)
                            )
                        }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Blur
            BackgroundOption(
                title = "Blur Background",
                subtitle = "Blurred original",
                isSelected = currentBackground is BackgroundType.Blur,
                onClick = { onBackgroundSelected(BackgroundType.Blur(15f)) }
            ) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Gray.copy(alpha = 0.5f))
                )
            }

            Spacer(Modifier.height(12.dp))

            // Original
            BackgroundOption(
                title = "Original",
                subtitle = "Keep original background",
                isSelected = currentBackground is BackgroundType.Original,
                onClick = { onBackgroundSelected(BackgroundType.Original) }
            ) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(Color.Cyan, Color.Blue, Color.Magenta)
                            )
                        )
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}



@Composable
fun BackgroundOption(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    preview: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .background(
                if (isSelected) Primary.copy(alpha = 0.1f)
                else Color.Transparent
            )
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) Primary else Color.Gray.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        preview()

        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (isSelected) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Primary
            )
        }
    }
}

@Composable
fun ColorOption(
    color: Color,
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(color)
                .border(
                    width = if (isSelected) 3.dp else 1.dp,
                    color = if (isSelected) Primary else Color.Gray,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = if (color == Color.White || color == Color.Yellow) Color.Black else Color.White
                )
            }
        }
        Text(
            name,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
fun GradientOption(
    startColor: Color,
    endColor: Color,
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(startColor, endColor)
                    )
                )
                .border(
                    width = if (isSelected) 3.dp else 1.dp,
                    color = if (isSelected) Primary else Color.Gray,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White
                )
            }
        }
        Text(
            name,
            style = MaterialTheme.typography.bodySmall
        )
    }
}