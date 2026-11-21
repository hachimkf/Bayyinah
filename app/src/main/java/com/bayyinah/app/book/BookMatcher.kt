package com.bayyinah.app.book

import com.bayyinah.app.ocr.TextImagePrep
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Recognizes a camera crop of her book by comparing printed-line "fingerprints" with the book's
 * own lines (built from the official PDF, see tools/book). No OCR, no AI: the print is identical,
 * so the shapes match directly.
 *
 * Pipeline (mirrors tools/book/bookfp.py + eval_matcher.py — keep in sync):
 * ink → drop border rules → deskew (vertical shear) → line spacing (autocorrelation) → evenly
 * spaced baselines → one strip per line (20 rows, scale-normalized by line spacing) → sliding
 * normalized correlation against every reference line (coarse 2×2, then refine the best 6) →
 * evidence from two consecutive lines → accept only a clear winner with plausible ink density.
 *
 * Measured on 60 synthetic camera shots of the book's Quran lines + 40 of its other text:
 * 41 recognised from a single frame, 0 wrong, 0 false matches.
 */
class BookMatcher(private val refs: BookRefs) {

    data class Result(val page: Int, val surah: Int, val ayah: Int, val score: Float, val margin: Float, val density: Float) {
        val verseId get() = surah * 1000 + ayah
        val accepted get() = score >= MIN_SCORE && margin >= MIN_MARGIN && density in DENSITY_MIN..DENSITY_MAX
    }

    /** [lum] grayscale 0..255, row-major [w]×[h]. Null when no text line was found. */
    fun match(lum: IntArray, w: Int, h: Int): Result? {
        val strips = queryStrips(lum, w, h)
        if (strips.isEmpty()) return null
        val n = refs.lines.size
        data class Scored(val base: Int, val pitch: Int, val q: Strip, val sc: FloatArray, val off: IntArray)
        val scored = strips.map { (base, pitch, q) -> val (sc, off) = matchLine(q); Scored(base, pitch, q, sc, off) }

        data class Cand(val score: Float, val ref: Int, val off: Int, val q: Strip)
        val cands = ArrayList<Cand>(scored.size * n * 2)
        for (s in scored) for (i in 0 until n) cands += Cand(s.sc[i], i, s.off[i], s.q)
        val byY = scored.sortedBy { it.base }
        for (k in 0 until byY.size - 1) {
            val a = byY[k]; val b = byY[k + 1]
            if (a.pitch != b.pitch) continue
            val gap = b.base - a.base
            if (gap < 0.6 * a.pitch || gap > 1.6 * a.pitch) continue
            for (i in 0 until n) {
                val j = refs.next[i]
                if (j >= 0) cands += Cand((a.sc[i] + b.sc[j]) / 2 + PAIR_BONUS, i, a.off[i], a.q)
            }
        }
        cands.sortByDescending { it.score }
        val best = cands.first()
        val r = refs.lines[best.ref]
        val second = cands.firstOrNull { refs.lines[it.ref].page != r.page }?.score ?: 0f
        val qw = best.q.w
        val a0 = max(0, best.off); val a1 = min(r.w, best.off + qw)
        var qs = 0.0; var rs = 0.0
        for (y in 0 until r.h) for (x in a0 until a1) { qs += best.q[y, x - best.off]; rs += r.strip[y * r.w + x] }
        val density = if (rs > 1e-6) (qs / rs).toFloat() else 0f
        val ayah = r.spans.maxByOrNull { sp -> max(0, min(sp.x1, best.off + qw) - max(sp.x0, best.off)) }?.ayah ?: return null
        return Result(r.page, r.surah, ayah, best.score, best.score - second, density)
    }

    // ------------------------------------------------------------------ query side

