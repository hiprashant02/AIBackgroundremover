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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.remover.background.AI.R
import com.remover.background.AI.model.BackgroundType
import com.remover.background.AI.model.BrushMode
import com.remover.background.AI.ui.components.BrushControlPanel
import com.remover.background.AI.ui.components.DrawingCanvas
import com.remover.background.AI.ui.theme.*
import com.remover.background.AI.viewmodel.EditorState
import com.remover.background.AI.viewmodel.EditorViewModel
import com.remover.background.AI.ui.components.ColorPicker
import com.remover.background.AI.ui.components.GradientBuilder
import com.remover.background.AI.ui.components.BannerAd
import kotlinx.coroutines.delay


enum class PickerView {
    Main,
    CustomColor,
    CustomGradient,
    GradientStartColor,
    GradientEndColor
}

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
                    color = if (isLightSquare) Color(0xFFFFFFFF) else Color(0xFFE0E0E0), // White-Grey
                    topLeft = Offset(col * squareSize, row * squareSize),
                    size = Size(squareSize, squareSize)
                )
            }
        }
    }
}





@Composable
fun BackgroundLayer(
    backgroundType: BackgroundType,
    originalBitmap: Bitmap? = null,
    blurredBitmap: Bitmap? = null,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        when (backgroundType) {
            is BackgroundType.Transparent -> {
                CheckerboardBackground(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White)
                )
            }
            is BackgroundType.SolidColor -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(backgroundType.color)
                )
            }
            is BackgroundType.Gradient -> {
                val gradientBrush = remember(backgroundType.startColor, backgroundType.endColor, backgroundType.angle) {
                    object : androidx.compose.ui.graphics.ShaderBrush() {
                        override fun createShader(size: androidx.compose.ui.geometry.Size): androidx.compose.ui.graphics.Shader {
                            val w = size.width
                            val h = size.height
                            val r = backgroundType.angle * (kotlin.math.PI / 180.0)
                            val cx = w / 2
                            val cy = h / 2
                            val d = kotlin.math.sqrt((w*w + h*h).toDouble()) / 2
                            
                            val sx = cx - d * kotlin.math.cos(r)
                            val sy = cy - d * kotlin.math.sin(r)
                            val ex = cx + d * kotlin.math.cos(r)
                            val ey = cy + d * kotlin.math.sin(r)
                            
                            return androidx.compose.ui.graphics.LinearGradientShader(
                                from = androidx.compose.ui.geometry.Offset(sx.toFloat(), sy.toFloat()),
                                to = androidx.compose.ui.geometry.Offset(ex.toFloat(), ey.toFloat()),
                                colors = listOf(backgroundType.startColor, backgroundType.endColor),
                                tileMode = androidx.compose.ui.graphics.TileMode.Clamp
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(gradientBrush)
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
                if (blurredBitmap != null) {
                    Image(
                        bitmap = blurredBitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (originalBitmap != null) {
                    Image(
                        bitmap = originalBitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(viewModel: EditorViewModel, onBackClick: () -> Unit) {
    val context = LocalContext.current
    val editorState = viewModel.editorState
    val isManual = viewModel.isManualEditMode
    
    // Use ViewModel states instead of local remember
    val showBgPicker = viewModel.showBgPicker
    val showSaveSheet = viewModel.showSaveSheet
    val showSuccessDialog = viewModel.showSuccessDialog
    val showExitConfirmDialog = viewModel.showExitConfirmDialog
    
    // Handle back button press
    androidx.activity.compose.BackHandler {
        viewModel.showExitConfirmDialog = true
    }
    
    val customImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                // First, get image dimensions without loading full bitmap
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                context.contentResolver.openInputStream(it)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, options)
                }
                
                // Calculate sample size to avoid loading huge images
                val maxDimension = 4096
                var sampleSize = 1
                while (options.outWidth / sampleSize > maxDimension || 
                       options.outHeight / sampleSize > maxDimension) {
                    sampleSize *= 2
                }
                
                // Now decode with the calculated sample size
                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                }
                
                val bitmap = context.contentResolver.openInputStream(it)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, decodeOptions)
                }
                
                if (bitmap != null) {
                    // Pass original URI for full-resolution export
                    viewModel.applyBackground(BackgroundType.CustomImage(bitmap, originalUri = it))
                    viewModel.showBgPicker = false
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.showExitConfirmDialog = true }, // Show dialog instead of direct back
                        modifier = Modifier
                            .padding(8.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.4f))
                            .border(1.dp, Color.White.copy(0.1f), CircleShape)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
                    }
                },
                actions = {
                    if (isManual) {
                        // Undo/Redo Group
                        Row(
                            modifier = Modifier
                                .padding(8.dp)
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
                                    Icons.AutoMirrored.Filled.Undo,
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
                                    Icons.AutoMirrored.Filled.Redo,
                                    "Redo",
                                    tint = if (viewModel.canRedo) Color.White else Color.White.copy(0.3f)
                                )
                            }
                        }
                    } else {
                        Button(
                            onClick = {
                                viewModel.showSaveSheet = true
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Primary,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.padding(8.dp)
                        ) {
                            Text(stringResource(R.string.menu_save), fontWeight = FontWeight.SemiBold)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f) // Take remaining space
                    .background(MaterialTheme.colorScheme.background).animateContentSize()
            ) {
                // 1. IMAGE/CANVAS LAYER
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (editorState is EditorState.Success) {
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
                                ).animateContentSize()
                        )


                        // Display the image - background including checkerboard is already composited into bitmap
                        if (isManual) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                DrawingCanvas(
                                    bitmap = editorState.bitmap,
                                    brushTool = viewModel.currentBrushTool,
                                    isEnabled = !viewModel.isProcessing && !viewModel.isBrushProcessing,
                                    showCheckerboard = viewModel.currentBackground is BackgroundType.Transparent,
                                    onDrawingPath = { viewModel.addBrushStroke(it) },
                                    onDisplayScaleChanged = { viewModel.displayScaleFactor = it },
                                    modifier = Modifier.fillMaxSize().animateContentSize()
                                )
                                
                                // Show loading indicator when brush strokes are being applied
                                if (viewModel.isBrushProcessing) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.3f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(48.dp),
                                            color = Color.White,
                                            strokeWidth = 4.dp
                                        )
                                    }
                                }
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
                                    blurredBitmap = viewModel.blurredBackgroundBitmap,
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
                                            ).animateContentSize(),
                                        contentScale = ContentScale.Fit
                                    )
                                }
                            }
                        }
                    }

                    if (viewModel.isProcessing) {
                        CircularProgressIndicator(
                            color = Primary,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
            }

            // 2. BOTTOM CONTROLS - Now in Column, stacks naturally
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
                // Main Menu - Clean Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    EditorMenuItem(
                        icon = Icons.Default.ColorLens,
                        label = stringResource(R.string.menu_background),
                        onClick = { viewModel.showBgPicker = true }
                    )

                    EditorMenuItem(
                        icon = Icons.Default.Delete,
                        label = stringResource(R.string.manual_mode_erase),
                        onClick = {
                            viewModel.updateBrushTool(mode = BrushMode.ERASE, null, null, null)
                            viewModel.enterManualEditMode()
                        }
                    )

                    EditorMenuItem(
                        icon = Icons.Default.Brush,
                        label = stringResource(R.string.manual_mode_restore),
                        onClick = {
                            viewModel.updateBrushTool(mode = BrushMode.RESTORE, null, null, null)
                            viewModel.enterManualEditMode()
                        }
                    )
                }
            }

            // 3. BANNER AD - At bottom of Column
            BannerAd(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            )
        }
    }

    if (showBgPicker) {
        BackgroundPickerSheet(
            currentBackground = viewModel.currentBackground,
            onBackgroundSelected = { viewModel.applyBackground(it) },
            onPickCustomImage = { customImagePickerLauncher.launch("image/*") },
            onDismiss = { viewModel.showBgPicker = false }
        )
    }

    if (showSaveSheet) {
        SaveOptionsSheet(
            currentBackground = viewModel.currentBackground,
            onSave = { format ->
                viewModel.showSaveSheet = false
                viewModel.saveBitmap(format) { result ->
                    if (result.isSuccess) {
                        viewModel.showSuccessDialog = true
                    } else {
                        Toast.makeText(context, "Failed to save image", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onDismiss = { viewModel.showSaveSheet = false }
        )
    }

    if (showSuccessDialog) {
        SaveSuccessDialog(
            onEditNewImage = {
                viewModel.showSuccessDialog = false
                onBackClick() // This resets VM and goes back to Home
            },
            onDismiss = { viewModel.showSuccessDialog = false }
        )
    }

    if (viewModel.isSaving) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(enabled = false) {},
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Primary)
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.saving_message), color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }

    if (showExitConfirmDialog) {
        ExitConfirmDialog(
            onConfirm = {
                viewModel.showExitConfirmDialog = false
                onBackClick()
            },
            onDismiss = { viewModel.showExitConfirmDialog = false }
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
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.dp
            )
        } else {
            Icon(
                icon,
                contentDescription = label,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(22.dp) // Reduced Icon Size
            )
        }
        
        Spacer(Modifier.height(4.dp))
        
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
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
    val tabs = listOf(
        stringResource(R.string.tab_colors),
        stringResource(R.string.tab_gradients),
        stringResource(R.string.tab_images),
        stringResource(R.string.tab_blur)
    )
    
    // State Management
    var currentView by remember { mutableStateOf(PickerView.Main) }
    var gradientStart by remember { mutableStateOf(Color.Blue) }
    var gradientEnd by remember { mutableStateOf(Color.Cyan) }
    var gradientAngle by remember { mutableFloatStateOf(0f) }

    // Sync local state with current background
    LaunchedEffect(currentBackground) {
        if (currentBackground is BackgroundType.Gradient) {
            gradientStart = currentBackground.startColor
            gradientEnd = currentBackground.endColor
            gradientAngle = currentBackground.angle
        }
    }

    // Helper to handle back press
    val onBack = {
        when (currentView) {
            PickerView.Main -> onDismiss()
            PickerView.CustomColor -> currentView = PickerView.Main
            PickerView.CustomGradient -> currentView = PickerView.Main
            PickerView.GradientStartColor -> currentView = PickerView.CustomGradient
            PickerView.GradientEndColor -> currentView = PickerView.CustomGradient
        }
    }

    // Create sheet state to open fully expanded
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    // If not Main, show full screen picker
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.onSurfaceVariant) }
    ) {
        if (currentView != PickerView.Main) {
            // ... (Detail Views: Custom Color, Gradient, etc.)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        when (currentView) {
                            PickerView.Main -> onDismiss()
                            PickerView.CustomColor -> currentView = PickerView.Main
                            PickerView.CustomGradient -> currentView = PickerView.Main
                            PickerView.GradientStartColor -> currentView = PickerView.CustomGradient
                            PickerView.GradientEndColor -> currentView = PickerView.CustomGradient
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    
                    Text(
                        text = when (currentView) {
                            PickerView.CustomColor -> "Custom Color"
                            PickerView.CustomGradient -> "Custom Gradient"
                            PickerView.GradientStartColor -> "Start Color"
                            PickerView.GradientEndColor -> "End Color"
                            else -> ""
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(Modifier.width(48.dp)) // Balance the back button
                }

                Spacer(Modifier.height(16.dp))

                // Content
                when (currentView) {
                    PickerView.CustomColor -> {
                        com.remover.background.AI.ui.components.ColorPicker(
                            initialColor = (currentBackground as? BackgroundType.SolidColor)?.color ?: Color.White,
                            onColorChanged = { onBackgroundSelected(BackgroundType.SolidColor(it)) }
                        )
                    }
                    PickerView.CustomGradient -> {
                        com.remover.background.AI.ui.components.GradientBuilder(
                            startColor = gradientStart,
                            endColor = gradientEnd,
                            angle = gradientAngle,
                            onStartColorClick = { currentView = PickerView.GradientStartColor },
                            onEndColorClick = { currentView = PickerView.GradientEndColor },
                            onAngleChange = { 
                                gradientAngle = it
                            },
                            onAngleChangeFinished = {
                                onBackgroundSelected(BackgroundType.Gradient(gradientStart, gradientEnd, gradientAngle))
                            },
                            onSwapColors = {
                                 val temp = gradientStart
                                 gradientStart = gradientEnd
                                 gradientEnd = temp
                                 onBackgroundSelected(BackgroundType.Gradient(gradientStart, gradientEnd, gradientAngle))
                            },
                            onPreviewClick = {
                                onBackgroundSelected(BackgroundType.Gradient(gradientStart, gradientEnd, gradientAngle))
                            }
                        )
                    }
                    PickerView.GradientStartColor -> {
                        com.remover.background.AI.ui.components.ColorPicker(
                            initialColor = gradientStart,
                            onColorChanged = { 
                                gradientStart = it
                                onBackgroundSelected(BackgroundType.Gradient(it, gradientEnd, gradientAngle))
                            }
                        )
                    }
                    PickerView.GradientEndColor -> {
                        com.remover.background.AI.ui.components.ColorPicker(
                            initialColor = gradientEnd,
                            onColorChanged = { 
                                gradientEnd = it
                                onBackgroundSelected(BackgroundType.Gradient(gradientStart, it, gradientAngle))
                            }
                        )
                    }
                    else -> {}
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    edgePadding = 16.dp,
                    indicator = { tabPositions ->
                        if (selectedTab < tabPositions.size) {
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                color = Primary,
                                height = 3.dp
                            )
                        }
                    },
                    divider = {
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.2f))
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
                                    color = if (selectedTab == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                ) 
                            }
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                Box(modifier = Modifier.height(280.dp).padding(horizontal = 16.dp)) {
                    when (selectedTab) {
                        0 -> ColorsGrid(currentBackground, onBackgroundSelected, onPickCustomColor = { currentView = PickerView.CustomColor })
                        1 -> GradientsGrid(currentBackground, onBackgroundSelected, onPickCustomGradient = { currentView = PickerView.CustomGradient })
                        2 -> ImagesGrid(currentBackground, onBackgroundSelected, onPickCustomImage)
                        3 -> BlurControl(currentBackground, onBackgroundSelected)
                    }
                }
            }
        }
    }
}



