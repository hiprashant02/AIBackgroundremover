package com.remover.background.AI.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.remover.background.AI.model.BrushMode
import com.remover.background.AI.model.BrushTool
import com.remover.background.AI.model.DrawingPath
import com.remover.background.AI.model.DrawingPoint

/**
 * Optimized drawing canvas with smooth zoom and proper brush application
 */
@Composable
fun DrawingCanvas(
    bitmap: Bitmap,
    brushTool: BrushTool,
    isEnabled: Boolean = true,
    onDrawingPath: (DrawingPath) -> Unit,
    modifier: Modifier = Modifier
) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var currentPath by remember { mutableStateOf(listOf<DrawingPoint>()) }
    var isDrawing by remember { mutableStateOf(false) }

    // Zoom and pan state
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // Calculate image display bounds to maintain aspect ratio
    val imageAspectRatio = remember(bitmap) { bitmap.width.toFloat() / bitmap.height.toFloat() }
    val imageBounds = remember(canvasSize, imageAspectRatio) {
        if (canvasSize.width == 0 || canvasSize.height == 0) {
            androidx.compose.ui.geometry.Rect.Zero
        } else {
            calculateImageBounds(canvasSize, imageAspectRatio)
        }
    }

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { size ->
                    canvasSize = size
                }
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                )
                // Zoom gestures (two fingers)
                .pointerInput(Unit) {
                    detectTransformGestures(
                        panZoomLock = true
                    ) { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)

                        if (scale > 1f) {
                            val maxOffsetX = (canvasSize.width * (scale - 1)) / 2
                            val maxOffsetY = (canvasSize.height * (scale - 1)) / 2

                            offsetX = (offsetX + pan.x).coerceIn(-maxOffsetX, maxOffsetX)
                            offsetY = (offsetY + pan.y).coerceIn(-maxOffsetY, maxOffsetY)
                        } else {
                            offsetX = 0f
                            offsetY = 0f
                        }
                    }
                }
                // Drawing gestures (single finger) and double tap
                .pointerInput(isEnabled, brushTool, imageBounds, scale, offsetX, offsetY) {
                    if (!isEnabled) return@pointerInput

                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()

                            when (event.changes.size) {
                                1 -> {
                                    // Single finger - drawing
                                    val change = event.changes.first()

                                    when {
                                        change.pressed && !change.previousPressed -> {
                                            // Start drawing
                                            val imagePos = screenToImageCoordinates(
                                                change.position,
                                                imageBounds,
                                                scale,
                                                Offset(offsetX, offsetY)
                                            )

                                            if (imagePos != null) {
                                                isDrawing = true
                                                currentPath = listOf(imagePos)
                                                change.consume()
                                            }
                                        }
                                        change.pressed && change.positionChanged() && isDrawing -> {
                                            // Continue drawing
                                            val imagePos = screenToImageCoordinates(
                                                change.position,
                                                imageBounds,
                                                scale,
                                                Offset(offsetX, offsetY)
                                            )

                                            if (imagePos != null) {
                                                currentPath = currentPath + imagePos
                                                change.consume()
                                            }
                                        }
                                        !change.pressed && isDrawing -> {
                                            // End drawing
                                            if (currentPath.isNotEmpty()) {
                                                val path = DrawingPath(
                                                    points = currentPath,
                                                    brushTool = brushTool
                                                )
                                                onDrawingPath(path)
                                            }
                                            isDrawing = false
                                            currentPath = emptyList()
                                        }
                                    }
                                }
                                else -> {
                                    // Multiple fingers - stop drawing
                                    if (isDrawing) {
                                        isDrawing = false
                                        currentPath = emptyList()
                                    }
                                }
                            }
                        }
                    }
                }
                // Double tap to reset zoom
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (scale > 1f) {
                                scale = 1f
                                offsetX = 0f
                                offsetY = 0f
                            } else {
                                scale = 2.5f
                            }
                        }
                    )
                }
        ) {
            // Draw the image with proper aspect ratio
            drawIntoCanvas { canvas ->
                if (imageBounds != androidx.compose.ui.geometry.Rect.Zero) {
                    val destRect = android.graphics.RectF(
                        imageBounds.left,
                        imageBounds.top,
                        imageBounds.right,
                        imageBounds.bottom
                    )

                    canvas.nativeCanvas.drawBitmap(
                        bitmap,
                        null,
                        destRect,
                        null
                    )
                }
            }

            // Draw current path preview
            if (currentPath.isNotEmpty() && imageBounds != androidx.compose.ui.geometry.Rect.Zero) {
                val path = Path()
                val firstPoint = currentPath.first()
                path.moveTo(
                    imageBounds.left + firstPoint.x * imageBounds.width,
                    imageBounds.top + firstPoint.y * imageBounds.height
                )

                for (i in 1 until currentPath.size) {
                    val point = currentPath[i]
                    path.lineTo(
                        imageBounds.left + point.x * imageBounds.width,
                        imageBounds.top + point.y * imageBounds.height
                    )
                }

                // Draw preview stroke
                val previewColor = when (brushTool.mode) {
                    BrushMode.ERASE -> Color.Red.copy(alpha = 0.5f)
                    BrushMode.RESTORE -> Color.Green.copy(alpha = 0.5f)
                }

                drawPath(
                    path = path,
                    color = previewColor,
                    style = Stroke(width = brushTool.size / scale)
                )

                // Draw brush circles at points (show less for performance)
                if (currentPath.size <= 50) {
                    currentPath.forEach { point ->
                        drawCircle(
                            color = previewColor,
                            radius = brushTool.size / (2 * scale),
                            center = Offset(
                                imageBounds.left + point.x * imageBounds.width,
                                imageBounds.top + point.y * imageBounds.height
                            ),
                            alpha = 0.3f
                        )
                    }
                } else {
                    // Show only last few points for performance
                    currentPath.takeLast(20).forEach { point ->
                        drawCircle(
                            color = previewColor,
                            radius = brushTool.size / (2 * scale),
                            center = Offset(
                                imageBounds.left + point.x * imageBounds.width,
                                imageBounds.top + point.y * imageBounds.height
                            ),
                            alpha = 0.3f
                        )
                    }
                }
            }
        }
    }
}