    private fun queryStrips(lum: IntArray, w: Int, h: Int): List<Triple<Int, Int, Strip>> {
        val ink = deskew(removeRules(TextImagePrep.binarize(lum, w, h), w, h), w, h)
        val p = pitchOf(ink, w, h, max(12, h / 8), (h * 0.75).toInt())
        val pitches = if (p != null) listOf(p) else {
            val bh = bandHeight(ink, w, h) ?: return emptyList()
            listOf(0.85, 1.0, 1.15).map { (bh * 0.9 * it).toInt() }
        }
        val out = ArrayList<Triple<Int, Int, Strip>>()
        for (pc in pitches) {
            if (pc < 8) continue
            for (base in lines(ink, w, h, pc)) {
                val st = strip(ink, w, h, base, pc) ?: continue
                if (st.w >= 20) out += Triple(base, pc, st)
            }
        }
        return out
    }

    /** Coarse (2×2) scan of every reference, full-resolution refine of the top 6. */
    private fun matchLine(q: Strip): Pair<FloatArray, IntArray> {
        val n = refs.lines.size
        val sc = FloatArray(n); val off = IntArray(n)
        val qc = q.pool2()
        for (i in 0 until n) {
            val (v, o) = nccSlide(qc, refs.lines[i].coarse)
            sc[i] = v - 0.05f; off[i] = o * 2
        }
        val top = (0 until n).sortedByDescending { sc[it] }.take(TOP_K)
        for (i in top) {
            val (v, o) = nccWindow(q, refs.lines[i].full, off[i] - 3, off[i] + 3)
            sc[i] = v; off[i] = o
        }
        return sc to off
    }

