package com.remover.background.AI.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.remover.background.AI.model.BrushMode
import com.remover.background.AI.model.BrushTool
import com.remover.background.AI.ui.theme.Primary

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
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(Color(0xFF1E1E1E))
            .padding(24.dp)
    ) {
        // Mode Switch
        Row(modifier = Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp)).background(Color.Black.copy(0.5f))) {
            val isErase = brushTool.mode == BrushMode.ERASE
            Box(
                modifier = Modifier.weight(1f).fillMaxHeight().padding(4.dp).clip(RoundedCornerShape(20.dp))
                    .background(if (isErase) Color(0xFFE57373) else Color.Transparent)
                    .clickable { onBrushToolChange(brushTool.copy(mode = BrushMode.ERASE)) },
                contentAlignment = Alignment.Center
            ) { Text("Erase", color = Color.White, fontWeight = FontWeight.Bold) }

            Box(
                modifier = Modifier.weight(1f).fillMaxHeight().padding(4.dp).clip(RoundedCornerShape(20.dp))
                    .background(if (!isErase) Color(0xFF81C784) else Color.Transparent)
                    .clickable { onBrushToolChange(brushTool.copy(mode = BrushMode.RESTORE)) },
                contentAlignment = Alignment.Center
            ) { Text("Restore", color = Color.White, fontWeight = FontWeight.Bold) }
        }

        Spacer(Modifier.height(24.dp))

        // Sliders
        Text("Size", color = Color.Gray)
        Slider(
            value = brushTool.size,
            onValueChange = { onBrushToolChange(brushTool.copy(size = it)) },
            valueRange = 10f..200f,
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Primary)
        )

        Spacer(Modifier.height(16.dp))

        // Footer Actions
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onCancel) { Text("Cancel", color = Color.Gray) }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                IconButton(onClick = onSmoothMask, modifier = Modifier.background(Color.White.copy(0.1f), CircleShape)) {
                    Icon(Icons.Default.BlurOn, null, tint = Color.White)
                }
                Button(onClick = onDone, colors = ButtonDefaults.buttonColors(containerColor = Primary)) {
                    Text("Done")
                }
            }
        }
    }
}