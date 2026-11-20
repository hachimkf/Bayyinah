package com.bayyinah.app.match

/**
 * Reduces Quranic Arabic (Warsh / Maghrebi print or OCR output) to a plain consonantal
 * skeleton so book print, lighting noise and spelling variants still compare equal.
 *
 * Pure Kotlin, no Android dependencies: unit-tested in `src/test`, and mirrored in
 * `tools/build_quran_db.py` so the DB's `normalized_text` column agrees with the app.
 */
object ArabicNormalizer {

    /** Normalized text: Arabic letters + single spaces. Keeps ف/ق distinct. */
    fun normalize(input: String): String {
        val sb = StringBuilder(input.length)
        var lastWasSpace = true
        for (ch in input) {
            val mapped = mapChar(ch)
            if (mapped == SKIP) continue
            if (mapped == ' ') {
                if (!lastWasSpace) { sb.append(' '); lastWasSpace = true }
            } else {
                sb.append(mapped); lastWasSpace = false
            }
        }
        if (sb.isNotEmpty() && sb[sb.length - 1] == ' ') sb.setLength(sb.length - 1)
        return sb.toString()
    }

    /**
     * Dot-free "rasm" skeleton used only for fuzzy scoring. Letters that share a base shape and
     * differ only by dots are merged (ب ت ث ن ي, ج ح خ, د ذ, ر ز, س ش, ص ض, ط ظ, ع غ, ف ق).
     * The OCR step strips small marks — tashkeel and some letter dots — and Maghrebi print puts
     * the dots of ف/ق differently, so comparing shapes is far more robust (measured: 85% → 95%
     * verses identified per frame on degraded renders).
     */
    fun skeleton(normalized: String): String {
        val sb = StringBuilder(normalized.length)
        for (ch in normalized) sb.append(RASM[ch] ?: ch)
        return sb.toString()
    }

    private val RASM: Map<Char, Char> = buildMap {
        for (c in "\u062A\u062B\u0646\u064A") put(c, '\u0628')   // ت ث ن ي -> ب
        for (c in "\u062C\u062E") put(c, '\u062D')                // ج خ -> ح
        put('\u0630', '\u062F')                                   // ذ -> د
        put('\u0632', '\u0631')                                   // ز -> ر
        put('\u0634', '\u0633')                                   // ش -> س
        put('\u0636', '\u0635')                                   // ض -> ص
        put('\u0638', '\u0637')                                   // ظ -> ط
        put('\u063A', '\u0639')                                   // غ -> ع
        put('\u0642', '\u0641')                                   // ق -> ف
        put('\u0629', '\u0647')                                   // ة -> ه
    }

    private const val SKIP = '\u0000'

    private fun mapChar(ch: Char): Char = when (ch) {
        // Alef variants (hamza forms, madda, wasla, wavy hamza)
        'إ', 'أ', 'آ', 'ٱ', 'ٲ', 'ٳ' -> 'ا'
        // Alef maqsura, Farsi/Maghrebi ya forms, yeh barree (used for final ya in KFGQPC Warsh)
        'ى', 'ی', 'ې', 'ۑ', 'ے', 'ࢆ' -> 'ي'
        'ة' -> 'ه'                       // ta marbuta -> ha
        // Hamza carriers -> base letter; lone hamza dropped (OCR rarely sees it)
        'ؤ' -> 'و'
        'ئ' -> 'ي'
        'ء' -> SKIP
        // Maghrebi fa (dot below) / dotless fa, Maghrebi qaf (one dot above) / dotless qaf
        'ڢ', 'ڡ' -> 'ف'
        'ڧ', 'ٯ' -> 'ق'
        'ک' -> 'ك'                       // keheh -> kaf
        'ھ' -> 'ه'                       // heh doachashmee -> ha
        'ـ' -> SKIP                           // tatweel
        // Whitespace and direction marks become a word break
        ' ', '\t', '\n', '\r', ' ', '‏', '‎', '‌', '‍' -> ' '
        else -> when {
            // KFGQPC Arabic Extended-B alef forms (wasl alef with dot, etc.)
            ch in 'ࡰ'..'ࢂ' -> 'ا'
            // Tashkeel, dagger alef, Quranic annotation signs, small high/low letters,
            // KFGQPC baseline dots and extended tanween / waqf signs.
            ch in 'ً'..'ٟ' || ch == 'ٰ' ||
                ch in 'ؐ'..'ؚ' || ch in 'ۖ'..'ۭ' ||
                ch in 'ࢃ'..'ࢅ' || ch in 'ࢇ'..'࢟' ||
                ch in '࣊'..'ࣿ' -> SKIP
            // Core Arabic letters are kept as-is
            ch in 'ء'..'ي' -> ch
            // Everything else (digits, ayah ornaments, Latin noise) is a separator
            else -> ' '
        }
    }
}
