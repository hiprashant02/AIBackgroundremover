package com.remover.background.AI.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.remover.background.AI.model.BrushMode
import com.remover.background.AI.model.BrushTool
import com.remover.background.AI.model.DrawingPath
import com.remover.background.AI.model.DrawingPoint

@Composable
fun DrawingCanvas(
    bitmap: Bitmap,
    brushTool: BrushTool,
    isEnabled: Boolean = true,
    onDrawingPath: (DrawingPath) -> Unit,
    modifier: Modifier = Modifier
) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    // Zoom and pan state
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var isDrawing by remember { mutableStateOf(false) }
    var currentPath by remember { mutableStateOf(emptyList<DrawingPoint>()) }
    var cursorPosition by remember { mutableStateOf<Offset?>(null) }

    DisposableEffect(Unit) {
        scale = 1f
        offsetX = 0f
        offsetY = 0f
        isDrawing = false
        currentPath = emptyList()
        onDispose { }
    }

    val imageAspectRatio = remember(bitmap) { bitmap.width.toFloat() / bitmap.height.toFloat() }
    val imageBounds = remember(canvasSize, imageAspectRatio) {
        if (canvasSize.width == 0 || canvasSize.height == 0) {
            androidx.compose.ui.geometry.Rect.Zero
        } else {
            calculateImageBounds(canvasSize, imageAspectRatio)
        }
    }

    Box(modifier = modifier.fillMaxSize().clipToBounds()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { size -> canvasSize = size }
                // Pointer Input BEFORE graphicsLayer to get raw coordinates
                .pointerInput(isEnabled, brushTool, imageBounds) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()

                            // Handling Multitouch (Zoom/Pan)
                            if (event.changes.size >= 2) {
                                // Save current stroke only if it has multiple points (prevents dots)
                                if (isDrawing && currentPath.size > 1) {
                                    onDrawingPath(DrawingPath(currentPath, brushTool))
                                }
                                isDrawing = false
                                currentPath = emptyList()

                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.size >= 2) {
                                    val p1 = pressed[0]
                                    val p2 = pressed[1]
                                    if (p1.previousPressed && p2.previousPressed) {
                                        val oldDist = (p1.previousPosition - p2.previousPosition).getDistance()
                                        val newDist = (p1.position - p2.position).getDistance()
                                        if (oldDist > 10f) {
                                            scale = (scale * (newDist / oldDist)).coerceIn(1f, 10f)
                                        }
                                        val pan = ((p1.position - p1.previousPosition) + (p2.position - p2.previousPosition)) / 2f
                                        if (scale > 1f) {
                                            offsetX += pan.x
                                            offsetY += pan.y
                                        }
                                    }
                                    if (scale <= 1f) {
                                        scale = 1f; offsetX = 0f; offsetY = 0f
                                    }
                                    pressed.forEach { it.consume() }
                                }
                            }
                            // Handling Single Touch (Drawing)
                            else if (event.changes.size == 1 && isEnabled) {
                                val change = event.changes.first()
                                cursorPosition = change.position // Update cursor preview

                                val imagePos = screenToImageCoordinates(
                                    change.position, imageBounds, scale, Offset(offsetX, offsetY), canvasSize
                                )

                                if (imagePos != null) {
                                    if (change.pressed && !change.previousPressed) {
                                        isDrawing = true
                                        currentPath = listOf(imagePos)
                                        change.consume()
                                    } else if (change.pressed && change.positionChanged() && isDrawing) {
                                        currentPath = currentPath + imagePos
                                        change.consume()
                                    } else if (!change.pressed && isDrawing) {
                                        // Save all strokes including single taps
                                        if (currentPath.isNotEmpty()) {
                                            onDrawingPath(DrawingPath(currentPath, brushTool))
                                        }
                                        isDrawing = false
                                        currentPath = emptyList()
                                        cursorPosition = null
                                    }
                                } else {
                                    // Touch outside image bounds - only save multi-point strokes
                                    if (isDrawing && currentPath.size > 1) {
                                        onDrawingPath(DrawingPath(currentPath, brushTool))
                                    }
                                    isDrawing = false
                                    currentPath = emptyList()
                                    cursorPosition = null
                                }
                            } else {
                                // No touches - complete any in-progress stroke
                                if (isDrawing && currentPath.size > 1) {
                                    onDrawingPath(DrawingPath(currentPath, brushTool))
                                    isDrawing = false
                                    currentPath = emptyList()
                                }
                                
                                cursorPosition = null
                            }
                        }
                    }
                }
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                )
        ) {
            // Draw Image
            drawIntoCanvas { canvas ->
                if (imageBounds != androidx.compose.ui.geometry.Rect.Zero) {
                    val destRect = android.graphics.RectF(imageBounds.left, imageBounds.top, imageBounds.right, imageBounds.bottom)
                    canvas.nativeCanvas.drawBitmap(bitmap, null, destRect, null)
                }
            }

            // Draw Brush Preview Path
            if (currentPath.isNotEmpty() && imageBounds != androidx.compose.ui.geometry.Rect.Zero) {
                val path = Path()
                path.moveTo(
                    imageBounds.left + currentPath.first().x * imageBounds.width,
                    imageBounds.top + currentPath.first().y * imageBounds.height
                )
                currentPath.drop(1).forEach { p ->
                    path.lineTo(
                        imageBounds.left + p.x * imageBounds.width,
                        imageBounds.top + p.y * imageBounds.height
                    )
                }

                val scaleRatio = if (bitmap.width > 0) imageBounds.width / bitmap.width.toFloat() else 1f
                val strokeWidth = brushTool.size * scaleRatio
                val color = if (brushTool.mode == BrushMode.ERASE) Color.Red.copy(0.5f) else Color.Green.copy(0.5f)

                drawPath(path, color, style = Stroke(width = strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round))
            }

            // Draw Cursor
            if (cursorPosition != null && isEnabled && imageBounds != androidx.compose.ui.geometry.Rect.Zero) {
                val scaleRatio = if (bitmap.width > 0) imageBounds.width / bitmap.width.toFloat() else 1f
                val radius = (brushTool.size * scaleRatio) / 2
                val color = if (brushTool.mode == BrushMode.ERASE) Color.Red.copy(0.3f) else Color.Green.copy(0.3f)

                drawCircle(color, radius, cursorPosition!!)
                drawCircle(color.copy(alpha=0.8f), 2f, cursorPosition!!) // Center dot
            }
        }
    }
}

