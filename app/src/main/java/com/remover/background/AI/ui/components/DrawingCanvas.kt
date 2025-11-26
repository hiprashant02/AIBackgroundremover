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
                // FIX 1: Move pointerInput BEFORE graphicsLayer
                // This ensures we get RAW screen coordinates, making your manual math in
                // screenToImageCoordinates correct.
                .pointerInput(isEnabled, brushTool, imageBounds) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()

                            when {
                                // Two-finger gesture: Zoom and Pan
                                event.changes.size >= 2 -> {
                                    if (isDrawing) {
                                        isDrawing = false
                                        currentPath = emptyList()
                                    }

                                    val pressed = event.changes.filter { it.pressed }
                                    if (pressed.size >= 2) {
                                        val p1 = pressed[0]
                                        val p2 = pressed[1]

                                        if (p1.previousPressed && p2.previousPressed) {
                                            val oldDist = (p1.previousPosition - p2.previousPosition).getDistance()
                                            val newDist = (p1.position - p2.position).getDistance()
                                            if (oldDist > 10f) {
                                                val zoomChange = newDist / oldDist
                                                scale = (scale * zoomChange).coerceIn(1f, 10f)
                                            }

                                            val p1Movement = p1.position - p1.previousPosition
                                            val p2Movement = p2.position - p2.previousPosition
                                            val pan = (p1Movement + p2Movement) / 2f
                                            if (scale > 1f) {
                                                offsetX += pan.x
                                                offsetY += pan.y
                                            }
                                        }

                                        if (scale <= 1f) {
                                            scale = 1f
                                            offsetX = 0f
                                            offsetY = 0f
                                        }
                                        pressed.forEach { it.consume() }
                                    }
                                }

                                // Single-finger gesture: Drawing
                                event.changes.size == 1 && isEnabled -> {
                                    val change = event.changes.first()

                                    // With pointerInput before graphicsLayer, `change.position` is untransformed screen coords.
                                    // Your screenToImageCoordinates math will now work correctly.
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
                // GraphicsLayer is now applied AFTER pointer input, only affecting visual drawing
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                )
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

            // Draw the Brush Preview Path
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

                // FIX 2: Correct Brush Preview Size scaling
                // We calculate how many screen pixels represent 1 bitmap pixel
                val bitmapToScreenScale = if (bitmap.width > 0) imageBounds.width / bitmap.width.toFloat() else 1f

                // We apply that ratio to the brush size.
                // We do NOT divide by 'scale' (zoom) because the graphicsLayer scales the drawing for us.
                val visualStrokeWidth = brushTool.size * bitmapToScreenScale

                drawPath(
                    path,
                    previewColor,
                    style = Stroke(
                        width = visualStrokeWidth,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                )
            }
        }
    }
}

// Keep helper functions exactly as they were, they are correct now that modifier order is fixed.
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

    val transformedX = ((screenPos.x - offset.x - cx) / scale) + cx
    val transformedY = ((screenPos.y - offset.y - cy) / scale) + cy

    if (transformedX < imageBounds.left || transformedX > imageBounds.right ||
        transformedY < imageBounds.top || transformedY > imageBounds.bottom) {
        return null
    }

    val normalizedX = (transformedX - imageBounds.left) / imageBounds.width
    val normalizedY = (transformedY - imageBounds.top) / imageBounds.height

    return DrawingPoint(normalizedX.coerceIn(0f, 1f), normalizedY.coerceIn(0f, 1f))
}