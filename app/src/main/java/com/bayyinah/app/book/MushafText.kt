package com.bayyinah.app.book

import android.content.Context
import android.net.Uri
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import java.io.File
import java.text.Normalizer
import java.util.zip.ZipInputStream

/**
 * The Mushaf Mohammadi text in its own Moroccan font (AALMAGHRIBI), from a private pack she imports
 * (book-source/mushaf-text/…; never bundled: the Foundation's font and text have no redistribution licence).
 *
 * The words use the font's own encoding (e.g. ۱للَّهِ): display only — search and scanning keep
 * using the normal Warsh text in the app's database.
 */
class MushafText(val font: FontFamily, private val ayat: Map<Int, String>, private val dir: File) {

    /** The ayah's words for display (basmala left out), or null if missing. Key = surah*1000+ayah. */
    fun ayah(verseId: Int): String? = ayat[verseId]

    /** One printed line: its items in reading order. */
    class Item(val kind: Char, val surah: Int, val ayah: Int, val text: String)

    /** The lines of a printed page (2..639), read on demand. */
    fun page(page: Int): List<List<Item>> {
        val f = File(dir, "pages.tsv")
        val out = ArrayList<List<Item>>(15)
        f.bufferedReader().useLines { seq ->
            for (row in seq) {
                val tab1 = row.indexOf('\t'); if (tab1 < 0) continue
                if (row.substring(0, tab1).toIntOrNull() != page) { if (out.isNotEmpty()) break else continue }
                val items = row.substring(row.indexOf('\t', tab1 + 1) + 1).split('‖').mapNotNull { parseItem(it) }
                out += items
            }
        }
        return out
    }

    /** The printed page where an ayah's marker sits (where the ayah ends). */
    fun pageOf(verseId: Int): Int? = markerPage[verseId]

    private val markerPage: Map<Int, Int> by lazy {
        val m = HashMap<Int, Int>(6300)
        File(dir, "pages.tsv").bufferedReader().useLines { seq ->
            for (row in seq) {
                val p = row.substringBefore('\t').toIntOrNull() ?: continue
                for (it in row.substringAfterLast('\t').split('‖')) {
                    if (it.startsWith("m:")) parseItem(it)?.let { i -> m[i.surah * 1000 + i.ayah] = p }
                }
            }
        }
        m
    }

    companion object {
        const val FIRST_PAGE = 2
        const val LAST_PAGE = 639
        private const val DIR = "mushaf_text"

        private fun parseItem(s: String): Item? {
            val p = s.split(':', limit = 4); if (p.size < 4) return null
            return Item(p[0][0], p[1].toIntOrNull() ?: 0, p[2].toIntOrNull() ?: 0, nfc(p[3]))
        }

        /** The official reader shapes NFC text; the raw data stays untouched in the pack. */
        private fun nfc(s: String) = Normalizer.normalize(s, Normalizer.Form.NFC)

        fun isInstalled(context: Context) = File(File(context.filesDir, DIR), "ayat.tsv").isFile

        fun load(context: Context): MushafText? {
            val dir = File(context.filesDir, DIR)
            val fontFile = File(dir, "font.otf"); val ayatFile = File(dir, "ayat.tsv")
            if (!fontFile.isFile || !ayatFile.isFile) return null
            return try {
                val ayat = HashMap<Int, String>(6300)
                ayatFile.bufferedReader().useLines { seq ->
                    for (row in seq) {
                        val p = row.split('\t'); if (p.size < 3) continue
                        ayat[p[0].toInt() * 1000 + p[1].toInt()] = nfc(p[2])
                    }
                }
                MushafText(FontFamily(Font(fontFile)), ayat, dir)
            } catch (e: Exception) {
                android.util.Log.e("Bayyinah", "Mushaf text pack unreadable", e); null
            }
        }

        /** True when the picked file is a zip (the text pack); the scan pack starts with "BYMP". */
        fun looksLikeTextPack(context: Context, uri: Uri): Boolean =
            context.contentResolver.openInputStream(uri)?.use { val b = ByteArray(2); it.read(b) == 2 && b[0] == 'P'.code.toByte() && b[1] == 'K'.code.toByte() } ?: false

        /** Unpacks a text pack into private storage (validated, then swapped in). */
        fun import(context: Context, uri: Uri): MushafText {
            val tmp = File(context.filesDir, "$DIR.tmp").apply { deleteRecursively(); mkdirs() }
            val allowed = setOf("font.otf", "ayat.tsv", "pages.tsv", "manifest.json")
            context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input.buffered()).use { z ->
                    while (true) {
                        val e = z.nextEntry ?: break
                        if (e.isDirectory || e.name !in allowed) continue
                        File(tmp, e.name).outputStream().use { z.copyTo(it) }
                    }
                }
            } ?: throw IllegalArgumentException("Can't open the file")
            val manifest = File(tmp, "manifest.json").takeIf { it.isFile }?.readText().orEmpty()
            require("bayyinah-mushaf-text" in manifest) { "Not a Mushaf text pack" }
            require(File(tmp, "font.otf").isFile && File(tmp, "ayat.tsv").isFile) { "Incomplete pack" }
            val dest = File(context.filesDir, DIR)
            dest.deleteRecursively()
            if (!tmp.renameTo(dest)) { tmp.copyRecursively(dest, overwrite = true); tmp.deleteRecursively() }
            return load(context) ?: throw IllegalArgumentException("Pack unreadable")
        }

        fun remove(context: Context) { File(context.filesDir, DIR).deleteRecursively() }
    }
}