    companion object {
        const val MIN_SCORE = 0.42f
        const val MIN_MARGIN = 0.06f
        const val PAIR_BONUS = 0.08f
        const val DENSITY_MIN = 0.6f
        const val DENSITY_MAX = 2.3f
        private const val TOP_K = 6
        private const val H_STRIP = 20
        private const val UPP = 0.62
        private const val DOWNP = 0.38
        private const val MIN_OVERLAP = 0.7

        /** Drop long thin connected components (box borders, table rules). */
        internal fun removeRules(ink: BooleanArray, w: Int, h: Int): BooleanArray {
            val label = IntArray(w * h)
            val stack = IntArray(w * h)
            val drop = ArrayList<Boolean>()
            for (start in ink.indices) {
                if (!ink[start] || label[start] != 0) continue
                val id = drop.size + 1
                var sp = 0; var minX = w; var maxX = -1; var minY = h; var maxY = -1
                stack[sp++] = start; label[start] = id
                while (sp > 0) {
                    val p = stack[--sp]; val x = p % w; val y = p / w
                    if (x < minX) minX = x; if (x > maxX) maxX = x; if (y < minY) minY = y; if (y > maxY) maxY = y
                    for (dy in -1..1) {
                        val ny = y + dy; if (ny < 0 || ny >= h) continue
                        for (dx in -1..1) {
                            val nx = x + dx; if (nx < 0 || nx >= w) continue
                            val q = ny * w + nx
                            if (ink[q] && label[q] == 0) { label[q] = id; stack[sp++] = q }
                        }
                    }
                }
                val bw = maxX - minX + 1; val bh = maxY - minY + 1
                drop += (bw > 0.35 * w && bh < 0.06 * h + 6) || (bh > 0.5 * h && bw < 12)
            }
            return BooleanArray(w * h) { label[it] != 0 && !drop[label[it] - 1] }
        }

        /** Vertical shear y' = y − tan(a)·(x − w/2), a ∈ [−6°, 6°] maximizing Σ rowcount². */
        internal fun deskew(ink: BooleanArray, w: Int, h: Int): BooleanArray {
            var count = 0
            for (v in ink) if (v) count++
            if (count < 50) return ink
            var bestE = -1.0; var bestT = 0.0
            val hist = IntArray(h * 3 + 4)
            var a10 = -60
            while (a10 <= 60) {
                val t = tan(Math.toRadians(a10 / 10.0))
                java.util.Arrays.fill(hist, 0)
                for (y in 0 until h) for (x in 0 until w) {
                    if (!ink[y * w + x]) continue
                    val yy = floor(y - t * (x - w / 2.0) + 0.5).toInt() + h
                    if (yy in hist.indices) hist[yy]++
                }
                var e = 0.0
                for (c in hist) e += c.toDouble() * c
                if (e > bestE) { bestE = e; bestT = t }
                a10 += 5
            }
            if (abs(bestT) < tan(Math.toRadians(0.25))) return ink
            val out = BooleanArray(w * h)
            for (x in 0 until w) {
                val s = floor(-bestT * (x - w / 2.0) + 0.5).toInt()
                for (y in 0 until h) {
                    val ny = y + s
                    if (ny in 0 until h && ink[y * w + x]) out[ny * w + x] = true
                }
            }
            return out
        }

        private fun rowProfile(ink: BooleanArray, w: Int, h: Int) = DoubleArray(h) { y ->
            var c = 0; for (x in 0 until w) if (ink[y * w + x]) c++; c.toDouble()
        }

        /** Line spacing: first clear peak of the row-profile autocorrelation in [lo, hi]. */
        internal fun pitchOf(ink: BooleanArray, w: Int, h: Int, lo: Int, hiIn: Int): Int? {
            val prof = rowProfile(ink, w, h)
            if ((prof.maxOrNull() ?: 0.0) == 0.0) return null
            val mean = prof.average()
            val p = DoubleArray(h) { prof[it] - mean }
            val n = h; val hi = min(hiIn, n - 1)
            if (hi <= lo + 2) return null
            var ac0 = 1e-9; for (v in p) ac0 += v * v
            val vals = DoubleArray(hi - lo + 1) { i ->
                val k = lo + i; var s = 0.0
                for (j in 0 until n - k) s += p[j] * p[j + k]
                s / ac0
            }
            for (i in 1 until vals.size - 1) {
                if (vals[i] > vals[i - 1] && vals[i] >= vals[i + 1] && vals[i] > 0.15) return lo + i
            }
            return null
        }

        private fun smooth(v: DoubleArray, winIn: Int): DoubleArray {
            val win = max(1, winIn)
            val c = DoubleArray(v.size + 1); for (i in v.indices) c[i + 1] = c[i] + v[i]
            return DoubleArray(v.size) { i ->
                val a = max(0, i - win / 2); val b = min(v.size, i + win / 2 + 1)
                (c[b] - c[a]) / (b - a)
            }
        }

        /** Evenly spaced baselines: best phase of Σ prof(φ + k·pitch), each refined ±pitch/6. */
        internal fun lines(ink: BooleanArray, w: Int, h: Int, pitch: Int): List<Int> {
            val prof = smooth(rowProfile(ink, w, h), max(3, pitch / 4))
            val mx = prof.maxOrNull() ?: return emptyList()
            if (mx == 0.0) return emptyList()
            var best = -1.0; var phase = 0
            for (ph in 0 until pitch) {
                var s = 0.0; var y = ph
                while (y < prof.size) { s += prof[y]; y += pitch }
                if (s > best) { best = s; phase = ph }
            }
            val rows = ArrayList<Int>()
            var y = phase
            while (y < prof.size) {
                val a = max(0, y - pitch / 6); val b = min(prof.size, y + pitch / 6 + 1)
                var yy = a
                for (k in a until b) if (prof[k] > prof[yy]) yy = k
                if (prof[yy] >= 0.08 * mx) rows += yy
                y += pitch
            }
            return rows
        }

        internal fun bandHeight(ink: BooleanArray, w: Int, h: Int): Int? {
            val prof = rowProfile(ink, w, h)
            val mx = prof.maxOrNull() ?: return null
            if (mx == 0.0) return null
            val on = prof.indices.filter { prof[it] > 0.06 * mx }
            return if (on.isEmpty()) null else on.last() - on.first() + 1
        }

        /** Rows [base−0.62·pitch, base+0.38·pitch) of the line's ink extent, area-averaged to 20 rows. */
        internal fun strip(ink: BooleanArray, w: Int, h: Int, base: Int, pitch: Int): Strip? {
            val top = round(base - UPP * pitch).toInt(); val bot = round(base + DOWNP * pitch).toInt()
            val sh = bot - top
            if (sh <= 0) return null
            var cx0 = w; var cx1 = -1
            for (y in max(0, top) until min(h, bot)) for (x in 0 until w) if (ink[y * w + x]) { if (x < cx0) cx0 = x; if (x > cx1) cx1 = x }
            if (cx1 < 0) return null
            cx1 += 1
            val sw = cx1 - cx0
            val s = H_STRIP.toDouble() / sh
            val ow = max(4, round(sw * s).toInt())
            // integral image over the sub-window (rows top..bot, cols cx0..cx1)
            val ii = LongArray((sh + 1) * (sw + 1))
            for (yy in 0 until sh) {
                var rowSum = 0L; val y = top + yy
                for (xx in 0 until sw) {
                    if (y in 0 until h && ink[y * w + cx0 + xx]) rowSum++
                    ii[(yy + 1) * (sw + 1) + xx + 1] = ii[yy * (sw + 1) + xx + 1] + rowSum
                }
            }
            val out = FloatArray(H_STRIP * ow)
            for (i in 0 until H_STRIP) {
                val ys0 = i.toDouble() * sh / H_STRIP; val ys1 = (i + 1).toDouble() * sh / H_STRIP
                val y0 = ys0.toInt(); val y1 = max(y0 + 1, ceil(ys1).toInt())
                for (j in 0 until ow) {
                    val xs0 = j.toDouble() * sw / ow; val xs1 = (j + 1).toDouble() * sw / ow
                    val x0 = xs0.toInt(); val x1 = max(x0 + 1, ceil(xs1).toInt())
                    val sum = ii[y1 * (sw + 1) + x1] - ii[y0 * (sw + 1) + x1] - ii[y1 * (sw + 1) + x0] + ii[y0 * (sw + 1) + x0]
                    out[i * ow + j] = sum.toFloat() / ((y1 - y0) * (x1 - x0))
                }
            }
            return Strip(H_STRIP, ow, out)
        }

        internal fun nccSlide(q: Strip, r: Strip): Pair<Float, Int> =
            nccWindow(q, r, -(q.w * (1 - MIN_OVERLAP)).toInt(), r.w - (q.w * MIN_OVERLAP).toInt())

        /** Best normalized correlation of [q] placed at column offsets [lo, hi] of [r] (same height). */
        internal fun nccWindow(q: Strip, r: Strip, lo: Int, hi: Int): Pair<Float, Int> {
            var best = -1f; var bestOff = lo
            val h = min(q.h, r.h)
            for (off in lo..hi) {
                val a0 = max(0, off); val a1 = min(r.w, off + q.w)
                if (a1 - a0 < MIN_OVERLAP * q.w) continue
                var sr = 0.0; var sq = 0.0; var srr = 0.0; var sqq = 0.0; var srq = 0.0
                for (y in 0 until h) {
                    val ro = y * r.w; val qo = y * q.w - off
                    for (x in a0 until a1) {
                        val rv = r.v[ro + x].toDouble(); val qv = q.v[qo + x].toDouble()
                        sr += rv; sq += qv; srr += rv * rv; sqq += qv * qv; srq += rv * qv
                    }
                }
                val cnt = (a1 - a0).toDouble() * h
                val cov = srq - sr * sq / cnt
                val den = sqrt((srr - sr * sr / cnt) * (sqq - sq * sq / cnt))
                val v = if (den > 1e-9) (cov / den).toFloat() else -1f
                if (v > best) { best = v; bestOff = off }
            }
            return best to bestOff
        }
    }
}
