package com.bayyinah.app.ui

import android.graphics.RectF
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** Design colours (app ui home and scan pages.svg). */
/** Circles on the scan pages: sage with deep green icons (better contrast). */
val ScanMint = Color(0xFFDFE6DA)
val ScanLine = Color(0xFF174A3A)
private val BeamGreen = Color(0xFF2D6B4F)

/** The frame, as fractions of the screen — the same on home and on the camera page (from the design). */
const val FRAME_LEFT = 0.108f
const val FRAME_RIGHT = 0.891f
const val FRAME_TOP = 0.285f
const val FRAME_HEIGHT = 0.479f

fun scanFrame(w: Float, h: Float) = RectF(w * FRAME_LEFT, h * FRAME_TOP, w * FRAME_RIGHT, h * (FRAME_TOP + FRAME_HEIGHT))

/**
 * The design's frame: a top and a bottom bracket (slightly bowed edge, rounded corners, short
 * side arms), a dark green scan line, and a trapezoid beam fading below it. The line drifts gently
 * up and down around its place in the design (45 % of the frame).
 */
@Composable
fun ScanFrame(
    frame: RectF, modifier: Modifier = Modifier, sweeping: Boolean = true,
    accent: Color = Color.White, line: Color = ScanLine, beamTop: Color = BeamGreen.copy(alpha = 0.30f),
) {
    val p by rememberInfiniteTransition(label = "sweep").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "p",
    )
    Canvas(modifier) {
        val l = frame.left; val t = frame.top; val r = frame.right; val b = frame.bottom
        val fw = r - l; val fh = b - t
        val radius = fw * 0.10f; val arm = fh * 0.16f; val bow = fh * 0.011f
        val stroke = Stroke(4.5.dp.toPx(), cap = StrokeCap.Round)
        val top = Path().apply {
            moveTo(l, t + arm); lineTo(l + 1f, t + radius)
            quadraticTo(l + 2f, t + bow, l + radius, t + bow)
            quadraticTo((l + r) / 2f, t - bow, r - radius, t + bow)
            quadraticTo(r - 2f, t + bow, r - 1f, t + radius); lineTo(r, t + arm)
        }
        val bottom = Path().apply {
            moveTo(l, b - arm); lineTo(l + 1f, b - radius)
            quadraticTo(l + 2f, b - bow, l + radius, b - bow)
            quadraticTo((l + r) / 2f, b + bow, r - radius, b - bow)
            quadraticTo(r - 2f, b - bow, r - 1f, b - radius); lineTo(r, b - arm)
        }
        drawPath(top, accent, style = stroke)
        drawPath(bottom, accent, style = stroke)

        val y = t + fh * (if (sweeping) 0.18f + 0.27f * p else 0.447f)
        // Beam: trapezoid, narrower under the line, wider at its foot (design proportions).
        val foot = y + fh * 0.516f
        val beam = Path().apply {
            moveTo(l + fw * 0.055f, y); lineTo(l + fw * 0.943f, y)
            lineTo(l + fw * 0.986f, foot); lineTo(l + fw * 0.011f, foot); close()
        }
        drawPath(beam, Brush.verticalGradient(listOf(beamTop, BeamGreen.copy(alpha = 0f)), startY = y, endY = foot))
        val inset = fw * 0.017f
        drawLine(line, Offset(l + inset, y), Offset(r - inset, y), strokeWidth = 4.5.dp.toPx(), cap = StrokeCap.Round)
    }
}
