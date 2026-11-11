package com.monzoflow.app.ui.canvas

import androidx.compose.animation.core.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.abs
import kotlin.math.max

object ConnectionWire {

    fun drawWire(
        drawScope: DrawScope,
        start: Offset,
        end: Offset,
        color: Color = Color(0xFF38BDF8),
        pulseProgress: Float = 0f,
        strokeWidth: Float = 4f
    ) {
        val dx = max(abs(end.x - start.x) * 0.5f, 60f)
        val path = Path().apply {
            moveTo(start.x, start.y)
            cubicTo(
                x1 = start.x + dx,
                y1 = start.y,
                x2 = end.x - dx,
                y2 = end.y,
                x3 = end.x,
                y3 = end.y
            )
        }

        // 1. Soft Outer Glow
        drawScope.drawPath(
            path = path,
            color = color.copy(alpha = 0.25f),
            style = Stroke(width = strokeWidth + 6f, cap = StrokeCap.Round)
        )

        // 2. Core Crisp Wire
        drawScope.drawPath(
            path = path,
            color = color,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        // 3. Port Blips at Start and End
        drawScope.drawCircle(color = color, radius = strokeWidth + 2f, center = start)
        drawScope.drawCircle(color = color, radius = strokeWidth + 2f, center = end)

        // 4. Animated Flow Particle along Bézier curve (t = pulseProgress)
        if (pulseProgress > 0f) {
            val t = pulseProgress.coerceIn(0f, 1f)
            val p0 = start
            val p1 = Offset(start.x + dx, start.y)
            val p2 = Offset(end.x - dx, end.y)
            val p3 = end

            val u = 1f - t
            val tt = t * t
            val uu = u * u
            val uuu = uu * u
            val ttt = tt * t

            val px = (uuu * p0.x) + (3 * uu * t * p1.x) + (3 * u * tt * p2.x) + (ttt * p3.x)
            val py = (uuu * p0.y) + (3 * uu * t * p1.y) + (3 * u * tt * p2.y) + (ttt * p3.y)

            val pulsePos = Offset(px, py)
            drawScope.drawCircle(color = Color.White, radius = strokeWidth + 3f, center = pulsePos)
            drawScope.drawCircle(color = color.copy(alpha = 0.5f), radius = strokeWidth + 8f, center = pulsePos)
        }
    }
}
