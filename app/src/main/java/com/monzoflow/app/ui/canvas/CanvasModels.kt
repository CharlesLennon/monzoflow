package com.monzoflow.app.ui.canvas

import androidx.compose.ui.geometry.Offset

data class CanvasViewport(
    val panX: Float = 0f,
    val panY: Float = 0f,
    val scale: Float = 1.0f
) {
    fun toScreen(canvasX: Float, canvasY: Float): Offset {
        return Offset(
            x = (canvasX * scale) + panX,
            y = (canvasY * scale) + panY
        )
    }

    fun toCanvas(screenX: Float, screenY: Float): Offset {
        return Offset(
            x = (screenX - panX) / scale,
            y = (screenY - panY) / scale
        )
    }
}

data class ActiveWireDrag(
    val fromNodeId: String,
    val fromPortId: String,
    val startPos: Offset,
    val currentDragPos: Offset
)
