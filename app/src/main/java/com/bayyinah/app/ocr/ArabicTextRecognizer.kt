package com.bayyinah.app.ocr

import android.content.Context
import android.graphics.Bitmap
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File

/** Offline OCR engine abstraction, so the engine can be swapped without touching the UI. */
interface ArabicTextRecognizer {
    /** Blocking; call from a background thread. Returns raw recognized text (may be empty). */
    fun recognize(bitmap: Bitmap): String
    fun close()
}

/**
 * Tesseract 5 (LSTM) with `ara.traineddata` bundled in `assets/tessdata/`, copied to app storage
 * on first use. Fully on-device; no network.
 *
 * Not thread-safe: the caller serializes access (see MainViewModel's single-thread dispatcher).
 */
class TesseractArabicRecognizer(context: Context) : ArabicTextRecognizer {

    private val api: TessBaseAPI

    init {
        val app = context.applicationContext
        val root = File(app.filesDir, "ocr")
        val tessdata = File(root, "tessdata").apply { mkdirs() }
        val model = File(tessdata, MODEL)
        if (!model.exists() || model.length() == 0L) {
            val tmp = File(tessdata, "$MODEL.tmp")
            app.assets.open("tessdata/$MODEL").use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
            tmp.renameTo(model)
        }
        api = TessBaseAPI()
        check(api.init(root.absolutePath, "ara", TessBaseAPI.OEM_LSTM_ONLY)) { "Tesseract init failed" }
        // A framed verse is usually 1–3 lines of uniform text.
        api.pageSegMode = TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK
        // Quran print is dense with marks; don't let the dictionary "correct" words.
        api.setVariable("load_system_dawg", "0")
        api.setVariable("load_freq_dawg", "0")
    }

    override fun recognize(bitmap: Bitmap): String {
        api.setImage(bitmap)
        return try { api.utF8Text.orEmpty() } finally { api.clear() }
    }

    override fun close() = api.recycle()

    private companion object { const val MODEL = "ara.traineddata" }
}
