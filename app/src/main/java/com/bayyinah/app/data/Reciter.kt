package com.bayyinah.app.data

/** An online Warsh recitation: whole-surah MP3s at `{folderUrl}{SSS}.mp3` + per-ayah timings. */
data class Reciter(
    val id: Int,
    val nameAr: String,
    val nameEn: String,
    val folderUrl: String,
)

/** One ayah inside a surah recording. */
data class AyahClip(val url: String, val startMs: Long, val endMs: Long)
