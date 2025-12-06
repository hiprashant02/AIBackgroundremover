package com.remover.background.AI.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.remover.background.AI.R

@Composable
fun ColorPicker(
    initialColor: Color,
    onColorChanged: (Color) -> Unit
) {
    // Convert initial color to HSV
    var hsv by remember {
        mutableStateOf(
            FloatArray(3).apply {
                android.graphics.Color.colorToHSV(initialColor.toArgb(), this)
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Saturation/Value Box
        SaturationValueBox(
            hue = hsv[0],
            saturation = hsv[1],
            value = hsv[2],
            onSaturationValueChanged = { s, v ->
                hsv = floatArrayOf(hsv[0], s, v)
                onColorChanged(Color(android.graphics.Color.HSVToColor(hsv)))
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
                .clip(RoundedCornerShape(12.dp))
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Hue Bar
        HueBar(
            hue = hsv[0],
            onHueChanged = { h ->
                hsv = floatArrayOf(h, hsv[1], hsv[2])
                onColorChanged(Color(android.graphics.Color.HSVToColor(hsv)))
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .clip(RoundedCornerShape(16.dp))
        )
        
        Spacer(modifier = Modifier.height(24.dp))

        // Preview & Hex Input Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Preview Circle
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(android.graphics.Color.HSVToColor(hsv)))
                    .border(2.dp, Color.White.copy(0.2f), CircleShape)
            )

            // Hex Input
            var hexText by remember(initialColor) { 
                mutableStateOf(
                    String.format("#%06X", (0xFFFFFF and initialColor.toArgb()))
                ) 
            }
            
            // Update hex text when color changes externally (drag)
            LaunchedEffect(hsv[0], hsv[1], hsv[2]) {
                val color = android.graphics.Color.HSVToColor(hsv)
                hexText = String.format("#%06X", (0xFFFFFF and color))
            }

            OutlinedTextField(
                value = hexText,
                onValueChange = { newValue ->
                    if (newValue.length <= 7) {
                        hexText = newValue.uppercase()
                        if (newValue.length == 7 && newValue.startsWith("#")) {
                            try {
                                val colorInt = android.graphics.Color.parseColor(newValue)
                                val newHsv = FloatArray(3)
                                android.graphics.Color.colorToHSV(colorInt, newHsv)
                                hsv = newHsv
                                onColorChanged(Color(colorInt))
                            } catch (e: IllegalArgumentException) {
                                // Invalid hex, ignore
                            }
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                label = { Text(stringResource(R.string.hex_code)) },
                placeholder = { Text(stringResource(R.string.hex_hint)) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    cursorColor = MaterialTheme.colorScheme.primary,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                ),
                shape = RoundedCornerShape(12.dp)
            )
        }
    }
}

@Composable
fun SaturationValueBox(
    hue: Float,
    saturation: Float,
    value: Float,
    onSaturationValueChanged: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        Canvas(modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    val s = (change.position.x / size.width).coerceIn(0f, 1f)
                    val v = 1f - (change.position.y / size.height).coerceIn(0f, 1f)
                    onSaturationValueChanged(s, v)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val s = (offset.x / size.width).coerceIn(0f, 1f)
                    val v = 1f - (offset.y / size.height).coerceIn(0f, 1f)
                    onSaturationValueChanged(s, v)
                }
            }
        ) {
            // Draw Hue background
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.White, Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f))))
                )
            )
            // Draw Value overlay (Black to Transparent vertical)
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black)
                )
            )
            
            // Draw Selector
            val x = saturation * size.width
            val y = (1f - value) * size.height
            drawCircle(
                color = Color.White,
                radius = 8.dp.toPx(),
                center = Offset(x, y),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
            )
            drawCircle(
                color = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value))),
                radius = 6.dp.toPx(),
                center = Offset(x, y)
            )
        }
    }
}

@Composable
fun HueBar(
    hue: Float,
    onHueChanged: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        Canvas(modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    val h = (change.position.x / size.width).coerceIn(0f, 1f) * 360f
                    onHueChanged(h)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val h = (offset.x / size.width).coerceIn(0f, 1f) * 360f
                    onHueChanged(h)
                }
            }
        ) {
            // Draw Rainbow
            val colors = (0..360).step(10).map { 
                Color(android.graphics.Color.HSVToColor(floatArrayOf(it.toFloat(), 1f, 1f))) 
            }
            drawRect(
                brush = Brush.horizontalGradient(colors)
            )
            
            // Draw Selector
            val x = (hue / 360f) * size.width
            drawCircle(
                color = Color.White,
                radius = 12.dp.toPx(),
                center = Offset(x, size.height / 2),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
            )
        }
    }
}
