package com.remover.background.AI.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties

data class Language(
    val code: String,
    val name: String,
    val flag: String
)

val SUPPORTED_LANGUAGES = listOf(
    Language("en", "English", "🇺🇸"),
    Language("es", "Español", "🇪🇸"),
    Language("fr", "Français", "🇫🇷"),
    Language("de", "Deutsch", "🇩🇪"),
    Language("hi", "हिन्दी", "🇮🇳"),
    Language("zh", "中文", "🇨🇳"),
    Language("pt", "Português", "🇧🇷"),
    Language("in", "Bahasa Indonesia", "🇮🇩"),
    Language("ja", "日本語", "🇯🇵"),
    Language("ko", "한국어", "🇰🇷"),
    Language("ar", "العربية", "🇸🇦"),
    Language("tr", "Türkçe", "🇹🇷")
)

@Composable
fun LanguageSelector(
    currentLanguage: String,
    onLanguageSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    // Use remember with key to update when currentLanguage changes
    val selectedLanguage = remember(currentLanguage) {
        SUPPORTED_LANGUAGES.find { it.code == currentLanguage } 
            ?: SUPPORTED_LANGUAGES.first()
    }

    Box(modifier = modifier) {
        // Language button
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = selectedLanguage.flag,
                    style = MaterialTheme.typography.bodyLarge
                )
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = "Select Language",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Dropdown menu
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface)
                .widthIn(min = 160.dp)
        ) {
            SUPPORTED_LANGUAGES.forEach { language ->
                DropdownMenuItem(
                    text = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = language.flag,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = language.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (language.code == currentLanguage) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    },
                    onClick = {
                        onLanguageSelected(language.code)
                        expanded = false
                    }
                )
            }
        }
    }
}
