package com.bayyinah.app.book

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

/**
 * Runs the on-phone book matcher on synthetic camera shots of the book (tools/book/gen_testcrops.py).
 * The crops are images of the book, so they stay local in book-source/ (git-ignored): the test is
 * skipped when they're absent.
 */
class BookMatcherTest {

    private val refs by lazy { File("src/main/assets/book_refs.bin").inputStream().use { BookRefs.read(it) } }
    private val dir = File("../book-source/testcrops")

    @Test fun refs_load() {
        assertEquals(24, refs.lessons.size)
        assertTrue(refs.lines.size > 100)
        assertEquals(92, refs.lessons.first().page)
        assertTrue(refs.lines.all { it.h == 20 && it.spans.isNotEmpty() })
    }

    @Test fun syntheticCameraShots_recognisedWithoutFalseMatches() {
        assumeTrue("book-source/testcrops not present", File(dir, "labels.json").exists())
        val labels = org.json.JSONArray(File(dir, "labels.json").readText())
        val matcher = BookMatcher(refs)
        var right = 0; var wrong = 0; var rejected = 0; var falsePos = 0; var pos = 0; var neg = 0
        val t0 = System.nanoTime()
        for (i in 0 until labels.length()) {
            val l = labels.getJSONObject(i)
            val img = ImageIO.read(File(dir, l.getString("file")))
            val w = img.width; val h = img.height
            val lum = IntArray(w * h) { k ->
                val rgb = img.getRGB(k % w, k / w)
                (((rgb shr 16) and 0xFF) * 299 + ((rgb shr 8) and 0xFF) * 587 + (rgb and 0xFF) * 114) / 1000
            }
            val r = matcher.match(lum, w, h)
            val page = l.getInt("page")
            if (page == 0) { neg++; if (r?.accepted == true) { falsePos++; println("FALSE MATCH ${l.getString("file")} -> $r") } }
            else {
                pos++
                when {
                    r?.accepted != true -> rejected++
                    r.page == page -> right++
                    else -> { wrong++; println("WRONG ${l.getString("file")} page $page -> $r") }
                }
            }
        }
        val ms = (System.nanoTime() - t0) / 1_000_000 / labels.length()
        println("Book matcher: $right/$pos recognised, $wrong wrong, $rejected rejected; $falsePos/$neg false matches; ~$ms ms per crop (JVM)")
        assertEquals("wrong verse", 0, wrong)
        assertEquals("false matches on non-Quran text", 0, falsePos)
        assertTrue("recognition regressed: $right/$pos", right >= 36)
    }
}
