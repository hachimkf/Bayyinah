package com.bayyinah.app.book

import com.bayyinah.app.ocr.TextImagePrep
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sqrt

/**
 * Recognizes a camera crop of any page of the printed Mushaf from an imported [MushafPack].
 * Mirror of tools/mushaf/mushaf_matcher.py (keep in sync):
 *
 * ink → drop rules → deskew → line spacing fitted to the Mushaf's line template (×0.96, 1, 1.04) →
 * one strip per line → coarse correlation of each line against every line of the Mushaf at once →
 * anchors scored by up to 3 crop lines (nearest the middle) on consecutive Mushaf lines → fine correlation of the best 40 at
 * the three scales → accept a clear winner.
 *
 * Measured (Python twin) on 300 synthetic camera shots of random pages + 100 of the Mushaf's own
 * non-Quran pages: 243 recognised (≈90 % with 3+ lines in view), 0 wrong, 0 false matches.
 */
class MushafMatcher(private val pack: MushafPack) {

    data class Result(
        val pdfPage: Int, val page: Int, val line: Int, val surah: Int, val ayah: Int,
        val score: Float, val margin: Float, val lines: Int,
        /** How well the crop's rows fit the Mushaf's line pattern (0..1). */
        val fit: Float,
        /** Ink in the crop's middle line ÷ ink in the matched Mushaf line, over the matched columns. */
        val density: Float,
        /** Horizontal disagreement (reference columns) between the crop's lines; -1 with one line. */
        val spread: Float,
    ) {
        val verseId get() = surah * 1000 + ayah

        /**
         * Text-likeness + clear winner. Tuned on 1,350 camera-like crops of scenes without Mushaf text
         * (rooms, walls, faces, screens, stripes, her book's lesson pages) + 100 of the Mushaf's own
         * non-Quran pages: 0 accepted, 221/300 Mushaf crops recognised (tools/mushaf/mushaf_matcher.py ACCEPT).
         */
        val accepted: Boolean get() {
            if (density !in 0.6f..2.0f) return false
            return if (lines >= 2 && spread >= 0f)
                score >= 0.54f && margin >= 0.04f && fit >= 0.70f && spread <= 8f        // lines must align: justified text
            else
                score >= 0.60f && margin >= 0.12f && fit >= 0.80f && density in 0.85f..1.6f   // one line: weaker evidence
        }
    }

    private class QLine(val base: Int, val strip: FloatArray, val w: Int, val cx0: Int)   // 20-row query strip

    private class Coarse(val best: FloatArray, val bestT: IntArray, val qs: FloatArray, val qw: Int)

