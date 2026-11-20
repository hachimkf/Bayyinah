package com.bayyinah.app.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * CameraX analyzer that does **nothing** until a scan is requested (battery friendly: frames
 * are closed immediately). On request, it takes the next frame, rotates it upright, crops it to
 * the on-screen framing box, downscales and grayscales it, then hands the small grayscale bitmap over.
 *
 * Preview and analysis must use the same aspect ratio (both 4:3 in [com.bayyinah.app.ui]) and the
 * PreviewView must use FILL_CENTER so the view → image mapping below is exact.
 */
class OcrAnalyzer : ImageAnalysis.Analyzer {

    /** Framing box in PreviewView pixel coordinates, plus the view size. Set from Compose. */
    @Volatile var frameInView: RectF = RectF()
    @Volatile var viewWidth: Int = 0
    @Volatile var viewHeight: Int = 0

    private val pending = AtomicReference<((Bitmap?) -> Unit)?>(null)

    /** Asks for the next frame. The callback runs on the analysis executor. */
    fun requestFrame(onCropped: (Bitmap?) -> Unit) {
        pending.set(onCropped)
    }

    fun cancel() { pending.set(null) }

    override fun analyze(image: ImageProxy) {
        val callback = pending.getAndSet(null)
        if (callback == null) { image.close(); return }
        val result = try {
            cropToFrame(image)
        } catch (t: Throwable) {
            null
        } finally {
            image.close()
        }
        callback(result)
    }

    private fun cropToFrame(image: ImageProxy): Bitmap? {
        val vw = viewWidth; val vh = viewHeight; val frame = RectF(frameInView)
        if (vw <= 0 || vh <= 0 || frame.isEmpty) return null

        val raw = image.toBitmap() // RGBA_8888 output format → cheap
        val rotation = image.imageInfo.rotationDegrees
        val upright = if (rotation == 0) raw else Bitmap.createBitmap(
            raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(rotation.toFloat()) }, true,
        ).also { raw.recycle() }

        // FILL_CENTER: image scaled by s to cover the view, centred.
        val w = upright.width.toFloat(); val h = upright.height.toFloat()
        val s = max(vw / w, vh / h)
        val dx = (vw - w * s) / 2f
        val dy = (vh - h * s) / 2f
        val left = ((frame.left - dx) / s).coerceIn(0f, w)
        val top = ((frame.top - dy) / s).coerceIn(0f, h)
        val right = ((frame.right - dx) / s).coerceIn(0f, w)
        val bottom = ((frame.bottom - dy) / s).coerceIn(0f, h)
        val cw = (right - left).roundToInt(); val ch = (bottom - top).roundToInt()
        if (cw < 32 || ch < 16) { upright.recycle(); return null }

        // Downscale so the long side is at most MAX_SIDE (Tesseract is O(pixels)).
        val scale = min(1f, MAX_SIDE / max(cw, ch).toFloat())
        val outW = (cw * scale).roundToInt(); val outH = (ch * scale).roundToInt()
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(
            upright,
            android.graphics.Rect(left.roundToInt(), top.roundToInt(), left.roundToInt() + cw, top.roundToInt() + ch),
            android.graphics.Rect(0, 0, outW, outH),
            grayPaint,
        )
        upright.recycle()
        return out
    }

    private companion object {
        const val MAX_SIDE = 900
        /** Plain grayscale; thresholding happens later in [TextImagePrep]. */
        val grayPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
        }
    }
}
