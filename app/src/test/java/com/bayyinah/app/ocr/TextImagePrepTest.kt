package com.bayyinah.app.ocr

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextImagePrepTest {
    /** Synthetic page: cream background with a lighting gradient and dark "text" bands. */
    private fun page(w: Int, h: Int, bands: List<IntRange>): IntArray = IntArray(w * h) { i ->
        val x = i % w; val y = i / w
        val light = 230 - (x * 60 / w)              // uneven lighting left→right
        if (bands.any { y in it } && (x / 6) % 2 == 0) 40 else light
    }

    @Test fun binarize_findsInkDespiteLightingGradient() {
        val w = 300; val h = 120
        val ink = TextImagePrep.binarize(page(w, h, listOf(40..69)), w, h)
        assertTrue(ink[50 * w + 2])         // in band, dark stripe
        assertFalse(ink[10 * w + 150])      // background, mid-gradient
        assertFalse(ink[100 * w + 290])     // background, darkest side
    }

    @Test fun stripMarks_removesSmallBlobsKeepsLetters() {
        val w = 60; val h = 40
        val ink = BooleanArray(w * h)
        fun fill(x0: Int, y0: Int, x1: Int, y1: Int) { for (y in y0 until y1) for (x in x0 until x1) ink[y * w + x] = true }
        fill(5, 15, 20, 30)   // letter body 15x15
        fill(30, 15, 45, 30)  // letter body 15x15
        fill(10, 5, 13, 8)    // haraka 3x3 above
        fill(35, 33, 37, 35)  // dot / kasra below
        val out = TextImagePrep.stripMarks(ink, w, h)
        assertTrue(out[20 * w + 10]); assertTrue(out[20 * w + 35])
        assertFalse(out[6 * w + 11]); assertFalse(out[34 * w + 36])
        assertArrayEquals(intArrayOf(5, 15, 45, 30), TextImagePrep.inkBounds(out, w, h))
    }

    @Test fun lineCount_countsSeparatedLines() {
        val w = 50; val h = 60
        val ink = BooleanArray(w * h)
        for (y in 5 until 15) for (x in 5 until 45) ink[y * w + x] = true
        for (y in 30 until 42) for (x in 5 until 45) ink[y * w + x] = true
        assertEquals(2, TextImagePrep.lineCount(ink, w, TextImagePrep.inkBounds(ink, w, h)!!))
    }
}