    /** [lum] grayscale 0..255, row-major [w]×[h]. Null when no text line was found. */
    fun match(lum: IntArray, w: Int, h: Int): Result? {
        val ink = BookMatcher.deskew(BookMatcher.removeRules(TextImagePrep.binarize(lum, w, h), w, h), w, h)
        val (groups, fit) = queryLines(ink, w, h)
        if (groups.isEmpty()) return null
        val keys = groups.keys.sorted()
        val p1 = keys[keys.size / 2]

        fun fullLines(pc: Double): List<QLine> {
            val g = groups.getValue(pc)
            val f = g.filter { it.base - 0.62 * pc >= -0.25 * pc && it.base + 0.38 * pc <= h + 0.25 * pc }.ifEmpty { g }
            return f.sortedBy { it.base }
        }

        // Coarse search over the whole Mushaf, once per line, at the fitted pitch only.
        var full = fullLines(p1)
        if (full.size > MAX_LINES) {   // coarse search is the costly part: keep the lines nearest the middle
            full = full.sortedBy { kotlin.math.abs(it.base - 0.12 * p1 - h / 2.0) }.take(MAX_LINES).sortedBy { it.base }
        }
        val use = ArrayList<Triple<Int, Coarse, QLine>>()
        for (q in full) {
            val k = round((q.base - full[0].base) / p1).toInt()
            coarse(q)?.let { use += Triple(k, it, q) }
        }
        if (use.isEmpty()) return null
        val n = pack.lines.size
        val a = DoubleArray(n)
        val shifted = use.map { (k, c, _) ->
            val idx = IntArray(n) { it }
            repeat(k) { for (i in 0 until n) if (idx[i] >= 0) idx[i] = pack.next[idx[i]] }
            for (i in 0 until n) a[i] += if (idx[i] >= 0) c.best[idx[i]].toDouble() else -1.0
            idx
        }
        val anchors = (0 until n).sortedByDescending { a[it] }.take(TOP_K)

        // Fine scoring of the anchors at each scale (same lines, matched by baseline).
        class Cand(val score: Double, val i: Int, val fine: List<Pair<Float, Int>>, val refs: List<Int>, val widths: List<Int>,
                   val qs: List<Pair<FloatArray, Int>>, val pitch: Double)
        val cands = ArrayList<Cand>()
        for (pc in groups.keys) {
            val other = fullLines(pc)
            val qs: List<Pair<FloatArray, Int>> = use.map { (_, c, q) ->
                if (pc == p1) c.qs to c.qw
                else { val o = other.minBy { kotlin.math.abs(it.base - q.base) }; quantize(o.strip, o.w) }
            }
            val widths = use.mapIndexed { j, (_, _, q) -> if (pc == p1) q.w else other.minBy { kotlin.math.abs(it.base - q.base) }.w }
            for (i in anchors) {
                val fs = use.indices.map { j -> fine(use[j].second, shifted[j][i], qs[j].first, qs[j].second) }
                cands += Cand(fs.map { it.first.toDouble() }.average(), i, fs, shifted.map { it[i] }, widths, qs, pc)
            }
        }
        val sorted = cands.sortedByDescending { it.score }
        val best = sorted.first()
        val l0 = pack.lines[best.i]
        val second = sorted.drop(1).firstOrNull { kotlin.math.abs(it.i - best.i) > 2 || pack.lines[it.i].pdfPage != l0.pdfPage }?.score ?: 0.0
        var mid = use.size / 2
        var r = best.refs[mid]
        if (r < 0) { r = best.i; mid = 0 }
        val l = pack.lines[r]
        val off = best.fine[mid].second
        val qw = (best.widths[mid] * pack.rows / 20.0).toInt()
        val sp = l.spans.maxByOrNull { max(0, min(it.x1, off + qw) - max(it.x0, off)) } ?: return null

        // ink density of the middle line against the matched reference columns
        val (q10, q10w) = best.qs[mid]; val rr = pack.strip(r); val rw = l.w; val rows = pack.rows
        val a0 = max(0, off); val a1 = min(rw, off + q10w)
        var density = 0.0
        if (a1 > a0) {
            var qsum = 0.0; var rsum = 0.0
            for (y in 0 until rows) for (x in a0 until a1) { qsum += q10[y * q10w + x - off]; rsum += rr[y * rw + x] }
            val cnt = rows.toDouble() * (a1 - a0)
            density = (qsum / cnt) / max(1e-6, rsum / cnt)
        }
        // alignment: Mushaf lines are justified, so every crop line must sit at the same page x
        val maxW = best.refs.filter { it >= 0 }.maxOfOrNull { pack.lines[it].w } ?: 0
        val lefts = use.indices.filter { j -> best.refs[j] >= 0 && pack.lines[best.refs[j]].w >= 0.9 * maxW }
            .map { j -> best.fine[j].second - use[j].third.cx0 * rows / best.pitch }
        val spread = if (lefts.size >= 2) lefts.max() - lefts.min() else -1.0
        return Result(l.pdfPage, pack.printedPage(l.pdfPage), l.lineNo, sp.surah, sp.ayah, best.score.toFloat(), (best.score - second).toFloat(),
            use.size, fit.toFloat(), density.toFloat(), spread.toFloat())
    }

