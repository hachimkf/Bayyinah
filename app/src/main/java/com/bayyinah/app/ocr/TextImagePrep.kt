package com.bayyinah.app.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Prepares a camera crop for Tesseract. Two measured findings drive this:
 *
 * 1. Tesseract's Arabic model mostly returns *nothing* for fully vowelled Quranic print: the dense
 *    tashkeel marks wreck its line finding. Removing small ink blobs (harakat, sukun, shadda, waqf
 *    signs) before OCR made it read almost every line. Some letter dots go too; the matcher
 *    compares on a dot-tolerant skeleton, so that's fine.
 * 2. It only reads lines in a narrow size band, so each crop is rescaled to a few calibrated
 *    per-line heights, one OCR pass each.
 *
 * Calibrated offline on camera-degraded renders of 60 Warsh verses (KFGQPC font) + 60 in Naskh:
 * ~85% identified per single frame, 0 false matches on non-Quran Arabic text.
 */
object TextImagePrep {

    /** Target line heights (px) after mark removal, one OCR pass each, in this order. */
    val TARGET_LINE_HEIGHTS = intArrayOf(32, 24, 40)

    /** Blobs smaller than this fraction of a typical letter-body blob are treated as marks. */
    internal const val MARK_RATIO = 0.2f
    private const val INK_DELTA = 14
    private const val BORDER = 10

    /** Returns one black-on-white bitmap per target height (caller recycles them). */
    fun prepare(gray: Bitmap): List<Bitmap> {
        val w = gray.width; val h = gray.height
        val px = IntArray(w * h)
        gray.getPixels(px, 0, w, 0, 0, w, h)
        val lum = IntArray(w * h) { px[it] and 0xFF } // input is already grayscale
        val ink = stripMarks(binarize(lum, w, h), w, h)
        val b = inkBounds(ink, w, h) ?: return emptyList()
        val lines = lineCount(ink, w, b)
        val box = Rect(b[0], b[1], b[2], b[3])

        for (i in px.indices) px[i] = if (ink[i]) Color.BLACK else Color.WHITE
        val bin = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        return TARGET_LINE_HEIGHTS.map { target ->
            val s = target.toFloat() * lines / box.height()
            val sw = max(8, (box.width() * s).roundToInt()); val sh = max(8, (box.height() * s).roundToInt())
            Bitmap.createBitmap(sw + 2 * BORDER, sh + 2 * BORDER, Bitmap.Config.ARGB_8888).also { out ->
                Canvas(out).apply {
                    drawColor(Color.WHITE)
                    drawBitmap(bin, box, Rect(BORDER, BORDER, BORDER + sw, BORDER + sh), paint)
                }
            }
        }.also { bin.recycle() }
    }

    /** Local-mean threshold via an integral image: ink where the pixel is darker than its surroundings. */
    internal fun binarize(lum: IntArray, w: Int, h: Int): BooleanArray {
        val r = max(6, min(w, h) / 4)
        val integral = LongArray((w + 1) * (h + 1))
        for (y in 0 until h) {
            var rowSum = 0L
            for (x in 0 until w) {
                rowSum += lum[y * w + x]
                integral[(y + 1) * (w + 1) + x + 1] = integral[y * (w + 1) + x + 1] + rowSum
            }
        }
        val ink = BooleanArray(w * h)
        for (y in 0 until h) {
            val y0 = max(0, y - r); val y1 = min(h, y + r + 1)
            for (x in 0 until w) {
                val x0 = max(0, x - r); val x1 = min(w, x + r + 1)
                val sum = integral[y1 * (w + 1) + x1] - integral[y0 * (w + 1) + x1] -
                    integral[y1 * (w + 1) + x0] + integral[y0 * (w + 1) + x0]
                val mean = sum / ((x1 - x0) * (y1 - y0))
                ink[y * w + x] = mean - lum[y * w + x] > INK_DELTA
            }
        }
        return ink
    }

    /**
     * Removes 8-connected ink blobs smaller than [MARK_RATIO] × the typical letter-body blob
     * (median size of the largest third of blobs).
     */
    internal fun stripMarks(ink: BooleanArray, w: Int, h: Int): BooleanArray {
        val label = IntArray(w * h)            // 0 = unlabeled / background
        val sizes = ArrayList<Int>()
        val stack = IntArray(w * h)
        for (start in ink.indices) {
            if (!ink[start] || label[start] != 0) continue
            val id = sizes.size + 1
            var sp = 0; var size = 0
            stack[sp++] = start; label[start] = id
            while (sp > 0) {
                val p = stack[--sp]; size++
                val x = p % w; val y = p / w
                for (dy in -1..1) {
                    val ny = y + dy
                    if (ny < 0 || ny >= h) continue
                    for (dx in -1..1) {
                        val nx = x + dx
                        if (nx < 0 || nx >= w) continue
                        val q = ny * w + nx
                        if (ink[q] && label[q] == 0) { label[q] = id; stack[sp++] = q }
                    }
                }
            }
            sizes += size
        }
        if (sizes.isEmpty()) return ink
        val sorted = sizes.sorted()
        val top = sorted.subList(sorted.size - max(1, sorted.size / 3), sorted.size)
        val typical = top[top.size / 2]
        val minSize = MARK_RATIO * typical
        return BooleanArray(w * h) { i -> label[i] != 0 && sizes[label[i] - 1] >= minSize }
    }

    /** Tight ink bounds as [left, top, right, bottom) or null when there is no ink. */
    internal fun inkBounds(ink: BooleanArray, w: Int, h: Int): IntArray? {
        var l = w; var t = h; var r = -1; var b = -1
        for (y in 0 until h) for (x in 0 until w) if (ink[y * w + x]) {
            if (x < l) l = x; if (x > r) r = x; if (y < t) t = y; if (y > b) b = y
        }
        return if (r < 0) null else intArrayOf(l, t, r + 1, b + 1)
    }

    /** Number of text lines: runs of inked rows inside [box] (marks are gone, so gaps are clean). */
    internal fun lineCount(ink: BooleanArray, w: Int, box: IntArray): Int {
        var lines = 0; var prev = false
        for (y in box[1] until box[3]) {
            var any = false
            for (x in box[0] until box[2]) if (ink[y * w + x]) { any = true; break }
            if (any && !prev) lines++
            prev = any
        }
        return max(1, lines)
    }
}
