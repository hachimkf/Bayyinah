package com.bayyinah.app.match

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class VerseMatcherTest {

    private val verses: List<Pair<Int, String>> by lazy {
        // Reuse the sample TSV so tests and the shipped sample DB never drift apart.
        val tsv = listOf("../tools/sample_verses.tsv", "tools/sample_verses.tsv").map(::File).first { it.exists() }
        tsv.readLines().filter { it.isNotBlank() && !it.startsWith("#") }.map {
            val c = it.split('\t'); (c[0].toInt() * 1000 + c[1].toInt()) to c[3]
        }
    }
    private val matcher by lazy {
        VerseMatcher(verses.map { VerseMatcher.Entry(it.first, ArabicNormalizer.normalize(it.second)) })
    }

    @Test fun normalizer_stripsTashkeelAndUnifiesLetters() {
        assertEquals("الحمد لله رب العلمين", ArabicNormalizer.normalize("ٱلْحَمْدُ لِلَّهِ رَبِّ ٱلْعَٰلَمِينَ"))
        assertEquals("اياك نعبد", ArabicNormalizer.normalize("إِيَّاكَ ۝ نَعْبُدُ ﴿٤﴾"))
        assertEquals("في الجنه", ArabicNormalizer.normalize("فِى ٱلْجَنَّةِ"))
        assertEquals("فل", ArabicNormalizer.normalize("ڢـل")) // Maghrebi fa + tatweel
        assertEquals("ق", ArabicNormalizer.normalize("ڧ"))
    }

    @Test fun normalizer_isIdempotent() {
        verses.forEach { (_, t) ->
            val once = ArabicNormalizer.normalize(t)
            assertEquals(once, ArabicNormalizer.normalize(once))
        }
    }

    @Test fun exactText_matchesEveryVerseOfTwoWordsOrMore() {
        verses.filter { ArabicNormalizer.normalize(it.second).contains(' ') }.forEach { (id, text) ->
            assertEquals("verse $id", id, matcher.match(text)?.id)
        }
    }

    @Test fun hafsSpellingOfWarshVerse_stillMatches() {
        // Hafs prints "مالك" / "العالمين"; Warsh book prints "ملك" / "العلمين".
        assertEquals(1001, matcher.match("الحمد لله رب العالمين")?.id)
        assertEquals(1003, matcher.match("مالك يوم الدين")?.id)
    }

    @Test fun noisyOcr_withDotConfusionAndDroppedLetters_matches() {
        // ق read as ف, one letter dropped, stray Latin/digits from the page margin.
        assertEquals(113001, matcher.match("12 قل اعود بر ب الفلف |")?.id)
        assertEquals(103003, matcher.match("الا الذين امنوا وعملو الصلحت وتواصوا بالحق")?.id)
        assertEquals(114005, matcher.match("الذي يوسوس في صدور الناس ٥")?.id)
    }

    @Test fun garbage_returnsNull() {
        assertNull(matcher.match("ـــ ، ، 123 abc"))
        assertNull(matcher.match("سيارة حمراء كبيرة جدا"))
    }
}