    // ---------------------------------------------------------------- query lines

    private fun queryLines(ink: BooleanArray, w: Int, h: Int): Pair<Map<Double, List<QLine>>, Double> {
        val prof = DoubleArray(h) { y -> var c = 0; for (x in 0 until w) if (ink[y * w + x]) c++; c.toDouble() }
        val (p0, ph, fit) = fitPitch(prof, pack.template, 24.0, 200.0) ?: return emptyMap<Double, List<QLine>>() to 0.0
        val sm = smooth(prof, max(3, p0.toInt() / 4))
        val out = LinkedHashMap<Double, MutableList<QLine>>()
        for (f in SCALES) {
            val p = p0 * f
            val key = round(p * 100) / 100
            var y = ph
            while (y < h + 0.5 * p) {
                val a = max(0.0, y - p / 8).toInt(); val b = min(h.toDouble(), y + p / 8 + 1).toInt()
                var base = y.toInt()
                if (b > a) { base = a; for (k in a until b) if (sm[k] > sm[base]) base = k }
                strip(ink, w, h, base, p)?.let { (st, sw, cx0) -> if (sw >= 20) out.getOrPut(key) { ArrayList() } += QLine(base, st, sw, cx0) }
                y += p
            }
        }
        return out to fit
    }

    // ---------------------------------------------------------------- matching

    /** 20-row query strip → pack resolution ([MushafPack.rows] rows), 8-bit rounded like the Python twin. */
    private fun quantize(st: FloatArray, w: Int): Pair<FloatArray, Int> {
        val rows = pack.rows
        val ow = max(4, round(w * rows / 20.0).toInt())
        val a = if (rows == 20 && ow == w) st.copyOf() else resizeBox(st, 20, w, rows, ow)
        for (i in a.indices) a[i] = round(a[i] * 255f) / 255f
        return a to ow
    }

    private fun coarse(q: QLine): Coarse? {
        val (qs, qw) = quantize(q.strip, q.w)
        val kr = pack.coarseRowPool; val kc = pack.coarseColPool; val rows = pack.coarseRows
        val wq = qw / kc
        if (wq < 6 || wq > 1000) return null
        val qc = FloatArray(rows * wq)
        for (r in 0 until rows) for (c in 0 until wq) {
            var s = 0f
            for (dy in 0 until kr) for (dx in 0 until kc) s += qs[(r * kr + dy) * qw + c * kc + dx]
            qc[r * wq + c] = s / (kr * kc)
        }
        val mean = qc.average().toFloat()
        var qn = 0.0
        for (i in qc.indices) { qc[i] -= mean; qn += qc[i].toDouble() * qc[i] }
        qn = sqrt(qn) + 1e-9
        val nPos = pack.coarseN - wq + 1
        if (nPos <= 0) return null
        val num = FloatArray(nPos)
        val n = pack.lines.size
        val chunks = POOL_THREADS * 4
        val len = (nPos + chunks - 1) / chunks
        val cells = rows * wq.toDouble()
        val nLines = n
        // Each chunk computes NCC on its range and keeps per-line maxima; merged below.
        val parts = pool.invokeAll((0 until chunks).map { ch ->
            Callable {
                val t0 = ch * len; val t1 = min(nPos, t0 + len)
                val best = FloatArray(nLines) { -1f }; val bestT = IntArray(nLines)
                if (t0 < t1) {
                    val R = pack.coarse; val N = pack.coarseN
                    for (r in 0 until rows) for (x in 0 until wq) {
                        val qv = qc[r * wq + x]
                        if (qv == 0f) continue
                        val base = r * N + x
                        for (t in t0 until t1) num[t] += qv * R[base + t]
                    }
                    val lo = (0.2 * wq).toInt()
                    for (t in t0 until t1) {
                        val c = t + wq / 2
                        val li = pack.lineOfColumn[c]
                        val local = c - pack.starts[li]
                        if (local < lo || local > pack.coarseWidths[li] - lo) continue
                        val a1 = pack.s1[t + wq] - pack.s1[t]; val a2 = pack.s2[t + wq] - pack.s2[t]
                        val v = (num[t] / (sqrt(max(a2 - a1 * a1 / cells, 1e-6)) * qn)).toFloat()
                        if (v > best[li]) { best[li] = v; bestT[li] = t }
                    }
                }
                best to bestT
            }
        })
        val best = FloatArray(nLines) { -1f }; val bestT = IntArray(nLines)
        for (p in parts) {
            val (pb, pt) = p.get()
            for (li in 0 until nLines) if (pb[li] > best[li]) { best[li] = pb[li]; bestT[li] = pt[li] }
        }
        return Coarse(best, bestT, qs, qw)
    }

