package com.bayyinah.app.data

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.bayyinah.app.match.ArabicNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Read-only access to the local verse database. All calls run on [Dispatchers.IO]. */
class VerseRepository(context: Context) {

    private val helper = DatabaseHelper(context.applicationContext)
    @Volatile private var db: SQLiteDatabase? = null

    private fun db(): SQLiteDatabase = db ?: synchronized(this) {
        db ?: helper.open().also { db = it }
    }

    /** (id, normalized text) for every verse, to build the in-memory OCR match index. */
    suspend fun loadMatchEntries(): List<Pair<Int, String>> = withContext(Dispatchers.IO) {
        db().rawQuery("SELECT id, normalized_text, verse_text FROM verses ORDER BY id", null).use { c ->
            buildList(c.count) {
                while (c.moveToNext()) {
                    val stored = if (c.isNull(1)) "" else c.getString(1)
                    add(c.getInt(0) to ArabicNormalizer.normalize(stored.ifBlank { c.getString(2) }))
                }
            }
        }
    }

    suspend fun surahs(): List<Surah> = withContext(Dispatchers.IO) {
        db().rawQuery("SELECT number, name_ar, name_en, revelation, ayah_count FROM surahs ORDER BY number", null).use { c ->
            buildList(c.count) {
                while (c.moveToNext()) add(Surah(c.getInt(0), c.getString(1), c.getString(2), c.getString(3) == "makki", c.getInt(4)))
            }
        }
    }

    suspend fun versesOf(surah: Int): List<Verse> =
        query("SELECT $COLUMNS FROM verses WHERE surah = ? ORDER BY ayah", surah.toString())

    suspend fun byId(id: Int): Verse? = query("SELECT $COLUMNS FROM verses WHERE id = ?", id.toString()).firstOrNull()

    suspend fun byRef(surah: Int, ayah: Int): Verse? = byId(surah * 1000 + ayah)

    /** Next verse in reading order (works even if the DB only holds the book's selection). */
    suspend fun next(current: Verse): Verse? =
        query("SELECT $COLUMNS FROM verses WHERE id > ? ORDER BY id ASC LIMIT 1", current.id.toString()).firstOrNull()

    suspend fun previous(current: Verse): Verse? =
        query("SELECT $COLUMNS FROM verses WHERE id < ? ORDER BY id DESC LIMIT 1", current.id.toString()).firstOrNull()

    /**
     * Verse search on the normalized text, so the learner can type without tashkeel and with
     * any alef/ya/ta-marbuta form. Every word must appear (in any order).
     */
    suspend fun searchVerses(query: String, limit: Int = 60): List<Verse> {
        val words = ArabicNormalizer.normalize(query).split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        val where = words.joinToString(" AND ") { "normalized_text LIKE ?" }
        return query(
            "SELECT $COLUMNS FROM verses WHERE $where ORDER BY id LIMIT $limit",
            *words.map { "%$it%" }.toTypedArray(),
        )
    }

    private suspend fun query(sql: String, vararg args: String): List<Verse> = withContext(Dispatchers.IO) {
        db().rawQuery(sql, args).use { c -> buildList(c.count) { while (c.moveToNext()) add(c.toVerse()) } }
    }

    fun close() {
        synchronized(this) { db?.close(); db = null }
    }

    private fun Cursor.toVerse() = Verse(
        id = getInt(0),
        surah = getInt(1),
        ayah = getInt(2),
        surahName = getString(3),
        verseText = getString(4),
        normalizedText = if (isNull(5)) "" else getString(5),
        audioFile = if (isNull(6)) "" else getString(6),
    )

    private companion object {
        const val COLUMNS = "id, surah, ayah, surah_name, verse_text, normalized_text, audio_file"
    }
}
