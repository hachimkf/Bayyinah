package com.bayyinah.app.ui

import android.graphics.RectF
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.bayyinah.app.ui.theme.Jade

/** Where the scan frame sits, as fractions of the screen (shared by home and the camera page). */
const val FRAME_LEFT = 0.08f
const val FRAME_RIGHT = 0.92f
const val FRAME_TOP = 0.30f
const val FRAME_HEIGHT = 0.20f

fun scanFrame(w: Float, h: Float) = RectF(w * FRAME_LEFT, h * FRAME_TOP, w * FRAME_RIGHT, h * (FRAME_TOP + FRAME_HEIGHT))

/**
 * White rounded brackets (top and bottom, open at the sides) and a scan line that sweeps
 * upward through the frame, trailing a soft light beam beneath it.
 */
@Composable
fun ScanFrame(frame: RectF, modifier: Modifier = Modifier, sweeping: Boolean = true, accent: Color = Color.White) {
    val p by rememberInfiniteTransition(label = "sweep").animateFloat(
        1f, 0f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "p",
    )
    Canvas(modifier) {
        val l = frame.left; val t = frame.top; val r = frame.right; val b = frame.bottom
        val radius = 30.dp.toPx(); val arm = 54.dp.toPx(); val bow = 6.dp.toPx()
        val stroke = Stroke(4.dp.toPx(), cap = StrokeCap.Round)
        // Top bracket: down-left arm, rounded corner, a gently bowed top edge, rounded corner, arm.
        val top = Path().apply {
            moveTo(l, t + arm); lineTo(l + 1f, t + radius)
            quadraticTo(l + 2f, t + 2f, l + radius, t + 2f)
            quadraticTo((l + r) / 2f, t - bow, r - radius, t + 2f)
            quadraticTo(r - 2f, t + 2f, r - 1f, t + radius); lineTo(r, t + arm)
        }
        val bottom = Path().apply {
            moveTo(l, b - arm); lineTo(l + 1f, b - radius)
            quadraticTo(l + 2f, b - 2f, l + radius, b - 2f)
            quadraticTo((l + r) / 2f, b + bow, r - radius, b - 2f)
            quadraticTo(r - 2f, b - 2f, r - 1f, b - radius); lineTo(r, b - arm)
        }
        drawPath(top, accent, style = stroke)
        drawPath(bottom, accent, style = stroke)

        val inset = 14.dp.toPx()
        val y = if (sweeping) t + (b - t) * (0.12f + 0.76f * p) else (t + b) / 2f
        if (sweeping) {
            // Beam below the line (the line travels upward), fading out.
            val beam = (b - t) * 0.42f
            drawRect(
                Brush.verticalGradient(listOf(Jade.copy(alpha = 0.45f), Color.White.copy(alpha = 0.12f), Color.Transparent), startY = y, endY = y + beam),
                topLeft = Offset(l + inset, y), size = Size(r - l - 2 * inset, beam),
            )
        }
        drawLine(accent, Offset(l + inset, y), Offset(r - inset, y), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
    }
}
