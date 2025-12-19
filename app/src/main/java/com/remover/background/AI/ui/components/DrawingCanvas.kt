package com.remover.background.AI.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.remover.background.AI.model.BrushMode
import com.remover.background.AI.model.BrushTool
import com.remover.background.AI.model.DrawingPath
import com.remover.background.AI.model.DrawingPoint

@Composable
fun DrawingCanvas(
    bitmap: Bitmap,
    brushTool: BrushTool,
    isEnabled: Boolean = true,
    showCheckerboard: Boolean = false,
    onDrawingPath: (DrawingPath) -> Unit,
    onDisplayScaleChanged: (Float) -> Unit = {},
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

    // Calculate and report display scale factor
    LaunchedEffect(imageBounds, bitmap) {
        if (imageBounds.width > 0 && bitmap.width > 0) {
            val displayScale = imageBounds.width / bitmap.width.toFloat()
            onDisplayScaleChanged(displayScale)
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
                                // DISCARD current stroke when switching to zoom gesture
                                // (don't save - the first finger of a zoom shouldn't draw)
                                isDrawing = false
                                currentPath = emptyList()
                                cursorPosition = null

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
                                        // Finger just pressed down - start new stroke
                                        isDrawing = true
                                        currentPath = listOf(imagePos)
                                        change.consume()
                                    } else if (change.pressed && isDrawing) {
                                        // Finger is moving while pressed
                                        // Add point if it moved enough from the last point
                                        val lastPoint = currentPath.lastOrNull()
                                        val shouldAddPoint = if (lastPoint != null) {
                                            val dx = imagePos.x - lastPoint.x
                                            val dy = imagePos.y - lastPoint.y
                                            val distSq = dx * dx + dy * dy
                                            // Very low threshold for responsive drawing
                                            distSq > 0.00001f  // ~0.3% of image dimension
                                        } else {
                                            true
                                        }
                                        
                                        if (shouldAddPoint) {
                                            currentPath = currentPath + imagePos
                                        }
                                        change.consume()
                                    } else if (!change.pressed && isDrawing) {
                                        // Finger lifted - save stroke
                                        if (currentPath.isNotEmpty()) {
                                            onDrawingPath(DrawingPath(currentPath, brushTool))
                                        }
                                        isDrawing = false
                                        currentPath = emptyList()
                                        cursorPosition = null
                                    }
                                } else {
                                    // Touch outside image bounds - DON'T terminate stroke!
                                    // Just don't add points. Stroke continues when finger comes back.
                                    if (change.pressed && isDrawing) {
                                        // Still drawing, just outside bounds - do nothing, keep drawing
                                        change.consume()
                                    } else if (!change.pressed && isDrawing) {
                                        // Finger lifted while outside - save what we have
                                        if (currentPath.isNotEmpty()) {
                                            onDrawingPath(DrawingPath(currentPath, brushTool))
                                        }
                                        isDrawing = false
                                        currentPath = emptyList()
                                        cursorPosition = null
                                    }
                                }
                            } else {
                                // No touches - complete any in-progress stroke
                                if (isDrawing) {
                                    if (currentPath.isNotEmpty()) {
                                        onDrawingPath(DrawingPath(currentPath, brushTool))
                                    }
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
            // Draw Checkerboard ONLY within image bounds (if enabled)
            if (showCheckerboard && imageBounds != androidx.compose.ui.geometry.Rect.Zero) {
                clipRect(
                    left = imageBounds.left,
                    top = imageBounds.top,
                    right = imageBounds.right,
                    bottom = imageBounds.bottom
                ) {
                    val squareSize = 20.dp.toPx()
                    val numCols = (imageBounds.width / squareSize).toInt() + 1
                    val numRows = (imageBounds.height / squareSize).toInt() + 1
                    
                    for (row in 0..numRows) {
                        for (col in 0..numCols) {
                            val isLightSquare = (row + col) % 2 == 0
                            drawRect(
                                color = if (isLightSquare) Color(0xFFFFFFFF) else Color(0xFFE0E0E0),
                                topLeft = Offset(imageBounds.left + col * squareSize, imageBounds.top + row * squareSize),
                                size = Size(squareSize, squareSize)
                            )
                        }
                    }
                }
            }
            
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
                    // Optimization: Skip points that are identical to previous point (handled by drop(1) logic implicitly but being safe)
                    path.lineTo(
                        imageBounds.left + p.x * imageBounds.width,
                        imageBounds.top + p.y * imageBounds.height
                    )
                }

                // Don't scale by image ratio - brush size is in screen pixels (relative to the displayed image)
                val strokeWidth = brushTool.size
                val color = if (brushTool.mode == BrushMode.ERASE) Color.Red.copy(0.5f) else Color.Green.copy(0.5f)

                // Use StrokeJoin.Round to prevent "spikes" (sharp triangles) on sharp turns
                drawPath(
                    path, 
                    color, 
                    style = Stroke(
                        width = strokeWidth, 
                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                        join = androidx.compose.ui.graphics.StrokeJoin.Round
                    )
                )
            }

            // Draw Cursor
            if (cursorPosition != null && isEnabled && imageBounds != androidx.compose.ui.geometry.Rect.Zero) {
                // Determine untransformed drawing coordinates so the cursor appears exactly under the finger
                // visual_x = drawn_x * scale + offsetX
                // drawn_x = (visual_x - offsetX) / scale
                val drawX = (cursorPosition!!.x - offsetX) / scale
                val drawY = (cursorPosition!!.y - offsetY) / scale
                val drawPos = Offset(drawX, drawY)

                // Don't scale radius by image ratio - brush size is in screen pixels
                // But DO scale inverse by 'scale' so visual size remains constant relative to screen?
                // NO, we want brush to look "attached" to the image zoom level.
                // If we draw at 'radius', the graphicsLayer scales it by 'scale'.
                // So visual_radius = radius * scale.
                
                // If we want visual size = brushTool.size (constant screen size):
                // radius = (brushTool.size / 2) / scale
                
                // If we want visual size = brushTool.size * scale (zooms with image):
                // radius = brushTool.size / 2
                
                // Current logic implies we want it to zoom (since effect scales with zoom).
                val radius = brushTool.size / 2
                val color = if (brushTool.mode == BrushMode.ERASE) Color.Red.copy(0.3f) else Color.Green.copy(0.3f)

                drawCircle(color, radius, drawPos)
                drawCircle(color.copy(alpha=0.8f), 2f / scale, drawPos) // Center dot (inverse scaled to remain sharp)
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