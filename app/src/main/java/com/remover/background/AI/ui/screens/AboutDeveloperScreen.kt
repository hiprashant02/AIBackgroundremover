package com.remover.background.AI.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.remover.background.AI.R
import com.remover.background.AI.ui.theme.Primary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutDeveloperScreen(
    onBackClick: () -> Unit,
    onPrivacyPolicyClick: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        stringResource(R.string.about_developer_title),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
        ) {
            Spacer(Modifier.height(8.dp))
            
            // ═══════════════════════════════════════════════════════
            // LITTICHOKHA STUDIO HEADER
            // ═══════════════════════════════════════════════════════
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Studio Logo - "L"
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "L",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                
                Spacer(Modifier.height(20.dp))
                
                Text(
                    text = stringResource(R.string.studio_name),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                
                Spacer(Modifier.height(6.dp))
                
                Text(
                    text = stringResource(R.string.studio_tagline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // ═══════════════════════════════════════════════════════
            // ABOUT THIS APP SECTION
            // ═══════════════════════════════════════════════════════
            SectionHeader(title = stringResource(R.string.about_app_title))
            
            GroupedCard {
                Text(
                    text = stringResource(R.string.about_app_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 22.sp
                )
            }
            
            Spacer(Modifier.height(28.dp))
            
            // ═══════════════════════════════════════════════════════
            // DEVELOPER SECTION
            // ═══════════════════════════════════════════════════════
            SectionHeader(title = stringResource(R.string.about_us_title))
            
            GroupedCard {
                Text(
                    text = stringResource(R.string.about_us_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 22.sp
                )
                
                Spacer(Modifier.height(12.dp))
                
                Text(
                    text = stringResource(R.string.developed_by),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = Primary
                )
            }
            
            Spacer(Modifier.height(28.dp))
            
            // ═══════════════════════════════════════════════════════
            // CONTACT SECTION
            // ═══════════════════════════════════════════════════════
            SectionHeader(title = stringResource(R.string.contact_title))
            
            GroupedCard(
                modifier = Modifier.clickable {
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("mailto:${context.getString(R.string.developer_email)}")
                    }
                    context.startActivity(intent)
                }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.email_us),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.developer_email),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            
            Spacer(Modifier.height(12.dp))
            
            // Privacy Policy Button
            GroupedCard(
                modifier = Modifier.clickable { onPrivacyPolicyClick() }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.privacy_policy),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            
            Spacer(Modifier.height(40.dp))
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// iOS-STYLE COMPONENTS
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.Normal,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 0.5.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 36.dp)
            .padding(bottom = 8.dp)
    )
}

@Composable
private fun GroupedCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}
