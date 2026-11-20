package com.bayyinah.app.match

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Runs the real matcher + decider over actual Tesseract output (ara fast model, marks stripped,
 * the app's 3 line heights) for 120 camera-degraded verse renders (KFGQPC Warsh + Naskh fonts) and 10 non-Quran
 * Arabic sentences, against the full 6,214-verse Warsh index.
 */
class OcrRegressionTest {

    private val texts by lazy {
        javaClass.getResourceAsStream("/warsh_normalized.tsv")!!.bufferedReader().readLines()
            .associate { val (id, t) = it.split('\t'); id.toInt() to t }
    }
    private val matcher by lazy { VerseMatcher(texts.map { VerseMatcher.Entry(it.key, it.value) }) }
    private val samples by lazy {
        javaClass.getResourceAsStream("/ocr_samples.tsv")!!.bufferedReader().readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { it.split('\t') }
    }

    private fun decide(texts: List<String>): Int? {
        val passes = ArrayList<List<VerseMatcher.Candidate>>()
        for (t in texts) {
            val top = matcher.rank(t)
            passes += top
            MatchDecider.decisive(top)?.let { return it }
        }
        return MatchDecider.decide(passes)
    }

    @Test fun realOcr_recognitionRateAndNoFalseMatches() {
        var correct = 0; var wrong = 0; var falsePositives = 0; var verses = 0
        for (s in samples) {
            val expected = s[0].toInt()
            val got = decide(s.subList(2, 5))
            if (expected == 0) { if (got != null) falsePositives++ ; continue }
            verses++
            when {
                got == null -> {}
                // Identical wording (26:1 = 28:1 …): any of them is a correct reading.
                got == expected || texts[got] == texts[expected] -> correct++
                else -> { wrong++; println("WRONG $expected -> $got") }
            }
        }
        println("OCR regression: $correct/$verses correct, $wrong wrong, $falsePositives/10 false positives")
        assertEquals("non-Quran text must never match", 0, falsePositives)
        assertTrue("too many wrong verses: $wrong", wrong <= 3)
        assertTrue("recognition regressed: $correct/$verses", correct >= 110)
    }

    @Test fun maghribiScriptPage_crops() {
        val rows = javaClass.getResourceAsStream("/ocr_maghribi.tsv")!!.bufferedReader().readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }.map { it.split('	') }
        var ok = 0; var wrong = 0
        for (r in rows) {
            val accept = r[0].split(',').map { it.toInt() }
            when (val got = decide(r.subList(2, 5))) { null -> {}; in accept -> ok++; else -> { wrong++; println("MAGHRIBI WRONG ${r[1]} -> $got") } }
        }
        println("Maghribi page: $ok/${rows.size} crops correct, $wrong wrong")
        assertTrue("Maghribi recognition regressed: $ok", ok >= 8)
        assertTrue("Maghribi wrong matches: $wrong", wrong <= 1)
    }

    @Test fun identicalVerses_resolveToTheOneNearestTheCurrentVerse() {
        val tie = listOf(VerseMatcher.Candidate(69040, 0.7f), VerseMatcher.Candidate(81019, 0.7f), VerseMatcher.Candidate(5, 0.2f))
        assertEquals(81019, MatchDecider.decisive(tie, near = 81018))
        assertEquals(69040, MatchDecider.decisive(tie, near = 69039))
    }

    @Test fun waslaArtifacts_areCleaned() {
        assertEquals("الكتب", ArabicNormalizer.normalize(VerseMatcher.cleanOcrArtifacts("(تالكتب")))
        assertEquals("الارض", ArabicNormalizer.normalize(VerseMatcher.cleanOcrArtifacts("[تالارض")))
    }

    @Test fun kfgqpcCodepoints_normalize() {
        // Warsh text from the King Fahd Complex uses Arabic Extended-B alefs and yeh barree.
        assertEquals("الحمد لله رب العلمين", ArabicNormalizer.normalize("ࡴ۬لْحَمْدُ لِلهِ رَبِّ ࡴ۬لْعَٰلَمِينَ"))
        assertEquals("في الدين", ArabicNormalizer.normalize("فِے ࡴ۬لدِّينِ"))
    }
}
