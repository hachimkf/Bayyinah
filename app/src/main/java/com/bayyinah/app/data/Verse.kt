package com.bayyinah.app.data

/** One row of `verses` in quran_warsh.db. */
data class Verse(
    val id: Int,
    val surah: Int,
    val ayah: Int,
    val surahName: String,
    val verseText: String,
    val normalizedText: String,
    /** File name relative to `assets/audio/` or `filesDir/audio/`, e.g. `020114.opus`. */
    val audioFile: String,
)
