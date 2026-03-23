package com.remover.background.AI.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Star
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily

import com.remover.background.AI.R
import com.remover.background.AI.ui.components.BannerAd
import com.remover.background.AI.ui.components.LanguageSelector
import com.remover.background.AI.ui.theme.Primary
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.launch

val OutfitFont = FontFamily(Font(R.font.outfit_medium))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onImageSelected: (Uri) -> Unit,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    currentLanguage: String,
    onLanguageSelected: (String) -> Unit,
    onAboutClick: () -> Unit, // Kept for compatibility but not used for navigation anymore
    onPrivacyClick: () -> Unit
) {
    val context = LocalContext.current

    // Launchers
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { onImageSelected(it) } }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            try {
                val file = File(context.cacheDir, "captured_image_${System.currentTimeMillis()}.jpg")
                val stream = FileOutputStream(file)
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 100, stream)
                stream.close()
                onImageSelected(Uri.fromFile(file))
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted -> if (isGranted) cameraLauncher.launch(null) }

    // Background Animation
    val infiniteTransition = rememberInfiniteTransition(label = "background_anim")
    val gradientOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2000f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "gradient_offset"
    )

    val contentColor = MaterialTheme.colorScheme.onBackground
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val primaryColor = Primary

    val drawerWidth = 300.dp
    val drawerWidthPx = with(LocalDensity.current) { drawerWidth.toPx() }

    // Drawer Automation
    var isDrawerOpen by remember { mutableStateOf(false) }
    val drawerAnimatable = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    LaunchedEffect(isDrawerOpen) {
        drawerAnimatable.animateTo(
            targetValue = if (isDrawerOpen) 1f else 0f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }

    // Gesture Logic
    val draggableModifier = Modifier.draggable(
        orientation = Orientation.Horizontal,
        state = rememberDraggableState { delta ->
             val newVal = (drawerAnimatable.value * drawerWidthPx + delta) / drawerWidthPx
             scope.launch {
                 drawerAnimatable.snapTo(newVal.coerceIn(0f, 1f))
             }
        },
        onDragStopped = { velocity ->
             val isOpening = !isDrawerOpen
             val threshold = if (isOpening) 0.2f else 0.8f // Easier to open/close (20% drag)
             
             // Velocity threshold: 300px/s (moderate sensitivity)
             val targetOpen = when {
                 velocity > 300f -> true // Fling Right -> Open
                 velocity < -300f -> false // Fling Left -> Close
                 else -> if (isOpening) drawerAnimatable.value > threshold else drawerAnimatable.value > threshold
             }
             
             if (targetOpen != isDrawerOpen) {
                 isDrawerOpen = targetOpen
             } else {
                 // Snap back if threshold not met
                  scope.launch {
                     drawerAnimatable.animateTo(
                         targetValue = if (isDrawerOpen) 1f else 0f,
                         animationSpec = spring(
                             dampingRatio = Spring.DampingRatioNoBouncy,
                             stiffness = Spring.StiffnessLow
                         )
                     )
                 }
             }
        }
    )

    // Root Container
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(draggableModifier) // Applied to root to handle gestures everywhere (opening and closing)
            .background(MaterialTheme.colorScheme.surfaceVariant) // Background for the Drawer
    ) {
        // 1. DRAWER CONTENT (Behind Main Screen)
        // -----------------------------------------------------------------------
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(end = 60.dp) // Leave space for the main screen overlap
                .graphicsLayer {
                    alpha = 0.5f + (0.5f * drawerAnimatable.value) // Fade in
                    scaleX = 0.9f + (0.1f * drawerAnimatable.value) // Scale up slightly
                    scaleY = 0.9f + (0.1f * drawerAnimatable.value)
                    translationX = (1f - drawerAnimatable.value) * -100f // Parallax slide
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            ) {
                Spacer(Modifier.height(40.dp))
                
                // Drawer Content Layout
                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                    // Header with Icon
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                             painter = painterResource(id = R.drawable.app_icon),
                             contentDescription = null,
                             modifier = Modifier.fillMaxSize(),
                             tint = Color.Unspecified
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Version 3.0",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(32.dp))
                
                // About Description
                Text(
                    text = stringResource(R.string.about_app_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )

                Spacer(Modifier.height(32.dp))
                
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 24.dp), 
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                )
                
                Spacer(Modifier.height(24.dp))
                
                // Menu Items
                Column(modifier = Modifier.padding(horizontal = 12.dp)) {
                    // Rate App
                    DrawerMenuItem(
                        icon = Icons.Outlined.Star,
                        text = stringResource(R.string.rate_app),
                        onClick = {
                            val packageName = context.packageName
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")))
                            } catch (e: android.content.ActivityNotFoundException) {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")))
                            }
                        }
                    )
                    
                    // Share App
                    DrawerMenuItem(
                        icon = Icons.Outlined.Share,
                        text = stringResource(R.string.share_app),
                        onClick = {
                             val packageName = context.packageName
                             val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                 type = "text/plain"
                                 putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.app_name))
                                 putExtra(Intent.EXTRA_TEXT, context.getString(R.string.share_message) + "\nhttps://play.google.com/store/apps/details?id=$packageName")
                             }
                             context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share_app)))
                        }
                    )

                    // Contact
                     DrawerMenuItem(
                        icon = Icons.Outlined.Email,
                        text = stringResource(R.string.contact_title),
                        onClick = {
                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("mailto:${context.getString(R.string.developer_email)}")
                            }
                            context.startActivity(intent)
                        }
                    )
                    
                    // Privacy Policy
                    DrawerMenuItem(
                        icon = Icons.Outlined.Lock,
                        text = stringResource(R.string.privacy_policy),
                        onClick = {
                            isDrawerOpen = false
                            onPrivacyClick()
                        }
                    )
                }
                
                Spacer(Modifier.height(100.dp))
            }
        }


        // 2. MAIN SCREEN CONTENT (Foreground)
        // -----------------------------------------------------------------------
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val scale = 1f - (0.15f * drawerAnimatable.value)
                    scaleX = scale
                    scaleY = scale
                    translationX = drawerAnimatable.value * drawerWidthPx
                    val cornerRadius = 32.dp.toPx()
                    shape = RoundedCornerShape(cornerRadius * drawerAnimatable.value)
                    clip = true
                    shadowElevation = (10.dp.toPx() * drawerAnimatable.value)
                }
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Tap to Close overlay
            if (isDrawerOpen) {
                 Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(2f)
                        .clickable(
                            indication = null, 
                            interactionSource = remember { MutableInteractionSource() }
                        ) { isDrawerOpen = false }
                )
            }
            
            // MAIN HOME UI CONTENT
            // -----------------------------------------------------------
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // Clean Background
                // Gradient removed as requested (was too 'vibe coded')

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                ) {
                    // Header Row
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 8.dp)
                    ) {
                        // Menu Button
                        IconButton(
                            onClick = { isDrawerOpen = !isDrawerOpen },
                            modifier = Modifier
                                .size(48.dp)
                                .align(Alignment.TopStart)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                        ) {
                            Icon(
                                imageVector = if(isDrawerOpen) Icons.Default.Close else Icons.Default.Menu,
                                contentDescription = "Menu",
                                modifier = Modifier.size(24.dp),
                                tint = contentColor.copy(alpha = 0.8f)
                            )
                        }

                        // BRANDED TOOLBAR TITLE
                        Text(
                            text = stringResource(R.string.hero_title).replace("\n", " "),
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontFamily = OutfitFont,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = (-0.5).sp
                            ),
                            color = contentColor,
                            modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp)
                        )

                        Column(
                            modifier = Modifier.align(Alignment.TopEnd),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalAlignment = Alignment.End
                        ) {
                            // Theme Toggle
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .clickable { onToggleTheme() }
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
                                    contentDescription = "Theme",
                                    modifier = Modifier.size(20.dp),
                                    tint = contentColor
                                )
                            }

                            // Language Selector
                            LanguageSelector(
                                currentLanguage = currentLanguage,
                                onLanguageSelected = onLanguageSelected
                            )

                        }
                    }

                    Spacer(Modifier.weight(0.15f))

                    // Main Hero Text REMOVED (Moved to Toolbar)
                    Spacer(Modifier.weight(0.1f)) // Adjusted spacer
                    
                    Text(
                        text = stringResource(R.string.hero_subtitle),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = OutfitFont,
                            fontWeight = FontWeight.Medium
                        ),
                        color = contentColor.copy(alpha = 0.7f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp)
                    )

                    Spacer(Modifier.weight(0.2f))

                    // Action Buttons (Vertical Stack now)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        AestheticActionButton(
                            text = stringResource(R.string.action_gallery_title),
                            subtitle = stringResource(R.string.action_gallery_subtitle),
                            icon = Icons.Outlined.Image,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                imagePickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        )

                        AestheticActionButton(
                            text = stringResource(R.string.action_camera_title),
                            subtitle = stringResource(R.string.action_camera_subtitle),
                            icon = Icons.Outlined.PhotoCamera,
                            modifier = Modifier.fillMaxWidth(),
                            isPrimary = true, // Keep primary accent if desired, or make uniform. User said "horizontal list items", didn't strictly say remove primary color, but stroke border usually implies uniform look. I'll stick to stroke for both as requested "replace soft shadows with a 1dp stroke border". I will adapt the component to handle primary/secondary via tint or border color, but structure is same.
                            onClick = {
                                val permission = android.Manifest.permission.CAMERA
                                if (androidx.core.content.ContextCompat.checkSelfPermission(
                                        context, permission
                                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                ) {
                                    cameraLauncher.launch(null)
                                } else {
                                    permissionLauncher.launch(permission)
                                }
                            }
                        )
                    }
                    
                    Spacer(Modifier.weight(0.1f))

                    // Banner Ad
                     BannerAd(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// UI COMPONENTS
// ═══════════════════════════════════════════════════════════════════════════

@Composable
fun DrawerMenuItem(icon: ImageVector, text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface, // High emphasis
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium, // Clearer visibility
            color = MaterialTheme.colorScheme.onSurface // High emphasis
        )
    }
}

@Composable
fun AestheticActionButton(
    text: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false,
    onClick: () -> Unit
) {
    // Subtle bounce animation on press
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        label = "scale"
    )

    Surface(
        modifier = modifier
            .scale(scale)
            .clickable {
                isPressed = true
                onClick()
            },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f), // Glassy feel
        border = androidx.compose.foundation.BorderStroke(
            1.dp, 
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f) // 1dp stroke border
        )
    ) {
        // Reset press state
        LaunchedEffect(isPressed) {
            if (isPressed) {
                kotlinx.coroutines.delay(100)
                isPressed = false
            }
        }

        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon Container
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isPrimary) Primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isPrimary) Primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = OutfitFont,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = OutfitFont
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Optional Chevron
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// UI COMPONENTS (Start of Helper Components)
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
