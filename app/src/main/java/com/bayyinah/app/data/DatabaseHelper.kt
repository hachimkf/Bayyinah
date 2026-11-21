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
        if (!dbFile.exists() || dbFile.length() == 0L || prefs.getInt(KEY_VERSION, -1) != ASSET_DB_VERSION) {
            copyFromAssets(dbFile)
            prefs.edit().putInt(KEY_VERSION, ASSET_DB_VERSION).commit()
        }
        // Self-heal: a copy cut short (power loss, process killed) leaves an empty/corrupt file.
        return openValid(dbFile) ?: run {
            android.util.Log.w("Bayyinah", "Database invalid, copying again")
            copyFromAssets(dbFile)
            openValid(dbFile) ?: error("Database could not be opened")
        }
    }

    private fun openValid(file: File): SQLiteDatabase? = try {
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS)
            .also { db -> db.rawQuery("SELECT count(*) FROM verses", null).use { c -> check(c.moveToFirst() && c.getInt(0) > 0) } }
    } catch (e: Exception) {
        null
    }

    private fun copyFromAssets(target: File) {
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, "$DB_NAME.tmp")
        context.assets.open(DB_NAME).use { input ->
            java.io.FileOutputStream(tmp).use { output ->
                input.copyTo(output, 64 * 1024)
                output.fd.sync() // data must be on disk before the rename makes it "the" database
            }
        }
        target.delete()
        File(target.path + "-journal").delete()
        check(tmp.renameTo(target)) { "rename failed" }
    }

    companion object {
        const val DB_NAME = "quran_warsh.db"
        /** Bump this whenever you ship a new quran_warsh.db so devices re-copy it. */
        const val ASSET_DB_VERSION = 4
        private const val KEY_VERSION = "asset_db_version"
    }
}
