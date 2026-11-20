package com.bayyinah.app.match

import kotlin.math.max
import kotlin.math.min

/**
 * In-memory fuzzy matcher from noisy OCR text to a verse id.
 *
 * 1. Candidate retrieval: an inverted index of character trigrams over the dot-merged
 *    skeleton (spaces removed, so OCR word-splitting errors don't hurt) gives a Dice score
 *    for every verse sharing trigrams with the query. Top [CANDIDATES] are kept.
 * 2. Re-ranking: tokens are aligned with an edit-distance tolerance proportional to word
 *    length. Final score blends trigram Dice, OCR-token precision and verse coverage.
 *
 * Index build for a full 6,214-verse Mushaf takes ~100–200 ms on a budget phone and a few MB.
 */
class VerseMatcher(entries: List<Entry>) {

    data class Entry(val id: Int, val normalizedText: String)
    data class Match(val id: Int, val score: Float, val runnerUpScore: Float)
    data class Candidate(val id: Int, val score: Float)

    private class Indexed(val id: Int, val tokens: List<String>, val trigramCount: Int)

    private val indexed: Array<Indexed>
    private val postings: HashMap<Long, IntArray>

    init {
        val tmpPostings = HashMap<Long, MutableList<Int>>()
        indexed = Array(entries.size) { i ->
            val e = entries[i]
            val skel = ArabicNormalizer.skeleton(e.normalizedText)
            val grams = trigrams(skel)
            for (g in grams) tmpPostings.getOrPut(g) { ArrayList(4) }.add(i)
            Indexed(e.id, tokens(skel), grams.size)
        }
        postings = HashMap(tmpPostings.size * 2)
        for ((k, v) in tmpPostings) postings[k] = v.toIntArray()
    }

    /** Returns the best confident match, or null if the OCR text is too weak/ambiguous. */
    fun match(ocrText: String): Match? {
        val top = rank(ocrText)
        if (top.isEmpty()) return null
        val best = top[0]; val second = top.getOrNull(1)?.score ?: 0f
        val confident = best.score >= STRONG_SCORE ||
            (best.score >= MIN_SCORE && best.score - second >= MIN_MARGIN)
        return if (confident) Match(best.id, best.score, second) else null
    }

    /** Top [limit] candidates, best first (empty if the text carries too little signal). */
    fun rank(ocrText: String, limit: Int = 3): List<Candidate> {
        val normalized = ArabicNormalizer.normalize(cleanOcrArtifacts(ocrText))
        val skel = ArabicNormalizer.skeleton(normalized)
        val queryTokens = tokens(skel).filter { it.length >= 2 }
        val queryGrams = trigrams(skel)
        if (queryGrams.size < MIN_QUERY_TRIGRAMS || queryTokens.isEmpty()) return emptyList()

        // 1) Trigram Dice via inverted index
        val shared = IntArray(indexed.size)
        for (g in queryGrams) postings[g]?.forEach { shared[it]++ }
        val candidates = shared.indices
            .filter { shared[it] > 0 }
            .sortedByDescending { dice(shared[it], queryGrams.size, indexed[it].trigramCount) }
            .take(CANDIDATES)

        // 2) Token-level re-ranking
        return candidates.map { i ->
            val v = indexed[i]
            val d = dice(shared[i], queryGrams.size, v.trigramCount)
            val (precision, coverage) = tokenAlignment(queryTokens, v.tokens)
            // The learner frames one verse: reward covering it, but tolerate a cropped frame.
            Candidate(v.id, 0.40f * d + 0.35f * precision + 0.25f * coverage)
        }.sortedByDescending { it.score }.take(limit)
    }

    /** Length-weighted share of OCR tokens found in the verse, and share of verse tokens seen. */
    private fun tokenAlignment(query: List<String>, verse: List<String>): Pair<Float, Float> {
        val used = BooleanArray(verse.size)
        var matchedWeight = 0f; var totalWeight = 0f; var matchedVerse = 0
        for (q in query) {
            val w = q.length.toFloat()
            totalWeight += w
            var bestJ = -1; var bestSim = 0f
            for (j in verse.indices) {
                if (used[j]) continue
                val s = similarity(q, verse[j])
                if (s > bestSim) { bestSim = s; bestJ = j; if (s == 1f) break }
            }
            if (bestJ >= 0 && bestSim >= TOKEN_ACCEPT) {
                used[bestJ] = true; matchedVerse++
                matchedWeight += w * bestSim
            }
        }
        val precision = if (totalWeight > 0f) matchedWeight / totalWeight else 0f
        val coverage = if (verse.isNotEmpty()) matchedVerse.toFloat() / verse.size else 0f
        return precision to coverage
    }

    companion object {
        private const val CANDIDATES = 25
        private const val MIN_QUERY_TRIGRAMS = 3
        private const val TOKEN_ACCEPT = 0.6f
        private const val STRONG_SCORE = 0.72f
        private const val MIN_SCORE = 0.42f
        private const val MIN_MARGIN = 0.06f

        /** Tesseract often reads the wasla alef (\u0671 / KFGQPC dotted alef) as "(تا" or "[تا". */
        private val waslaArtifact = Regex("[(\\[\\]|]\u062A?\u0627")
        internal fun cleanOcrArtifacts(s: String): String = waslaArtifact.replace(s, "\u0627")

        internal fun tokens(s: String): List<String> = s.split(' ').filter { it.isNotEmpty() }

        /** Character trigrams of the text with spaces removed, packed collision-free into Longs. */
        internal fun trigrams(s: String): Set<Long> {
            val compact = s.replace(" ", "")
            if (compact.length < 3) return emptySet()
            val out = HashSet<Long>(compact.length)
            for (i in 0..compact.length - 3) {
                out.add((compact[i].code.toLong() shl 32) or (compact[i + 1].code.toLong() shl 16) or compact[i + 2].code.toLong())
            }
            return out
        }

        private fun dice(shared: Int, a: Int, b: Int): Float =
            if (a + b == 0) 0f else 2f * shared / (a + b)

        /** 1 - normalized Levenshtein distance, early-exiting on clearly different words. */
        internal fun similarity(a: String, b: String): Float {
            if (a == b) return 1f
            val maxLen = max(a.length, b.length)
            if (kotlin.math.abs(a.length - b.length) > maxLen / 2) return 0f
            var prev = IntArray(b.length + 1) { it }
            var cur = IntArray(b.length + 1)
            for (i in 1..a.length) {
                cur[0] = i
                for (j in 1..b.length) {
                    val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                    cur[j] = min(min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost)
                }
                val t = prev; prev = cur; cur = t
            }
            return 1f - prev[b.length].toFloat() / maxLen
        }
    }
}