    private fun fine(c: Coarse, i: Int, q: FloatArray, qw: Int): Pair<Float, Int> {
        if (i < 0 || c.best[i] <= -1f) return -1f to 0
        val o = (c.bestT[i] - pack.starts[i]) * pack.coarseColPool
        val d = pack.coarseColPool + 2 + (0.05 * qw).toInt()
        return nccWindow(q, qw, pack.strip(i), pack.lines[i].w, pack.rows, o - d, o + d)
    }

    companion object {
        private const val TOP_K = 40
        private const val MAX_LINES = 3
        private val SCALES = doubleArrayOf(0.96, 1.0, 1.04)
        private const val UPP = 0.62
        private const val DOWNP = 0.38
        private const val NB = 20
        private const val MIN_OVERLAP = 0.7
        private val POOL_THREADS = max(1, min(4, Runtime.getRuntime().availableProcessors()))
        private val pool by lazy {
            Executors.newFixedThreadPool(POOL_THREADS) { r -> Thread(r, "mushaf-match").apply { isDaemon = true } }
        }

        /** Best (pitch, phase of a baseline) fitting the periodic one-line template to the row profile. */
        internal fun fitPitch(prof: DoubleArray, tmpl: FloatArray, lo: Double, hi: Double): Triple<Double, Double, Double>? {
            val h = prof.size
            if ((prof.maxOrNull() ?: 0.0) == 0.0) return null
            val mean = prof.average()
            val pz = DoubleArray(h) { prof[it] - mean }
            var pn = 0.0; for (v in pz) pn += v * v; pn = sqrt(pn) + 1e-9
            val tz = DoubleArray(tmpl.size + 1) { tmpl[it % tmpl.size].toDouble() }
            val t = DoubleArray(h)
            var bestV = -2.0; var bestP = -1.0; var bestPh = 0.0
            val stop = min(hi, 2.2 * h) + 1e-9
            var p = lo
            while (p < stop) {
                val step = max(1.0, p / 40)
                var k = 0
                while (k * step < p) {
                    val ph = k * step
                    var sum = 0.0
                    for (y in 0 until h) {
                        var u = ((y - ph) / p + UPP) % 1.0
                        if (u < 0) u += 1.0
                        u *= NB
                        val i0 = floor(u).toInt(); val f = u - i0
                        t[y] = tz[i0] * (1 - f) + tz[i0 + 1] * f; sum += t[y]
                    }
                    val tm = sum / h
                    var num = 0.0; var tt = 0.0
                    for (y in 0 until h) { val d = t[y] - tm; num += d * pz[y]; tt += d * d }
                    val v = num / ((sqrt(tt) + 1e-9) * pn)
                    if (v > bestV) { bestV = v; bestP = p; bestPh = ph }
                    k++
                }
                p += 1.0
            }
            return if (bestP > 0) Triple(bestP, bestPh, bestV) else null
        }

        private fun smooth(v: DoubleArray, win: Int): DoubleArray {
            val c = DoubleArray(v.size + 1); for (i in v.indices) c[i + 1] = c[i] + v[i]
            return DoubleArray(v.size) { i ->
                val a = max(0, i - win / 2); val b = min(v.size, i + win / 2 + 1)
                (c[b] - c[a]) / (b - a)
            }
        }

        /** Strip of the line with baseline [base] at fractional [pitch]: 20 rows, scale-normalized. */
        internal fun strip(ink: BooleanArray, w: Int, h: Int, base: Int, pitch: Double): Triple<FloatArray, Int, Int>? {
            val top = round(base - UPP * pitch).toInt(); val bot = round(base + DOWNP * pitch).toInt()
            val sh = bot - top
            if (sh <= 0) return null
            var cx0 = w; var cx1 = -1
            for (y in max(0, top) until min(h, bot)) for (x in 0 until w) if (ink[y * w + x]) { if (x < cx0) cx0 = x; if (x > cx1) cx1 = x }
            if (cx1 < 0) return null
            cx1 += 1
            val sw = cx1 - cx0
            val ow = max(4, round(sw * (NB.toDouble() / sh)).toInt())
            val sub = FloatArray(sh * sw)
            for (yy in 0 until sh) { val y = top + yy; if (y in 0 until h) for (xx in 0 until sw) if (ink[y * w + cx0 + xx]) sub[yy * sw + xx] = 1f }
            return Triple(resizeBox(sub, sh, sw, NB, ow), ow, cx0)
        }

        /** Area-average resize (same index rounding as bookfp.resize_box). */
        internal fun resizeBox(a: FloatArray, h: Int, w: Int, oh: Int, ow: Int): FloatArray {
            val ii = DoubleArray((h + 1) * (w + 1))
            for (y in 0 until h) {
                var row = 0.0
                for (x in 0 until w) { row += a[y * w + x]; ii[(y + 1) * (w + 1) + x + 1] = ii[y * (w + 1) + x + 1] + row }
            }
            val out = FloatArray(oh * ow)
            for (i in 0 until oh) {
                val y0 = (i.toDouble() * h / oh).toInt(); val y1 = max(y0 + 1, ceil((i + 1).toDouble() * h / oh).toInt())
                for (j in 0 until ow) {
                    val x0 = (j.toDouble() * w / ow).toInt(); val x1 = max(x0 + 1, ceil((j + 1).toDouble() * w / ow).toInt())
                    val s = ii[y1 * (w + 1) + x1] - ii[y0 * (w + 1) + x1] - ii[y1 * (w + 1) + x0] + ii[y0 * (w + 1) + x0]
                    out[i * ow + j] = (s / ((y1 - y0) * (x1 - x0))).toFloat()
                }
            }
            return out
        }

        /** Best zero-mean NCC of q (rows × qw) placed at offsets [lo, hi] of r (rows × rw). */
        internal fun nccWindow(q: FloatArray, qw: Int, r: FloatArray, rw: Int, rows: Int, lo: Int, hi: Int): Pair<Float, Int> {
            var best = -1f; var bestOff = lo
            for (off in lo..hi) {
                val a0 = max(0, off); val a1 = min(rw, off + qw)
                if (a1 - a0 < MIN_OVERLAP * qw) continue
                var sr = 0.0; var sq = 0.0; var srr = 0.0; var sqq = 0.0; var srq = 0.0
                for (y in 0 until rows) {
                    val ro = y * rw; val qo = y * qw - off
                    for (x in a0 until a1) {
                        val rv = r[ro + x].toDouble(); val qv = q[qo + x].toDouble()
                        sr += rv; sq += qv; srr += rv * rv; sqq += qv * qv; srq += rv * qv
                    }
                }
                val cnt = (a1 - a0).toDouble() * rows
                val den = sqrt((srr - sr * sr / cnt) * (sqq - sq * sq / cnt))
                val v = if (den > 1e-9) ((srq - sr * sq / cnt) / den).toFloat() else -1f
                if (v > best) { best = v; bestOff = off }
            }
            return best to bestOff
        }
    }
}
