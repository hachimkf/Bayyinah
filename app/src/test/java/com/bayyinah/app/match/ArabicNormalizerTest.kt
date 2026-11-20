package com.bayyinah.app.match

import org.junit.Assert.assertEquals
import org.junit.Test

/** The normalizer powers search: typed text without tashkeel must match the Warsh text. */
class ArabicNormalizerTest {
    @Test fun stripsTashkeelAndUnifiesLetters() {
        assertEquals("الحمد لله رب العلمين", ArabicNormalizer.normalize("ٱلْحَمْدُ لِلَّهِ رَبِّ ٱلْعَٰلَمِينَ"))
        assertEquals("اياك نعبد", ArabicNormalizer.normalize("إِيَّاكَ ۝ نَعْبُدُ ﴿٤﴾"))
        assertEquals("في الجنه", ArabicNormalizer.normalize("فِى ٱلْجَنَّةِ"))
        assertEquals("فل", ArabicNormalizer.normalize("ڢـل")) // Maghrebi fa + tatweel
    }

    @Test fun kfgqpcWarshCodepoints() {
        // Warsh text from the King Fahd Complex uses Arabic Extended-B alefs and yeh barree.
        assertEquals("الحمد لله رب العلمين", ArabicNormalizer.normalize("ࡴ۬لْحَمْدُ لِلهِ رَبِّ ࡴ۬لْعَٰلَمِينَ"))
        assertEquals("في الدين", ArabicNormalizer.normalize("فِے ࡴ۬لدِّينِ"))
    }

    @Test fun isIdempotent() {
        val t = ArabicNormalizer.normalize("وَلَمْ يَكُن لَّهُۥ كُفُؤاً ࡰحَدࣱ")
        assertEquals(t, ArabicNormalizer.normalize(t))
    }
}
