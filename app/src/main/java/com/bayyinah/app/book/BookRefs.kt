package com.bayyinah.app.book

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** One Quran passage of the book: «أقرأ وأتعلم – المستوى الثاني», page [page]. Warsh numbering. */
data class BookLesson(val page: Int, val surah: Int, val firstAyah: Int, val lastAyah: Int)

/** Which ayah covers columns [x0, x1) of a reference line strip. */
data class AyahSpan(val ayah: Int, val x0: Int, val x1: Int)

/** One printed line of a verse box: its fingerprint strip (h × w, 0..1) and ayah spans. */
class RefLine(val page: Int, val surah: Int, val spans: List<AyahSpan>, val h: Int, val w: Int, val strip: FloatArray) {
    /** 2×2-pooled strip for the coarse search. */
    val coarse: Strip = Strip(h, w, strip).pool2()
    val full: Strip get() = Strip(h, w, strip)
}

/** Reads app/src/main/assets/book_refs.bin (format: tools/book/build_book_refs.py). */
class BookRefs(val lessons: List<BookLesson>, val lines: List<RefLine>) {
    /** Index of the next line in the same verse box, or -1. */
    val next: IntArray = IntArray(lines.size) { i -> if (i + 1 < lines.size && lines[i + 1].page == lines[i].page) i + 1 else -1 }

    companion object {
        fun read(input: InputStream): BookRefs {
            val bytes = input.readBytes()
            val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val magic = ByteArray(4).also { b.get(it) }
            require(String(magic) == "BKF1") { "Not a book_refs file" }
            val lessons = List(b.short.toInt() and 0xFFFF) {
                BookLesson(b.short.toInt() and 0xFFFF, b.get().toInt() and 0xFF, b.short.toInt() and 0xFFFF, b.short.toInt() and 0xFFFF)
            }
            val lines = List(b.short.toInt() and 0xFFFF) {
                val page = b.short.toInt() and 0xFFFF
                val surah = b.get().toInt() and 0xFF
                val ns = b.get().toInt() and 0xFF
                val spans = List(ns) { AyahSpan(b.short.toInt() and 0xFFFF, b.short.toInt() and 0xFFFF, b.short.toInt() and 0xFFFF) }
                val h = b.get().toInt() and 0xFF
                val w = b.short.toInt() and 0xFFFF
                val strip = FloatArray(h * w) { (b.get().toInt() and 0xFF) / 255f }
                RefLine(page, surah, spans, h, w, strip)
            }
            return BookRefs(lessons, lines)
        }
    }
}

/** Row-major float image used for fingerprints. */
class Strip(val h: Int, val w: Int, val v: FloatArray) {
    operator fun get(y: Int, x: Int) = v[y * w + x]

    fun pool2(): Strip {
        val h2 = h / 2; val w2 = w / 2
        val out = FloatArray(h2 * w2)
        for (y in 0 until h2) for (x in 0 until w2) {
            out[y * w2 + x] = (v[(2 * y) * w + 2 * x] + v[(2 * y) * w + 2 * x + 1] + v[(2 * y + 1) * w + 2 * x] + v[(2 * y + 1) * w + 2 * x + 1]) / 4f
        }
        return Strip(h2, w2, out)
    }
}
