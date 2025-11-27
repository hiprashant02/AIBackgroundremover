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
    val opacity: Float = 1.0f,      // Brush opacity (0-1)
    val spacing: Float = 0.15f      // Stroke spacing multiplier (0.1-0.5)
) {
    companion object {
        const val MIN_SIZE = 10f
        const val MAX_SIZE = 200f
        const val MIN_HARDNESS = 0.1f
        const val MAX_HARDNESS = 1.0f
        const val MIN_OPACITY = 0.1f
        const val MAX_OPACITY = 1.0f
        const val MIN_SPACING = 0.1f
        const val MAX_SPACING = 0.5f

        // Brush presets
        val PRESET_DETAIL = BrushTool(
            mode = BrushMode.ERASE,
            size = 20f,
            hardness = 0.9f,
            opacity = 1.0f,
            spacing = 0.1f
        )

        val PRESET_SOFT = BrushTool(
            mode = BrushMode.ERASE,
            size = 80f,
            hardness = 0.3f,
            opacity = 0.7f,
            spacing = 0.2f
        )

        val PRESET_HAIR = BrushTool(
            mode = BrushMode.RESTORE,
            size = 30f,
            hardness = 0.4f,
            opacity = 0.8f,
            spacing = 0.15f
        )

        val PRESET_HARD = BrushTool(
            mode = BrushMode.ERASE,
            size = 50f,
            hardness = 1.0f,
            opacity = 1.0f,
            spacing = 0.15f
        )

        val PRESET_ERASER = BrushTool(
            mode = BrushMode.ERASE,
            size = 100f,
            hardness = 0.6f,
            opacity = 1.0f,
            spacing = 0.2f
        )
    }
}

/**
 * Drawing path for manual editing with metadata
 */
data class DrawingPath(
    val points: List<DrawingPoint>,
    val brushTool: BrushTool,
    val timestamp: Long = System.currentTimeMillis(),
    val id: String = java.util.UUID.randomUUID().toString()
)

/**
 * Single point in a drawing path
 */
data class DrawingPoint(
    val x: Float,
    val y: Float,
    val pressure: Float = 1.0f
)

/**
 * Brush preset definition
 */
data class BrushPreset(
    val name: String,
    val description: String,
    val brushTool: BrushTool,
    val icon: String = "brush" // Icon identifier
)

