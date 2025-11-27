package com.remover.background.AI.ui.components

import androidx.compose.animation.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.remover.background.AI.model.BrushMode
import com.remover.background.AI.model.BrushPreset
import com.remover.background.AI.model.BrushTool

/**
 * Enhanced brush control panel with presets, undo/redo, and advanced controls
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedBrushControlPanel(
    brushTool: BrushTool,
    brushPresets: List<BrushPreset>,
    canUndoStroke: Boolean,
    canRedoStroke: Boolean,
    strokeCount: Int,
    onBrushToolChange: (BrushTool) -> Unit,
    onPresetSelected: (BrushPreset) -> Unit,
    onUndoStroke: () -> Unit,
    onRedoStroke: () -> Unit,
    onToggleMode: () -> Unit,
    onClearStrokes: () -> Unit,
    onSmoothMask: () -> Unit,
    onApplyStrokes: () -> Unit,
    onDone: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showAdvanced by remember { mutableStateOf(false) }
    var showPresets by remember { mutableStateOf(false) }

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
            // Header with stroke count
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
                        "Strokes: $strokeCount • Pinch to zoom • Double tap to reset",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onCancel) {
                        Text("Cancel")
                    }
                    Button(onClick = onDone) {
                        Text("Done")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Stroke Undo/Redo Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedIconButton(
                    onClick = onUndoStroke,
                    enabled = canUndoStroke,
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Undo, "Undo Stroke", modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Undo", style = MaterialTheme.typography.labelMedium)
                    }
                }

                OutlinedIconButton(
                    onClick = onRedoStroke,
                    enabled = canRedoStroke,
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Redo, "Redo Stroke", modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Redo", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Brush Mode Toggle
            Text(
                "Brush Mode",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                // Erase Mode
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(4.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (brushTool.mode == BrushMode.ERASE)
                                Color.Red.copy(alpha = 0.2f)
                            else Color.Transparent
                        )
                        .border(
                            width = if (brushTool.mode == BrushMode.ERASE) 2.dp else 0.dp,
                            color = if (brushTool.mode == BrushMode.ERASE) Color.Red else Color.Transparent,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable { onBrushToolChange(brushTool.copy(mode = BrushMode.ERASE)) },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Delete,
                            "Erase",
                            tint = if (brushTool.mode == BrushMode.ERASE) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            "Erase",
                            color = if (brushTool.mode == BrushMode.ERASE) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (brushTool.mode == BrushMode.ERASE) FontWeight.Bold else FontWeight.Normal,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                // Restore Mode
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(4.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (brushTool.mode == BrushMode.RESTORE)
                                Color.Green.copy(alpha = 0.2f)
                            else Color.Transparent
                        )
                        .border(
                            width = if (brushTool.mode == BrushMode.RESTORE) 2.dp else 0.dp,
                            color = if (brushTool.mode == BrushMode.RESTORE) Color.Green else Color.Transparent,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable { onBrushToolChange(brushTool.copy(mode = BrushMode.RESTORE)) },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Brush,
                            "Restore",
                            tint = if (brushTool.mode == BrushMode.RESTORE) Color.Green else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            "Restore",
                            color = if (brushTool.mode == BrushMode.RESTORE) Color.Green else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (brushTool.mode == BrushMode.RESTORE) FontWeight.Bold else FontWeight.Normal,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Brush Presets
            TextButton(
                onClick = { showPresets = !showPresets },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (showPresets) "Hide Presets" else "Show Brush Presets")
                Icon(
                    if (showPresets) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }

            AnimatedVisibility(visible = showPresets) {
                Column {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(brushPresets) { preset ->
                            BrushPresetCard(
                                preset = preset,
                                onClick = { onPresetSelected(preset) }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

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
                        "Hardness: ${(brushTool.hardness * 100).toInt()}%",
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
                        "Opacity: ${(brushTool.opacity * 100).toInt()}%",
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

@Composable
private fun BrushPresetCard(
    preset: BrushPreset,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.width(120.dp),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Brush,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                preset.name,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                preset.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

