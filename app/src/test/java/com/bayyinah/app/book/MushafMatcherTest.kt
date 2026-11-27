package com.bayyinah.app.book

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

/**
 * Runs the whole-Mushaf matcher on synthetic camera shots (tools/mushaf/gen_mushaf_crops.py) with the
 * locally built pack (tools/mushaf/build_mushaf_pack.py). Both are derived from the publisher's scans,
 * so they stay in book-source/ (git-ignored) and the test is skipped when they're absent.
 */
class MushafMatcherTest {
    private val packFile = File("../book-source/mushaf-fp/mushaf-warsh.bayyinah-pack")
    private val dir = File("../book-source/mushaf-crops")
    private val pyFile = File("../book-source/mushaf-fp/py_decisions.json")

    @Test fun pack_loads() {
        assumeTrue("pack not built", packFile.exists())
        val pack = packFile.inputStream().buffered().use { MushafPack.read(it) }
        assertEquals(10, pack.rows)
        assertTrue(pack.lines.size > 9000)
        assertEquals(4, pack.printedPage(9))
    }

    @Test fun notAPack_rejected() {
        val e = runCatching { MushafPack.read("hello world, not a pack".toByteArray()) }.exceptionOrNull()
        assertTrue(e is IllegalArgumentException)
    }

    @Test fun syntheticCameraShots() {
        assumeTrue("pack or crops not present", packFile.exists() && File(dir, "labels.json").exists())
        val t0 = System.nanoTime()
        val pack = packFile.inputStream().buffered().use { MushafPack.read(it) }
        println("pack load ${(System.nanoTime() - t0) / 1_000_000} ms, coarse ${pack.coarseRows}×${pack.coarseN}")
        val matcher = MushafMatcher(pack)
        val labels = org.json.JSONArray(File(dir, "labels.json").readText())
        val py = if (pyFile.exists()) org.json.JSONObject(pyFile.readText()) else null
        var right = 0; var wrong = 0; var rejected = 0; var falsePos = 0; var pos = 0; var neg = 0
        var samePy = 0; var compared = 0
        val times = ArrayList<Long>()
        for (i in 0 until labels.length()) {
            val l = labels.getJSONObject(i)
            val name = l.getString("file")
            val img = ImageIO.read(File(dir, name))
            val w = img.width; val h = img.height
            val lum = IntArray(w * h) { k ->   // same as PIL convert('L')
                val rgb = img.getRGB(k % w, k / w)
                (((rgb shr 16) and 0xFF) * 19595 + ((rgb shr 8) and 0xFF) * 38470 + (rgb and 0xFF) * 7471 + 0x8000) shr 16
            }
            val s = System.nanoTime()
            val r = matcher.match(lum, w, h)
            times += (System.nanoTime() - s) / 1_000_000
            if (py != null && !py.isNull(name)) {
                val p = py.getJSONObject(name); compared++
                if (r != null && r.pdfPage == p.getInt("page") && r.line == p.getInt("line") && kotlin.math.abs(r.score - p.getDouble("score")) < 0.02) samePy++
                else if (i < 400) println("DIFF $name kotlin=$r python=$p")
            }
            val page = l.getInt("page")
            if (page == 0) { neg++; if (r?.accepted == true) { falsePos++; println("FALSE MATCH $name -> $r") } }
            else {
                pos++
                val lines = l.getJSONArray("lines").let { a -> List(a.length()) { a.getInt(it) } }
                when {
                    r?.accepted != true -> rejected++
                    r.pdfPage == page && kotlin.math.abs(r.line - (lines.firstOrNull() ?: 0)) <= lines.size -> right++
                    else -> { wrong++; println("WRONG $name page $page -> $r") }
                }
            }
        }
        times.sort()
        println("Mushaf matcher: $right/$pos recognised, $wrong wrong, $rejected rejected; $falsePos/$neg false matches; " +
            "same as Python $samePy/$compared; per crop median ${times[times.size / 2]} ms, p90 ${times[times.size * 9 / 10]} ms (JVM)")
        assertEquals("wrong verse", 0, wrong)
        assertEquals("false matches on non-Quran text", 0, falsePos)
        assertTrue("recognition regressed: $right/$pos", right >= 235)
    }
}
