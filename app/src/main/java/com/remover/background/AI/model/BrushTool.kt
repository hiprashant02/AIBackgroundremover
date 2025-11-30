package com.remover.background.AI.model

import androidx.compose.ui.graphics.Color

/**
 * Brush tool mode for manual editing
 */
enum class BrushMode {
    ERASE,      // Remove background (make transparent)
    RESTORE     // Restore/keep background
}

/**
 * Brush tool configuration
 */
data class BrushTool(
    val mode: BrushMode = BrushMode.ERASE,
    val size: Float = 50f,          // Brush size in pixels
    val hardness: Float = 0.8f,     // Edge hardness (0-1)
    val opacity: Float = 1.0f       // Brush opacity (0-1)
) {
    companion object {
        const val MIN_SIZE = 3f
        const val MAX_SIZE = 200f
        const val MIN_HARDNESS = 0.1f
        const val MAX_HARDNESS = 1.0f
        const val MIN_OPACITY = 0.1f
        const val MAX_OPACITY = 1.0f
    }
}

/**
 * Drawing path for manual editing
 */
data class DrawingPath(
    val points: List<DrawingPoint>,
    val brushTool: BrushTool
)

/**
 * Single point in a drawing path
 */
data class DrawingPoint(
    val x: Float,
    val y: Float,
    val pressure: Float = 1.0f
)

