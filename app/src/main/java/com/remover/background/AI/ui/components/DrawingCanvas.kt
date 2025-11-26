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
    var currentPath by remember { mutableStateOf(listOf<DrawingPoint>()) }
    var isDrawing by remember { mutableStateOf(false) }

    // Zoom and pan state
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

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
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                )
                // Pan and Zoom Gesture
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 10f)
                        if (scale > 1f) {
                            // Allow panning only when zoomed in
                            val maxOffsetX = (canvasSize.width * (scale - 1)) / 2
                            val maxOffsetY = (canvasSize.height * (scale - 1)) / 2

                            // Simple bounds check (optional, can be removed for free panning)
                            offsetX += pan.x
                            offsetY += pan.y
                        } else {
                            // Reset position if zoomed out
                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f
                        }
                    }
                }
                // Drawing Gesture
                .pointerInput(isEnabled, brushTool, imageBounds, scale, offsetX, offsetY) {
                    if (!isEnabled) return@pointerInput
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            when (event.changes.size) {
                                1 -> {
                                    val change = event.changes.first()

                                    // CRITICAL FIX: Correctly map screen touch to image coordinates
                                    // taking into account the Zoom (Scale) and Pan (Offset)
                                    val imagePos = screenToImageCoordinates(
                                        change.position,
                                        imageBounds,
                                        scale,
                                        Offset(offsetX, offsetY),
                                        canvasSize
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
                                            if (currentPath.isNotEmpty()) {
                                                onDrawingPath(DrawingPath(currentPath, brushTool))
                                            }
                                            isDrawing = false
                                            currentPath = emptyList()
                                        }
                                    }
                                }
                                else -> {
                                    if (isDrawing) {
                                        isDrawing = false
                                        currentPath = emptyList()
                                    }
                                }
                            }
                        }
                    }
                }
        ) {
            // Draw the Image
            drawIntoCanvas { canvas ->
                if (imageBounds != androidx.compose.ui.geometry.Rect.Zero) {
                    val destRect = android.graphics.RectF(
                        imageBounds.left, imageBounds.top,
                        imageBounds.right, imageBounds.bottom
                    )
                    canvas.nativeCanvas.drawBitmap(bitmap, null, destRect, null)
                }
            }

            // Draw the Brush Preview Path (scaled inversely so it stays constant size visually)
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

                val previewColor = if (brushTool.mode == BrushMode.ERASE)
                    Color.Red.copy(alpha=0.5f) else Color.Green.copy(alpha=0.5f)

                // Divide width by scale so the stroke doesn't get huge when you zoom in
                drawPath(
                    path,
                    previewColor,
                    style = Stroke(
                        width = brushTool.size / scale,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                )
            }
        }
    }
}

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

/**
 * Maps a touch on the screen to a 0..1 coordinate on the image.
 * Handles the Inverse Matrix Logic for Center-Pivoted Scaling.
 */
private fun screenToImageCoordinates(
    screenPos: Offset,
    imageBounds: androidx.compose.ui.geometry.Rect,
    scale: Float,
    offset: Offset,
    canvasSize: IntSize
): DrawingPoint? {
    if (imageBounds.isEmpty) return null

    // The center of the canvas is the pivot point for scaling
    val cx = canvasSize.width / 2f
    val cy = canvasSize.height / 2f

    // Formula: (ScreenPos - Translation - Pivot) / Scale + Pivot
    val transformedX = ((screenPos.x - offset.x - cx) / scale) + cx
    val transformedY = ((screenPos.y - offset.y - cy) / scale) + cy

    // Check if the touch is actually on the image
    if (transformedX < imageBounds.left || transformedX > imageBounds.right ||
        transformedY < imageBounds.top || transformedY > imageBounds.bottom) {
        return null
    }

    // Normalize to 0..1
    val normalizedX = (transformedX - imageBounds.left) / imageBounds.width
    val normalizedY = (transformedY - imageBounds.top) / imageBounds.height

    return DrawingPoint(normalizedX.coerceIn(0f, 1f), normalizedY.coerceIn(0f, 1f))
}