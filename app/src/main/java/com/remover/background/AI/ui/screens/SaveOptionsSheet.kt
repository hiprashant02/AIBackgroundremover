package com.remover.background.AI.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.remover.background.AI.R
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
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.onSurfaceVariant) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                stringResource(R.string.save_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )

            Text(
                if (isImageBackground) "Image background detected. Saving as high-quality JPG."
                else "Choose a format to save your image:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val isTransparent = currentBackground is BackgroundType.Transparent

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // PNG Option (Only for transparent backgrounds)
                if (isTransparent) {
                    SaveFormatOption(
                        title = stringResource(R.string.save_format_png),
                        subtitle = stringResource(R.string.save_format_png_desc),
                        icon = Icons.Default.Image,
                        isSelected = false,
                        onClick = { onSave(Bitmap.CompressFormat.PNG) },
                        modifier = Modifier.weight(1f)
                    )
                }

                // JPG Option
                SaveFormatOption(
                    title = stringResource(R.string.save_format_jpg),
                    subtitle = if (isTransparent) stringResource(R.string.save_format_jpg_desc_transparent) else stringResource(R.string.save_format_jpg_desc_full_quality),
                    icon = Icons.Default.Photo,
                    isSelected = !isTransparent, 
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
            .background(if (isSelected) Primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant)
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
            tint = if (isSelected) Primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(32.dp)
        )
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
fun SaveSuccessDialog(
    onEditNewImage: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(48.dp)
            )
        },
        title = {
            Text(
                text = stringResource(R.string.save_success_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        },
        text = {
            Text(
                text = stringResource(R.string.save_success_message),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        },
        confirmButton = {
            Button(
                onClick = onEditNewImage,
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.btn_edit_new))
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.btn_keep_editing))
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
