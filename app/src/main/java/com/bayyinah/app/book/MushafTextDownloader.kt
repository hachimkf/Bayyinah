package com.bayyinah.app.book

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fetches the Mushaf Mohammadi font and page text once, from the Foundation's own public servers,
 * and stores them in the app's private files (the same files a text pack gives). Nothing of theirs
 * is in the APK or the repo. Resumable: pages already fetched are kept until it completes.
 *
 * Same conversion as tools/mushaf/build_text_pack.py:
 *  - page rows: wordId ‖ ayaId ‖ suraNum ‖ classes ‖ display text (kept byte for byte)
 *  - ayaId is the ayah's position in the Warsh order (1..6214) → (surah, ayah) from the surah sizes
 *  - ayat.tsv: the words of each ayah, basmala left out; pages.tsv: the printed lines.
 */
object MushafTextDownloader {
    private const val FONT_URL = "https://app.al-mushaf.com/_content/quranRCL/ressources/fonts/AALMAGHRIBI_v4.9.6.otf"
    private const val PAGE_URL = "https://quranapi.al-mushaf.com/api/Quran/QuranPageWordsContentByPageNum/pagenum=%d&mushafid=1&version=5.2.22"
    private const val SEP = '‖'
    private const val WORK = "mushaf_text_dl"

    /** [ayahCounts]: ayat per surah, 114 entries, Warsh count (from the app's database). Returns true when installed. */
    suspend fun run(context: Context, ayahCounts: List<Int>): Boolean = withContext(Dispatchers.IO) {
        if (MushafText.isInstalled(context)) return@withContext true
        require(ayahCounts.size == 114 && ayahCounts.sum() == 6214)
        val work = File(context.filesDir, WORK).apply { mkdirs() }
        val pagesDir = File(work, "pages").apply { mkdirs() }

        val font = File(work, "font.otf")
        if (!font.isFile || font.length() < 100_000) {
            val bytes = get(FONT_URL)
            require(bytes.size > 100_000) { "font too small" }
            File(work, "font.tmp").apply { writeBytes(bytes); renameTo(font) }
        }

        val gate = Semaphore(3)
        coroutineScope {
            (MushafText.FIRST_PAGE..MushafText.LAST_PAGE).map { p ->
                async {
                    val f = File(pagesDir, "%03d.txt".format(p))
                    if (f.isFile && f.length() > 0) return@async
                    gate.withPermit {
                        val text = get(PAGE_URL.format(p)).toString(Charsets.UTF_8)
                        require(SEP in text && !text.trimStart().startsWith("{") && !text.trimStart().startsWith("<")) { "page $p: unexpected reply" }
                        text.lineSequence().filter { it.isNotBlank() }.forEach { require(it.split(SEP).size == 5) { "page $p: bad row" } }
                        File(pagesDir, "%03d.tmp".format(p)).apply { writeText(text) ; renameTo(f) }
                    }
                }
            }.awaitAll()
        }

        // ayaId (1..6214) → surah*1000+ayah
        val byId = IntArray(6215)
        var id = 1
        ayahCounts.forEachIndexed { i, n -> for (a in 1..n) byId[id++] = (i + 1) * 1000 + a }

        val ayat = sortedMapOf<Int, MutableList<String>>()
        val lines = StringBuilder()
        for (p in MushafText.FIRST_PAGE..MushafText.LAST_PAGE) {
            var line = 1
            val items = ArrayList<String>()
            fun flush() { if (items.isNotEmpty()) { lines.append(p).append('\t').append(line).append('\t').append(items.joinToString(SEP.toString())).append('\n'); line++; items.clear() } }
            for (row in File(pagesDir, "%03d.txt".format(p)).readText().split('\n')) {
                if (row.isBlank()) continue
                val f = row.split(SEP)
                val (wid, aid, sura, cls) = f; val text = f[4]
                val classes = cls.split(' ').filter { it.isNotEmpty() }
                if ("classLineEnd" in classes) { flush(); continue }
                if (wid.isEmpty() || text.isBlank()) continue
                if ("suraTitle" in classes) { items += "t:${sura.ifEmpty { "0" }}:0:${text.trim()}"; continue }
                val n = aid.toIntOrNull() ?: continue
                val key = byId[n]; val s = key / 1000; val a = key % 1000
                when {
                    "tomon" in classes -> items += "o:$s:$a:${text.trim()}"
                    "aya" in classes -> items += "m:$s:$a:${text.trim()}"
                    else -> { items += "w:$s:$a:$text"; ayat.getOrPut(key) { ArrayList() } += text }
                }
            }
            flush()
        }
        // Basmala is not an ayah in Warsh: drop it from the start of ayah 1.
        for ((k, words) in ayat) if (k % 1000 == 1 && words.size > 4 && words[0].startsWith("بِسْمِ")) repeat(4) { words.removeAt(0) }
        require(ayat.size == 6214) { "expected 6214 ayat, got ${ayat.size}" }

        val out = File(context.filesDir, "mushaf_text.new").apply { deleteRecursively(); mkdirs() }
        font.copyTo(File(out, "font.otf"), overwrite = true)
        File(out, "ayat.tsv").writeText(buildString { for ((k, w) in ayat) append(k / 1000).append('\t').append(k % 1000).append('\t').append(w.joinToString(" ")).append('\n') })
        File(out, "pages.tsv").writeText(lines.toString())
        File(out, "manifest.json").writeText("""{"kind": "bayyinah-mushaf-text", "version": 1, "ayat": ${ayat.size}, "source": "download"}""")
        val dest = File(context.filesDir, "mushaf_text")
        dest.deleteRecursively()
        check(out.renameTo(dest)) { "could not install" }
        work.deleteRecursively()
        true
    }

    private fun get(url: String): ByteArray {
        var last: Exception? = null
        repeat(3) { attempt ->
            try {
                val c = URL(url).openConnection() as HttpURLConnection
                c.connectTimeout = 15_000; c.readTimeout = 30_000
                c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) Bayyinah")
                try {
                    if (c.responseCode != 200) throw java.io.IOException("HTTP ${c.responseCode}")
                    return c.inputStream.use { it.readBytes() }
                } finally { c.disconnect() }
            } catch (e: Exception) { last = e; Thread.sleep(1000L * (attempt + 1)) }
        }
        throw last ?: java.io.IOException("download failed")
    }
}
