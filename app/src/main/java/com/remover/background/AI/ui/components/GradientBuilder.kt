package com.remover.background.AI.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun GradientBuilder(
    startColor: Color,
    endColor: Color,
    angle: Float,
    onStartColorClick: () -> Unit,
    onEndColorClick: () -> Unit,
    onAngleChange: (Float) -> Unit,
    onAngleChangeFinished: () -> Unit,
    onSwapColors: () -> Unit,
    onPreviewClick: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Preview
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    brush = remember(startColor, endColor, angle) {
                        object : ShaderBrush() {
                             override fun createShader(size: androidx.compose.ui.geometry.Size): androidx.compose.ui.graphics.Shader {
                                 val w = size.width
                                 val h = size.height
                                 // Angle 0 = Top to Bottom (Standard)
                                 // In math, 0 is Right.
                                 // We want 0 to be Top->Bottom.
                                 // So we rotate by 90 degrees?
                                 // Let's use standard CSS linear-gradient angle: 0deg = Bottom to Top? No, 180 is Bottom to Top.
                                 // Let's stick to: 0 = Top->Bottom.
                                 
                                 val r = angle * (PI / 180.0)
                                 val cx = w / 2
                                 val cy = h / 2
                                 // Diagonal length
                                 val d = sqrt((w*w + h*h).toDouble()) / 2
                                 
                                 // Start point (opposite to direction)
                                 val sx = cx - d * cos(r)
                                 val sy = cy - d * sin(r)
                                 // End point (direction)
                                 val ex = cx + d * cos(r)
                                 val ey = cy + d * sin(r)
                                 
                                 return androidx.compose.ui.graphics.LinearGradientShader(
                                     from = androidx.compose.ui.geometry.Offset(sx.toFloat(), sy.toFloat()),
                                     to = androidx.compose.ui.geometry.Offset(ex.toFloat(), ey.toFloat()),
                                     colors = listOf(startColor, endColor),
                                     tileMode = TileMode.Clamp
                                 )
                             }
                        }
                    }
                )
                .border(1.dp, Color.White.copy(0.2f), RoundedCornerShape(12.dp))
                .then(
                    if (onPreviewClick != null) {
                        Modifier.clickable { onPreviewClick() }
                    } else {
                        Modifier
                    }
                )
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Color Pickers
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ColorCircle(color = startColor, label = "Start", onClick = onStartColorClick)
            
            IconButton(onClick = onSwapColors) {
                Icon(Icons.Default.SwapHoriz, contentDescription = "Swap", tint = MaterialTheme.colorScheme.onSurface)
            }
            
            ColorCircle(color = endColor, label = "End", onClick = onEndColorClick)
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Angle Slider
        Text("Angle: ${angle.toInt()}°", color = MaterialTheme.colorScheme.onSurface)
        Slider(
            value = angle,
            onValueChange = onAngleChange,
            onValueChangeFinished = onAngleChangeFinished,
            valueRange = 0f..360f,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary, 
                activeTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}

@Composable
fun ColorCircle(color: Color, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(color)
                .border(2.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f), CircleShape)
                .clickable(onClick = onClick)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(label, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
    }
}