// Helpers
private fun calculateImageBounds(canvasSize: IntSize, imageAspectRatio: Float): androidx.compose.ui.geometry.Rect {
    val canvasAspectRatio = canvasSize.width.toFloat() / canvasSize.height.toFloat()
    val (width, height) = if (imageAspectRatio > canvasAspectRatio) {
        val w = canvasSize.width.toFloat()
        w to w / imageAspectRatio
    } else {
        val h = canvasSize.height.toFloat()
        h * imageAspectRatio to h
    }
    val left = (canvasSize.width - width) / 2
    val top = (canvasSize.height - height) / 2
    return androidx.compose.ui.geometry.Rect(left, top, left + width, top + height)
}

private fun screenToImageCoordinates(
    screenPos: Offset,
    imageBounds: androidx.compose.ui.geometry.Rect,
    scale: Float,
    offset: Offset,
    canvasSize: IntSize
): DrawingPoint? {
    if (imageBounds.isEmpty) return null
    val cx = canvasSize.width / 2f
    val cy = canvasSize.height / 2f

    // Reverse Transform
    val transformedX = ((screenPos.x - offset.x - cx) / scale) + cx
    val transformedY = ((screenPos.y - offset.y - cy) / scale) + cy

    if (transformedX < imageBounds.left || transformedX > imageBounds.right ||
        transformedY < imageBounds.top || transformedY > imageBounds.bottom) return null

    val normX = (transformedX - imageBounds.left) / imageBounds.width
    val normY = (transformedY - imageBounds.top) / imageBounds.height

    return DrawingPoint(normX.coerceIn(0f, 1f), normY.coerceIn(0f, 1f))
}