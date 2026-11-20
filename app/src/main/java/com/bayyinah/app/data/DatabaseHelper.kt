package com.bayyinah.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File

/**
 * Copies `assets/quran_warsh.db` into app storage on first launch (or when [ASSET_DB_VERSION]
 * is bumped) and opens it read-only.
 *
 * Expected schema:
 * ```
 * CREATE TABLE verses (
 *   id INTEGER PRIMARY KEY,      -- surah*1000+ayah (reading order)
 *   surah INTEGER NOT NULL,
 *   ayah INTEGER NOT NULL,
 *   surah_name TEXT NOT NULL,
 *   verse_text TEXT NOT NULL,     -- display text with tashkeel (Warsh)
 *   normalized_text TEXT,         -- optional; recomputed in-app if empty
 *   audio_file TEXT               -- e.g. "020114.m4a"
 * );
 * CREATE TABLE surahs (number INTEGER PRIMARY KEY, name_ar TEXT, name_en TEXT,
 *                      revelation TEXT, ayah_count INTEGER);   -- powers the index screen
 * ```
 * Build it with `tools/build_quran_db.py`.
 */
class DatabaseHelper(private val context: Context) {

    fun open(): SQLiteDatabase {
        val dbFile = context.getDatabasePath(DB_NAME)
        val prefs = context.getSharedPreferences("db", Context.MODE_PRIVATE)
        if (!dbFile.exists() || prefs.getInt(KEY_VERSION, -1) != ASSET_DB_VERSION) {
            copyFromAssets(dbFile)
            prefs.edit().putInt(KEY_VERSION, ASSET_DB_VERSION).apply()
        }
        return SQLiteDatabase.openDatabase(
            dbFile.path,
            null,
            SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
        )
    }

    private fun copyFromAssets(target: File) {
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, "$DB_NAME.tmp")
        context.assets.open(DB_NAME).use { input ->
            tmp.outputStream().use { output -> input.copyTo(output, 64 * 1024) }
        }
        if (!tmp.renameTo(target)) {
            target.delete()
            tmp.renameTo(target)
        }
    }

    companion object {
        const val DB_NAME = "quran_warsh.db"
        /** Bump this whenever you ship a new quran_warsh.db so devices re-copy it. */
        const val ASSET_DB_VERSION = 3
        private const val KEY_VERSION = "asset_db_version"
    }
}
