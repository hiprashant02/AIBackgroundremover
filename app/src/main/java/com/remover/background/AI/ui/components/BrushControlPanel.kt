package com.remover.background.AI.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.remover.background.AI.model.BrushMode
import com.remover.background.AI.model.BrushTool
import com.remover.background.AI.ui.theme.Primary

private enum class BrushProperty {
    SIZE, HARDNESS, OPACITY
}

@OptIn(ExperimentalAnimationApi::class)
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
    var activeProperty by remember { mutableStateOf(BrushProperty.SIZE) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF0A0A0A)) // Deep dark background
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .navigationBarsPadding() // Respect gesture bar
    ) {
        // 1. Top Row: Mode Toggle & Action Buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Cancel Button
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.Close, "Cancel", tint = Color.White)
            }

            // Mode Toggle (Erase / Restore)
            Row(
                modifier = Modifier
                    .height(40.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF1E1E1E))
                    .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(50))
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isErase = brushTool.mode == BrushMode.ERASE
                
                // Erase Tab
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .background(if (isErase) Color(0xFFEF5350) else Color.Transparent)
                        .clickable { onBrushToolChange(brushTool.copy(mode = BrushMode.ERASE)) }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Erase",
                        color = Color.White,
                        fontWeight = if (isErase) FontWeight.Bold else FontWeight.Medium,
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                // Restore Tab
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .background(if (!isErase) Color(0xFF66BB6A) else Color.Transparent)
                        .clickable { onBrushToolChange(brushTool.copy(mode = BrushMode.RESTORE)) }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Restore",
                        color = Color.White,
                        fontWeight = if (!isErase) FontWeight.Bold else FontWeight.Medium,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            // Done Button
            IconButton(
                onClick = onDone,
                modifier = Modifier
                    .background(Primary, CircleShape)
                    .size(40.dp)
            ) {
                Icon(Icons.Default.Check, "Done", tint = Color.White)
            }
        }

        // 2. Property Selector (Size, Hardness, Opacity)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            PropertyTab(
                icon = Icons.Default.Circle,
                label = "Size",
                value = "${brushTool.size.toInt()}",
                isSelected = activeProperty == BrushProperty.SIZE,
                onClick = { activeProperty = BrushProperty.SIZE }
            )
            PropertyTab(
                icon = Icons.Default.BlurOn,
                label = "Hardness",
                value = "${(brushTool.hardness * 100).toInt()}%",
                isSelected = activeProperty == BrushProperty.HARDNESS,
                onClick = { activeProperty = BrushProperty.HARDNESS }
            )
            PropertyTab(
                icon = Icons.Default.Opacity,
                label = "Opacity",
                value = "${(brushTool.opacity * 100).toInt()}%",
                isSelected = activeProperty == BrushProperty.OPACITY,
                onClick = { activeProperty = BrushProperty.OPACITY }
            )
        }

        // 3. Active Slider
        AnimatedContent(targetState = activeProperty, label = "slider_anim") { property ->
            Column(modifier = Modifier.fillMaxWidth()) {
                when (property) {
                    BrushProperty.SIZE -> {
                        AestheticSlider(
                            value = brushTool.size,
                            onValueChange = { onBrushToolChange(brushTool.copy(size = it)) },
                            valueRange = 10f..200f
                        )
                    }
                    BrushProperty.HARDNESS -> {
                        AestheticSlider(
                            value = brushTool.hardness,
                            onValueChange = { onBrushToolChange(brushTool.copy(hardness = it)) },
                            valueRange = 0.1f..1f
                        )
                    }
                    BrushProperty.OPACITY -> {
                        AestheticSlider(
                            value = brushTool.opacity,
                            onValueChange = { onBrushToolChange(brushTool.copy(opacity = it)) },
                            valueRange = 0.1f..1f
                        )
                    }
                }
            }
        }
        
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun PropertyTab(
    icon: ImageVector,
    label: String,
    value: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Icon(
            icon,
            null,
            tint = if (isSelected) Primary else Color.Gray,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) Color.White else Color.Gray
        )
        Text(
            value,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) Primary else Color.Gray.copy(0.7f),
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun AestheticSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        colors = SliderDefaults.colors(
            thumbColor = Color.White,
            activeTrackColor = Primary,
            inactiveTrackColor = Color(0xFF333333)
        ),
        modifier = Modifier.height(40.dp)
    )
}
