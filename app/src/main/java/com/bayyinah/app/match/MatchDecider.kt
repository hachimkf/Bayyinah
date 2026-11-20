package com.bayyinah.app.match

import kotlin.math.abs

/**
 * Turns the ranked candidates of several OCR passes (same crop, different text sizes) into one
 * decision. Calibrated on rendered + camera-degraded Warsh/Naskh verse images and on non-Quran
 * Arabic sentences (which must never match):
 *  - one pass is decisive when its best score is high and clearly ahead of the runner-up;
 *  - a very high score wins even against a near-identical verse, if it is still strictly ahead;
 *  - otherwise two passes must agree on the same best verse with a reasonable score.
 *
 * Some verses have exactly the same wording (26:1 / 28:1, 69:40 / 81:19, …). They tie exactly;
 * the tie is treated as one answer and resolved to the verse nearest [near] — the verse she was
 * just reading, since a learner moves through her book in order.
 */
object MatchDecider {
    const val SINGLE_MIN = 0.40f
    const val SINGLE_REL_MARGIN = 0.12f
    const val AGREE_MIN = 0.42f  // 0.38 accepted a wrong verse on a Maghribi line spanning two ayat
    /** Near-duplicate verses (e.g. 1:1 vs 37:182) score close together; a very good read still wins. */
    const val HIGH_SCORE = 0.80f
    const val HIGH_MIN_LEAD = 0.015f
    private const val TIE = 1e-4f

    /** Decisive on its own? Lets the caller skip the remaining (slow) OCR passes. */
    fun decisive(top: List<VerseMatcher.Candidate>, near: Int? = null): Int? {
        val best = top.firstOrNull() ?: return null
        val tied = top.filter { best.score - it.score < TIE }
        val second = top.firstOrNull { best.score - it.score >= TIE }?.score ?: 0f
        val lead = best.score - second
        val ok = (best.score >= SINGLE_MIN && lead >= SINGLE_REL_MARGIN * best.score) ||
            (best.score >= HIGH_SCORE && lead >= HIGH_MIN_LEAD)
        return if (ok) pick(tied, near) else null
    }

    fun decide(passes: List<List<VerseMatcher.Candidate>>, near: Int? = null): Int? {
        passes.forEach { top -> decisive(top, near)?.let { return it } }
        val firsts = passes.mapNotNull { top -> top.firstOrNull()?.let { b -> top.filter { b.score - it.score < TIE } } }
        if (firsts.isEmpty()) return null
        // Count agreement per verse, letting a tie group vote for each of its members.
        val votes = HashMap<Int, Int>(); val bestScore = HashMap<Int, Float>()
        for (group in firsts) for (c in group) {
            votes[c.id] = (votes[c.id] ?: 0) + 1
            bestScore[c.id] = maxOf(bestScore[c.id] ?: 0f, c.score)
        }
        val maxVotes = votes.values.max()
        if (maxVotes < 2) return null
        val winners = votes.filter { it.value == maxVotes }.keys
            .map { VerseMatcher.Candidate(it, bestScore.getValue(it)) }
            .filter { it.score >= AGREE_MIN }
        return if (winners.isEmpty()) null else pick(winners, near)
    }

    private fun pick(tied: List<VerseMatcher.Candidate>, near: Int?): Int =
        if (near == null || tied.size == 1) tied.minOf { it.id }
        else tied.minBy { abs(it.id - near) }.id
}
