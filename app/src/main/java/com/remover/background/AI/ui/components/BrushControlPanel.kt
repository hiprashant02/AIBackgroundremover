package com.remover.background.AI.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.remover.background.AI.model.BrushMode
import com.remover.background.AI.model.BrushTool

/**
 * Bottom sheet for brush tool controls
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrushControlPanel(
    brushTool: BrushTool,
    onBrushToolChange: (BrushTool) -> Unit,
    onClearStrokes: () -> Unit,
    onSmoothMask: () -> Unit,
    onApplyStrokes: () -> Unit,
    onDone: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showAdvanced by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Manual Edit",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Pinch to zoom • Double tap to reset",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(onClick = onCancel) {
                        Text("Cancel")
                    }
                    Button(onClick = onDone) {
                        Text("Done")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Brush Mode Selection
            Text(
                "Brush Mode",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Erase Mode
                BrushModeButton(
                    icon = Icons.Default.Delete,
                    label = "Erase",
                    isSelected = brushTool.mode == BrushMode.ERASE,
                    selectedColor = Color.Red,
                    onClick = {
                        onBrushToolChange(brushTool.copy(mode = BrushMode.ERASE))
                    },
                    modifier = Modifier.weight(1f)
                )

                // Restore Mode
                BrushModeButton(
                    icon = Icons.Default.Brush,
                    label = "Restore",
                    isSelected = brushTool.mode == BrushMode.RESTORE,
                    selectedColor = Color.Green,
                    onClick = {
                        onBrushToolChange(brushTool.copy(mode = BrushMode.RESTORE))
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(16.dp))

            // Brush Size
            Text(
                "Brush Size: ${brushTool.size.toInt()}px",
                style = MaterialTheme.typography.bodyMedium
            )

            Slider(
                value = brushTool.size,
                onValueChange = { newSize ->
                    onBrushToolChange(brushTool.copy(size = newSize))
                },
                valueRange = BrushTool.MIN_SIZE..BrushTool.MAX_SIZE,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            // Advanced Settings Toggle
            TextButton(
                onClick = { showAdvanced = !showAdvanced },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (showAdvanced) "Hide Advanced" else "Show Advanced")
                Icon(
                    if (showAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Advanced Settings
            AnimatedVisibility(visible = showAdvanced) {
                Column {
                    Spacer(Modifier.height(8.dp))

                    // Hardness
                    Text(
                        "Brush Hardness: ${(brushTool.hardness * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Slider(
                        value = brushTool.hardness,
                        onValueChange = { newHardness ->
                            onBrushToolChange(brushTool.copy(hardness = newHardness))
                        },
                        valueRange = BrushTool.MIN_HARDNESS..BrushTool.MAX_HARDNESS,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(8.dp))

                    // Opacity
                    Text(
                        "Brush Opacity: ${(brushTool.opacity * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Slider(
                        value = brushTool.opacity,
                        onValueChange = { newOpacity ->
                            onBrushToolChange(brushTool.copy(opacity = newOpacity))
                        },
                        valueRange = BrushTool.MIN_OPACITY..BrushTool.MAX_OPACITY,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onApplyStrokes,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Apply", style = MaterialTheme.typography.labelMedium)
                }

                OutlinedButton(
                    onClick = onClearStrokes,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Clear, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Clear", style = MaterialTheme.typography.labelMedium)
                }

                OutlinedButton(
                    onClick = onSmoothMask,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.AutoFixHigh, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Smooth", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

/**
 * Brush mode selection button
 */
@Composable
private fun BrushModeButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    selectedColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isSelected) {
        selectedColor.copy(alpha = 0.2f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val contentColor = if (isSelected) {
        selectedColor
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    val borderColor = if (isSelected) {
        selectedColor
    } else {
        Color.Transparent
    }

    Surface(
        onClick = onClick,
        modifier = modifier
            .height(80.dp)
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            ),
        color = backgroundColor,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

/**
 * Compact brush size indicator
 */
@Composable
fun BrushSizeIndicator(
    size: Float,
    mode: BrushMode,
    modifier: Modifier = Modifier
) {
    val color = when (mode) {
        BrushMode.ERASE -> Color.Red
        BrushMode.RESTORE -> Color.Green
    }

    Box(
        modifier = modifier
            .size(80.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
            .border(2.dp, color, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size((size / 2).coerceIn(10f, 40f).dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.5f))
                .border(1.dp, color, CircleShape)
        )
    }
}