/**
 * Calculate image bounds to maintain aspect ratio (fit mode)
 */
private fun calculateImageBounds(canvasSize: IntSize, imageAspectRatio: Float): androidx.compose.ui.geometry.Rect {
    if (canvasSize.width == 0 || canvasSize.height == 0) {
        return androidx.compose.ui.geometry.Rect.Zero
    }

    val canvasAspectRatio = canvasSize.width.toFloat() / canvasSize.height.toFloat()

    val (width, height) = if (imageAspectRatio > canvasAspectRatio) {
        // Image is wider - fit to width
        val width = canvasSize.width.toFloat()
        val height = width / imageAspectRatio
        width to height
    } else {
        // Image is taller - fit to height
        val height = canvasSize.height.toFloat()
        val width = height * imageAspectRatio
        width to height
    }

    val left = (canvasSize.width - width) / 2
    val top = (canvasSize.height - height) / 2

    return androidx.compose.ui.geometry.Rect(
        left = left,
        top = top,
        right = left + width,
        bottom = top + height
    )
}

/**
 * Convert screen coordinates to normalized image coordinates (0-1)
 */
private fun screenToImageCoordinates(
    screenPos: Offset,
    imageBounds: androidx.compose.ui.geometry.Rect,
    scale: Float,
    offset: Offset
): DrawingPoint? {
    if (imageBounds == androidx.compose.ui.geometry.Rect.Zero) return null

    // Adjust for zoom and pan
    val adjustedX = (screenPos.x - offset.x) / scale
    val adjustedY = (screenPos.y - offset.y) / scale

    // Check if within image bounds
    if (adjustedX < imageBounds.left || adjustedX > imageBounds.right ||
        adjustedY < imageBounds.top || adjustedY > imageBounds.bottom) {
        return null
    }

    // Normalize to 0-1 range relative to image
    val normalizedX = (adjustedX - imageBounds.left) / imageBounds.width
    val normalizedY = (adjustedY - imageBounds.top) / imageBounds.height

    return DrawingPoint(
        x = normalizedX.coerceIn(0f, 1f),
        y = normalizedY.coerceIn(0f, 1f)
    )
}

/**
 * Simplified drawing overlay that shows brush cursor
 */
@Composable
fun DrawingOverlay(
    brushTool: BrushTool,
    isEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    var cursorPosition by remember { mutableStateOf<Offset?>(null) }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(isEnabled) {
                if (!isEnabled) return@pointerInput

                detectDragGestures(
                    onDragStart = { offset ->
                        cursorPosition = offset
                    },
                    onDrag = { change, _ ->
                        cursorPosition = change.position
                    },
                    onDragEnd = {
                        cursorPosition = null
                    },
                    onDragCancel = {
                        cursorPosition = null
                    }
                )
            }
    ) {
        // Draw brush cursor
        cursorPosition?.let { position ->
            val cursorColor = when (brushTool.mode) {
                BrushMode.ERASE -> Color.Red
                BrushMode.RESTORE -> Color.Green
            }

            // Outer circle
            drawCircle(
                color = cursorColor,
                radius = brushTool.size / 2,
                center = position,
                style = Stroke(width = 2f),
                alpha = 0.8f
            )

            // Inner crosshair
            drawCircle(
                color = cursorColor,
                radius = 2f,
                center = position,
                alpha = 0.8f
            )
        }
    }
}

