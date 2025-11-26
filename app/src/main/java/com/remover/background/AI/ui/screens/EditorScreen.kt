package com.remover.background.AI.ui.screens

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.remover.background.AI.model.BackgroundType
import com.remover.background.AI.model.BrushMode
import com.remover.background.AI.ui.components.BrushControlPanel
import com.remover.background.AI.ui.components.DrawingCanvas
import com.remover.background.AI.ui.theme.*
import com.remover.background.AI.viewmodel.EditorState
import com.remover.background.AI.viewmodel.EditorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val editorState = viewModel.editorState
    val isProcessing = viewModel.isProcessing
    val isSaving = viewModel.isSaving
    val currentBackground = viewModel.currentBackground
    val canUndo = viewModel.canUndo
    val canRedo = viewModel.canRedo
    val isManualEditMode = viewModel.isManualEditMode
    val currentBrushTool = viewModel.currentBrushTool

    var showBackgroundPicker by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Edit Image",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    // Undo
                    IconButton(
                        onClick = { viewModel.undo() },
                        enabled = canUndo && !isProcessing
                    ) {
                        Icon(
                            Icons.Default.Undo,
                            "Undo",
                            tint = if (canUndo && !isProcessing)
                                MaterialTheme.colorScheme.onSurface
                            else
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    }

                    // Redo
                    IconButton(
                        onClick = { viewModel.redo() },
                        enabled = canRedo && !isProcessing
                    ) {
                        Icon(
                            Icons.Default.Redo,
                            "Redo",
                            tint = if (canRedo && !isProcessing)
                                MaterialTheme.colorScheme.onSurface
                            else
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    }

                    // Manual Edit
                    IconButton(
                        onClick = {
                            if (isManualEditMode) {
                                viewModel.exitManualEditMode(applyChanges = true)
                            } else {
                                viewModel.enterManualEditMode()
                            }
                        },
                        enabled = editorState is EditorState.Success && !isProcessing
                    ) {
                        Icon(
                            if (isManualEditMode) Icons.Default.Check else Icons.Default.Edit,
                            if (isManualEditMode) "Apply Edits" else "Manual Edit",
                            tint = if (editorState is EditorState.Success && !isProcessing) {
                                if (isManualEditMode) Color.Green else MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            }
                        )
                    }

                    // Save
                    IconButton(
                        onClick = { showSaveDialog = true },
                        enabled = editorState is EditorState.Success && !isSaving && !isManualEditMode
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Default.Save,
                                "Save",
                                tint = if (editorState is EditorState.Success)
                                    MaterialTheme.colorScheme.onSurface
                                else
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            if (editorState is EditorState.Success) {
                if (isManualEditMode) {
                    // Show brush control panel in manual edit mode
                    BrushControlPanel(
                        brushTool = currentBrushTool,
                        onBrushToolChange = { newTool ->
                            viewModel.updateBrushTool(
                                mode = newTool.mode,
                                size = newTool.size,
                                hardness = newTool.hardness,
                                opacity = newTool.opacity
                            )
                        },
                        onClearStrokes = { viewModel.clearBrushStrokes() },
                        onSmoothMask = { viewModel.smoothMask() },
                        onApplyStrokes = { viewModel.applyStrokes() },
                        onDone = { viewModel.exitManualEditMode(applyChanges = true) },
                        onCancel = { viewModel.exitManualEditMode(applyChanges = false) }
                    )
                } else {
                    // Show background picker button normally
                    BottomAppBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        Button(
                            onClick = { showBackgroundPicker = true },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isProcessing,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Primary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.ColorLens, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Change Background")
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (editorState) {
                is EditorState.Idle -> {
                    // This shouldn't happen in editor screen
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No image loaded")
                    }
                }

                is EditorState.Loading, EditorState.Idle -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(64.dp),
                                color = Primary,
                                strokeWidth = 6.dp
                            )
                            Text(
                                "Processing image with AI...",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "This may take a few seconds",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                is EditorState.Success -> {
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Image Preview with Drawing Canvas
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(16.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            Color(0xFFEEEEEE),
                                            Color(0xFFCCCCCC)
                                        )
                                    )
                                )
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(20.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isManualEditMode) {
                                // Drawing canvas for manual editing
                                DrawingCanvas(
                                    bitmap = editorState.bitmap,
                                    brushTool = currentBrushTool,
                                    isEnabled = !isProcessing,
                                    onDrawingPath = { path ->
                                        viewModel.addBrushStroke(path)
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )

                                // Brush mode indicator
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(16.dp)
                                ) {
                                    Surface(
                                        color = when (currentBrushTool.mode) {
                                            BrushMode.ERASE -> Color.Red.copy(alpha = 0.9f)
                                            BrushMode.RESTORE -> Color.Green.copy(alpha = 0.9f)
                                        },
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                when (currentBrushTool.mode) {
                                                    BrushMode.ERASE -> Icons.Default.Delete
                                                    BrushMode.RESTORE -> Icons.Default.Brush
                                                },
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Text(
                                                when (currentBrushTool.mode) {
                                                    BrushMode.ERASE -> "Erase"
                                                    BrushMode.RESTORE -> "Restore"
                                                },
                                                color = Color.White,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            } else {
                                // Normal image preview
                                Image(
                                    bitmap = editorState.bitmap.asImageBitmap(),
                                    contentDescription = "Edited Image",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            }

                            // Processing overlay
                            if (isProcessing) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.5f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        strokeWidth = 4.dp
                                    )
                                }
                            }
                        }
                    }
                }

                is EditorState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(
                                "Error",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                editorState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(onClick = onBackClick) {
                                Text("Go Back")
                            }
                        }
                    }
                }
            }
        }
    }

    // Background Picker Bottom Sheet
    if (showBackgroundPicker) {
        BackgroundPickerSheet(
            currentBackground = currentBackground,
            onBackgroundSelected = { background ->
                viewModel.applyBackground(background)
                showBackgroundPicker = false
            },
            onDismiss = { showBackgroundPicker = false }
        )
    }

    // Save Dialog
    if (showSaveDialog) {
        SaveDialog(
            onSave = { format ->
                viewModel.saveBitmap(format) { result ->
                    result.fold(
                        onSuccess = {
                            Toast.makeText(context, "Image saved successfully!", Toast.LENGTH_SHORT).show()
                        },
                        onFailure = { error ->
                            Toast.makeText(context, "Failed to save: ${error.message}", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
                showSaveDialog = false
            },
            onDismiss = { showSaveDialog = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackgroundPickerSheet(
    currentBackground: BackgroundType,
    onBackgroundSelected: (BackgroundType) -> Unit,
    onDismiss: () -> Unit
) {
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

