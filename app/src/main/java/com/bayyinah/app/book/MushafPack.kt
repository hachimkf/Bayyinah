package com.bayyinah.app.book

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.InflaterInputStream

/**
 * Line fingerprints of the whole printed Mushaf (Warsh, Mushaf Mohammadi), imported by the user
 * as a `.bayyinah-pack` file. The pack is built from the publisher's scans with
 * tools/mushaf/build_mushaf_pack.py and is never shipped with the app.
 *
 * Every line stores a [rows]-row strip quantized to [bits] bits (values 0..levels) and which ayah
 * covers which strip columns. The coarse index used by [MushafMatcher] is built here at load time.
 */
class MushafPack private constructor(
    val rows: Int,
    val bits: Int,
    /** Mean row-ink profile of one line (20 bins), used to measure a crop's line spacing. */
    val template: FloatArray,
    val printedPageOffset: Int,
    val lines: List<Line>,
    /** All line strips back to back, one level (0..levels) per byte; line i starts at [Line.offset]. */
    private val levels: ByteArray,
) {
    class Span(val surah: Int, val ayah: Int, val x0: Int, val x1: Int)

    class Line(val pdfPage: Int, val lineNo: Int, val spans: List<Span>, val w: Int, val offset: Int)

    private val maxLevel = ((1 shl bits) - 1).toFloat()

    /** Strip of line [i] as floats 0..1, row-major rows × w. */
    fun strip(i: Int): FloatArray {
        val l = lines[i]
        return FloatArray(rows * l.w) { (levels[l.offset + it].toInt() and 0xFF) / maxLevel }
    }

    /** Index of the next line on the same page, or -1. */
    val next = IntArray(lines.size) { i -> if (i + 1 < lines.size && lines[i + 1].pdfPage == lines[i].pdfPage) i + 1 else -1 }

    // ---------------------------------------------------------------- coarse index
    // Every strip pooled (rows/COARSE_ROWS) × (that × POOLX) and laid end to end with GAP empty columns.

    val coarseRowPool = rows * COARSE_POOL / 20
    val coarseColPool = coarseRowPool * POOLX
    val coarseRows = rows / coarseRowPool
    val starts = IntArray(lines.size)
    val coarseWidths = IntArray(lines.size)
    /** coarseRows × coarseN, row-major. */
    val coarse: FloatArray
    val coarseN: Int
    /** Prefix sums over columns of Σrows v and Σrows v² (length coarseN + 1). */
    val s1: DoubleArray
    val s2: DoubleArray
    /** For every coarse column, the line it belongs to (gap columns belong to the line before). */
    val lineOfColumn: IntArray

    init {
        var pos = 0
        for (i in lines.indices) {
            starts[i] = pos
            coarseWidths[i] = lines[i].w / coarseColPool
            pos += coarseWidths[i] + GAP
        }
        coarseN = pos
        coarse = FloatArray(coarseRows * coarseN)
        lineOfColumn = IntArray(coarseN)
        val area = (coarseRowPool * coarseColPool).toFloat()
        for (i in lines.indices) {
            val l = lines[i]; val cw = coarseWidths[i]; val s0 = starts[i]
            val end = if (i + 1 < lines.size) starts[i + 1] else coarseN
            java.util.Arrays.fill(lineOfColumn, s0, end, i)
            for (r in 0 until coarseRows) for (c in 0 until cw) {
                var sum = 0
                for (dy in 0 until coarseRowPool) {
                    val row = l.offset + (r * coarseRowPool + dy) * l.w + c * coarseColPool
                    for (dx in 0 until coarseColPool) sum += levels[row + dx].toInt() and 0xFF
                }
                coarse[r * coarseN + s0 + c] = sum / maxLevel / area
            }
        }
        s1 = DoubleArray(coarseN + 1); s2 = DoubleArray(coarseN + 1)
        for (c in 0 until coarseN) {
            var a = 0.0; var b = 0.0
            for (r in 0 until coarseRows) { val v = coarse[r * coarseN + c].toDouble(); a += v; b += v * v }
            s1[c + 1] = s1[c] + a; s2[c + 1] = s2[c] + b
        }
    }

    fun printedPage(pdfPage: Int) = pdfPage + printedPageOffset

    companion object {
        const val FILE_EXTENSION = "bayyinah-pack"
        private const val COARSE_POOL = 4
        private const val POOLX = 2
        private const val GAP = 16

        /** Reads and fully validates a pack; throws IllegalArgumentException when it isn't one. */
        fun read(input: InputStream): MushafPack {
            val head = ByteArray(8)
            var got = 0
            while (got < 8) { val n = input.read(head, got, 8 - got); require(n > 0) { "File too short" }; got += n }
            require(String(head, 0, 4, Charsets.US_ASCII) == "BYMP") { "Not a Bayyinah Mushaf pack" }
            val version = (head[4].toInt() and 0xFF) or ((head[5].toInt() and 0xFF) shl 8)
            require(version == 1) { "Unsupported pack version $version" }
            val body = InflaterInputStream(input).readBytes()
            return parse(ByteBuffer.wrap(body).order(ByteOrder.LITTLE_ENDIAN))
        }

        fun read(bytes: ByteArray): MushafPack = read(ByteArrayInputStream(bytes))

        private fun parse(b: ByteBuffer): MushafPack {
            val rows = b.get().toInt() and 0xFF
            val bits = b.get().toInt() and 0xFF
            require(rows in 4..40 && bits in setOf(1, 2, 4, 8)) { "Bad strip format" }
            val tmpl = FloatArray(b.short.toInt() and 0xFFFF) { b.float }
            b.short   // first PDF page (informational)
            val printedOffset = b.short.toInt()
            val n = b.int
            require(n in 1..100_000) { "Bad line count" }
            val perByte = 8 / bits; val mask = (1 shl bits) - 1
            val lines = ArrayList<Line>(n)
            val levels = java.io.ByteArrayOutputStream(n * rows * 100)
            var offset = 0
            repeat(n) {
                val page = b.short.toInt() and 0xFFFF
                val lineNo = b.get().toInt() and 0xFF
                val ns = b.get().toInt() and 0xFF
                val spans = List(ns) {
                    Span(b.get().toInt() and 0xFF, b.short.toInt() and 0xFFFF, b.short.toInt() and 0xFFFF, b.short.toInt() and 0xFFFF)
                }
                val w = b.short.toInt() and 0xFFFF
                val count = rows * w
                val packed = ByteArray((count * bits + 7) / 8).also { b.get(it) }
                val out = ByteArray(count)
                for (k in 0 until count) {
                    val byte = packed[k / perByte].toInt() and 0xFF
                    out[k] = ((byte shr (8 - bits * (k % perByte + 1))) and mask).toByte()
                }
                levels.write(out)
                lines += Line(page, lineNo, spans, w, offset)
                offset += count
            }
            require(!b.hasRemaining()) { "Trailing data in pack" }
            return MushafPack(rows, bits, tmpl, printedOffset, lines, levels.toByteArray())
        }
    }
}
