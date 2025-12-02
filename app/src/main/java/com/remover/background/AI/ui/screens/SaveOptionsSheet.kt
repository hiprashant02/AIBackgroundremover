package com.remover.background.AI.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.remover.background.AI.model.BackgroundType
import com.remover.background.AI.ui.theme.Primary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveOptionsSheet(
    currentBackground: BackgroundType,
    onSave: (Bitmap.CompressFormat) -> Unit,
    onDismiss: () -> Unit
) {
    val isImageBackground = currentBackground is BackgroundType.Original || 
                           currentBackground is BackgroundType.CustomImage ||
                           currentBackground is BackgroundType.Blur

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E1E),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.Gray) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                "Save Image",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )

            Text(
                if (isImageBackground) "Image background detected. Saving as high-quality JPG."
                else "Choose a format to save your image:",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // PNG Option (Only for non-image backgrounds)
                if (!isImageBackground) {
                    SaveFormatOption(
                        title = "PNG",
                        subtitle = "Transparent Background",
                        icon = Icons.Default.Image,
                        isSelected = false,
                        onClick = { onSave(Bitmap.CompressFormat.PNG) },
                        modifier = Modifier.weight(1f)
                    )
                }

                // JPG Option
                SaveFormatOption(
                    title = "JPG",
                    subtitle = if (isImageBackground) "Full Quality" else "White Background",
                    icon = Icons.Default.Photo,
                    isSelected = isImageBackground, 
                    onClick = { onSave(Bitmap.CompressFormat.JPEG) },
                    modifier = Modifier.weight(1f)
                )
            }
            
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun SaveFormatOption(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) Primary.copy(alpha = 0.1f) else Color(0xFF2A2A2A))
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) Primary else Color.Transparent,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (isSelected) Primary else Color.Gray,
            modifier = Modifier.size(32.dp)
        )
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