@Composable
fun ColorsGrid(
    current: BackgroundType, 
    onSelect: (BackgroundType) -> Unit,
    onPickCustomColor: () -> Unit
) {
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
            // Custom Color Button
            val isCustomSelected = current is BackgroundType.SolidColor && current.color !in colors
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.sweepGradient(
                            colors = listOf(
                                Color.Red, Color.Magenta, Color.Blue, Color.Cyan,
                                Color.Green, Color.Yellow, Color.Red
                            )
                        )
                    )
                    .then(
                        if (isCustomSelected) Modifier.border(2.dp, Primary, CircleShape)
                        else Modifier.border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(0.2f), CircleShape)
                    )
                    .clickable { onPickCustomColor() },
                contentAlignment = Alignment.Center
            ) {
                if (isCustomSelected) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.3f))
                    )
                    Icon(Icons.Default.CheckCircle, null, tint = Primary)
                } else {
                    Icon(Icons.Default.Add, contentDescription = "Custom", tint = Color.White)
                }
            }
        }

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
fun GradientsGrid(
    current: BackgroundType, 
    onSelect: (BackgroundType) -> Unit,
    onPickCustomGradient: () -> Unit
) {
    val gradients = listOf(
        Pair(Color(0xFF2196F3), Color(0xFFE91E63)),
        Pair(Color(0xFF4CAF50), Color(0xFFFFEB3B)),
        Pair(Color(0xFFFF9800), Color(0xFFF44336)),
        Pair(Color(0xFF9C27B0), Color(0xFF3F51B5)),
        Pair(Color(0xFF00BCD4), Color(0xFF009688)),
        Pair(Color(0xFF673AB7), Color(0xFFE91E63)),
        Pair(Color(0xFFFF5722), Color(0xFFFFC107)),
        Pair(Color(0xFF795548), Color(0xFF3E2723))
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(5),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            // Custom Gradient Button
            val isCustomSelected = current is BackgroundType.Gradient && 
                                  !gradients.any { it.first == current.startColor && it.second == current.endColor }
            
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color.Blue, Color.Cyan)))
                    .then(
                        if (isCustomSelected) Modifier.border(2.dp, Primary, CircleShape)
                        else Modifier.border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(0.2f), CircleShape)
                    )
                    .clickable { onPickCustomGradient() },
                contentAlignment = Alignment.Center
            ) {
                if (isCustomSelected) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.3f))
                    )
                    Icon(Icons.Default.CheckCircle, null, tint = Primary)
                } else {
                    Icon(Icons.Default.Add, contentDescription = "Custom", tint = Color.White)
                }
            }
        }

        items(gradients) { (start, end) ->
            SelectionItem(
                isSelected = current is BackgroundType.Gradient && current.startColor == start,
                onClick = { onSelect(BackgroundType.Gradient(start, end)) }
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
            label = stringResource(R.string.original),
            aspectRatio = 1f,
            modifier = Modifier.weight(1f)
        ) {
            Box(Modifier.fillMaxSize().background(Color.DarkGray), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.original), color = Color.White.copy(0.5f), fontWeight = FontWeight.Bold)
            }
        }

        // Custom
        SelectionItem(
            isSelected = current is BackgroundType.CustomImage,
            onClick = onPickCustom,
            label = stringResource(R.string.custom_color),
            aspectRatio = 1f,
            modifier = Modifier.weight(1f)
        ) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun BlurControl(current: BackgroundType, onSelect: (BackgroundType) -> Unit) {
    // Initialize from current background if it's Blur, otherwise default to 10f
    var sliderValue by remember { 
        mutableFloatStateOf(
            if (current is BackgroundType.Blur) current.intensity else 10f
        ) 
    }
    
    // Debounce updates to prevent UI blocking
    LaunchedEffect(sliderValue) {
        // Check if update is needed
        val currentIntensity = (current as? BackgroundType.Blur)?.intensity
        if (currentIntensity == sliderValue) return@LaunchedEffect
        
        delay(100) // 100ms debounce
        onSelect(BackgroundType.Blur(sliderValue))
    }
    
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.blur_intensity), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 16.dp))
        
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
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
                    else Modifier.border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(0.2f), RoundedCornerShape(12.dp))
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
            Text(label, color = if (isSelected) Primary else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun ExitConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = stringResource(R.string.exit_confirm_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Text(
                text = stringResource(R.string.exit_confirm_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(stringResource(R.string.btn_leave), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(stringResource(R.string.btn_stay))
            }
        }
    )
}
