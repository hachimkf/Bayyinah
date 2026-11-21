package com.bayyinah.app.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bayyinah.app.audio.RecitationPlayer
import com.bayyinah.app.book.BookLesson
import com.bayyinah.app.book.BookMatcher
import com.bayyinah.app.book.BookRefs
import com.bayyinah.app.data.Reciter
import com.bayyinah.app.data.Surah
import com.bayyinah.app.data.Verse
import com.bayyinah.app.data.VerseRepository
import com.bayyinah.app.match.ArabicNormalizer
import com.bayyinah.app.match.MatchDecider
import com.bayyinah.app.match.VerseMatcher
import com.bayyinah.app.ocr.ArabicTextRecognizer
import com.bayyinah.app.ocr.OcrAnalyzer
import com.bayyinah.app.ocr.TesseractArabicRecognizer
import com.bayyinah.app.ocr.TextImagePrep
import com.bayyinah.app.update.UpdateInfo
import com.bayyinah.app.update.UpdateManager
import android.graphics.Bitmap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** In-app update banner state (GitHub Releases). */
sealed interface UpdateUi {
    data object Hidden : UpdateUi
    data object Checking : UpdateUi
    data object UpToDate : UpdateUi
    data class Available(val info: UpdateInfo) : UpdateUi
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateUi
    data class Ready(val info: UpdateInfo, val apk: java.io.File) : UpdateUi
    data class Failed(val info: UpdateInfo) : UpdateUi
}

enum class Screen { Home, Scanner, Reader, Settings, History }
enum class ScanState { Loading, Idle, Scanning, NoMatch }

/** Player sheet state (+ which screen is shown). */
data class UiState(
    val screen: Screen = Screen.Home,
    val scanState: ScanState = ScanState.Loading,
    val torch: Boolean = false,
    /** Settings: check GitHub for updates automatically on launch. */
    val autoUpdate: Boolean = true,
    val verse: Verse? = null,
    /** False when the chosen reciter has no timing for this ayah. */
    val audioAvailable: Boolean = false,
    val isPlaying: Boolean = false,
    val buffering: Boolean = false,
    /** Set when streaming failed (usually no internet); cleared on retry / next ayah. */
    val audioError: Boolean = false,
    val reciters: List<Reciter> = emptyList(),
    val reciter: Reciter? = null,
    val repeat: Boolean = false,
    val slow: Boolean = false,
    val progress: Float = 0f,
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
    val setupError: String? = null,
)

/** Home: quick jump (surah + ayah number), search, surah list and surah detail. */
data class IndexState(
    val surahs: List<Surah> = emptyList(),
    val openSurah: Surah? = null,
    val surahVerses: List<Verse> = emptyList(),
    val query: String = "",
    val surahHits: List<Surah> = emptyList(),
    val verseHits: List<Verse> = emptyList(),
    val searching: Boolean = false,
    /** Incremented to ask the search field to clear itself (the field owns its text). */
    val clearSignal: Int = 0,
    /** Quick jump: the chosen surah and the ayah number being typed. */
    val jumpSurah: Surah? = null,
    val jumpAyah: String = "",
    val jumpError: Boolean = false,
    /** Last verse opened (the quick jump starts on its surah). */
    val lastVerse: Verse? = null,
    /** Heart-marked ayat, newest first. */
    val favorites: List<Verse> = emptyList(),
    /** Recently played ayat, newest first. */
    val recent: List<Verse> = emptyList(),
    /** Her book «أقرأ وأتعلم – المستوى الثاني»: its 24 Quran lessons. */
    val lessons: List<BookLesson> = emptyList(),
    /** Set when a book lesson is open: only its ayat are listed. */
    val openLesson: BookLesson? = null,
    val pageError: Boolean = false,
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app), RecitationPlayer.Listener {

    private val repository = VerseRepository(app)
    private val player = RecitationPlayer(app, this)
    private val prefs = app.getSharedPreferences("reader", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _index = MutableStateFlow(IndexState())
    val index: StateFlow<IndexState> = _index.asStateFlow()
    private val queryFlow = MutableStateFlow("")

    private val updates = UpdateManager(app)
    private val _update = MutableStateFlow<UpdateUi>(UpdateUi.Hidden)
    val update: StateFlow<UpdateUi> = _update.asStateFlow()
    private var updateJob: Job? = null

    private var progressJob: Job? = null

    // Scanning (loaded lazily the first time the scanner opens).
    val analyzer = OcrAnalyzer()
    /** Tesseract is not thread-safe and is heavy: one dedicated worker. */
    private val ocrDispatcher = Dispatchers.Default.limitedParallelism(1)
    private var recognizer: ArabicTextRecognizer? = null
    private var matcher: VerseMatcher? = null
    private var scanSetupJob: Job? = null
    private var book: BookRefs? = null
    private var bookMatcher: BookMatcher? = null
    private var scanJob: Job? = null

    init {
        viewModelScope.launch {
            try {
                val reciters = repository.reciters()
                val chosen = reciters.firstOrNull { it.id == prefs.getInt(KEY_RECITER, -1) } ?: reciters.firstOrNull()
                _state.update { it.copy(reciters = reciters, reciter = chosen) }
                val surahs = repository.surahs()
                val last = prefs.getInt(KEY_LAST_VERSE, 0).takeIf { it > 0 }?.let { repository.byId(it) }
                val jumpSurah = surahs.firstOrNull { it.number == (last?.surah ?: 1) } ?: surahs.firstOrNull()
                val favorites = loadIds(KEY_FAVORITES).mapNotNull { repository.byId(it) }
                val recent = loadIds(KEY_RECENT).mapNotNull { repository.byId(it) }
                _index.update { it.copy(surahs = surahs, jumpSurah = jumpSurah, lastVerse = last, favorites = favorites, recent = recent) }
            } catch (e: Exception) {
                android.util.Log.e("Bayyinah", "Setup failed", e)
                _state.update { it.copy(setupError = e.message ?: e.javaClass.simpleName) }
            }
        }
        viewModelScope.launch {
            try {
                val refs = withContext(Dispatchers.IO) { getApplication<Application>().assets.open("book_refs.bin").use { BookRefs.read(it) } }
                book = refs; bookMatcher = BookMatcher(refs)
                _index.update { it.copy(lessons = refs.lessons) }
            } catch (e: Exception) {
                android.util.Log.e("Bayyinah", "Book references not loaded", e)
            }
        }
        viewModelScope.launch { queryFlow.debounce(250).collect { runSearch(it) } }
        val auto = prefs.getBoolean(KEY_AUTO_UPDATE, true)
        _state.update { it.copy(autoUpdate = auto) }
        if (auto) checkForUpdates(force = false)
    }

    // ---------------------------------------------------------------- navigation

    /** Opens the camera and starts scanning automatically (no button to press). */
    fun openScanner() {
        player.pause()
        _state.update { it.copy(screen = Screen.Scanner, verse = null, isPlaying = false, buffering = false, progress = 0f, scanState = ScanState.Scanning) }
        prepareScanner()
        startAutoScan()
    }

    fun openSettings() = _state.update { it.copy(screen = Screen.Settings) }

    /** Favourites (heart) and recently played ayat. */
    fun openHistory() = _state.update { it.copy(screen = Screen.History) }

    fun toggleFavorite(verse: Verse) {
        _index.update { st ->
            val favs = if (st.favorites.any { it.id == verse.id }) st.favorites.filter { it.id != verse.id }
            else listOf(verse) + st.favorites
            saveIds(KEY_FAVORITES, favs.map { it.id })
            st.copy(favorites = favs)
        }
    }

    fun clearRecent() {
        saveIds(KEY_RECENT, emptyList())
        _index.update { it.copy(recent = emptyList()) }
    }

    private fun loadIds(key: String): List<Int> =
        prefs.getString(key, "").orEmpty().split(',').mapNotNull { it.toIntOrNull() }

    private fun saveIds(key: String, ids: List<Int>) = prefs.edit().putString(key, ids.joinToString(",")).apply()

    /** Back to home from scanner/settings; returns false when already home. */
    fun backToHome(): Boolean {
        if (_state.value.screen == Screen.Home) return false
        scanJob?.cancel(); analyzer.cancel()
        if (_state.value.screen == Screen.Reader) player.pause()
        _state.update { it.copy(screen = Screen.Home, torch = false) }
        return true
    }

    fun setAutoUpdate(on: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_UPDATE, on).apply()
        _state.update { it.copy(autoUpdate = on) }
    }

    fun toggleTorch() = _state.update { it.copy(torch = !it.torch) }

    // ---------------------------------------------------------------- scanning

    private fun prepareScanner() {
        if (scanSetupJob != null) return
        scanSetupJob = viewModelScope.launch {
            try {
                val entries = repository.loadMatchEntries()
                matcher = withContext(Dispatchers.Default) { VerseMatcher(entries.map { VerseMatcher.Entry(it.first, it.second) }) }
                withContext(ocrDispatcher) { recognizer = TesseractArabicRecognizer(getApplication()) }
                _state.update { if (it.scanState == ScanState.Loading) it.copy(scanState = ScanState.Idle) else it }
            } catch (e: Exception) {
                android.util.Log.e("Bayyinah", "Scanner setup failed", e)
                scanSetupJob = null
            }
        }
    }

    /**
     * Continuous scanning while the scanner is open: every frame goes through the (fast) book
     * matcher; the heavier OCR fallback runs only on every [OCR_EVERY]th frame to spare the battery.
     * On a match the camera closes and the ayah plays in the full-screen reader.
     */
    private fun startAutoScan() {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            var n = 0
            while (isActive && _state.value.screen == Screen.Scanner) {
                val frame = nextFrame()
                if (frame == null) { delay(300); continue }
                val allowOcr = n % OCR_EVERY == OCR_EVERY - 1
                val id = withContext(ocrDispatcher) { recognizeFrame(frame, allowOcr) }
                n++
                val verse = id?.let { repository.byId(it) }
                if (verse != null && _state.value.screen == Screen.Scanner) {
                    analyzer.cancel()
                    _state.update { it.copy(screen = Screen.Reader, torch = false) }   // camera unbinds
                    showVerse(verse, autoplay = true)
                    break
                }
                delay(SCAN_INTERVAL_MS)
            }
        }
    }

    /**
     * 1) Her book: compare the crop's printed lines with the book's own lines (fast, no OCR).
     * 2) Otherwise OCR the crop at each calibrated text size; stop early on a decisive pass.
     */
    private fun recognizeFrame(frame: Bitmap, allowOcr: Boolean = true): Int? {
        bookMatcher?.let { bm ->
            val w = frame.width; val h = frame.height
            val px = IntArray(w * h); frame.getPixels(px, 0, w, 0, 0, w, h)
            val lum = IntArray(w * h) { px[it] and 0xFF } // frame is already grayscale
            val t0 = System.currentTimeMillis()
            val r = bm.match(lum, w, h)
            if (com.bayyinah.app.BuildConfig.DEBUG) android.util.Log.d("BayyinahOcr", "book: $r in ${System.currentTimeMillis() - t0} ms")
            if (r != null && r.accepted) { frame.recycle(); return r.verseId }
        }
        val ocr = recognizer; val m = matcher
        if (!allowOcr || ocr == null || m == null) { frame.recycle(); return null }
        val near = _state.value.verse?.id
        val variants = try { TextImagePrep.prepare(frame) } finally { frame.recycle() }
        val passes = ArrayList<List<VerseMatcher.Candidate>>(variants.size)
        try {
            for (img in variants) {
                val text = ocr.recognize(img)
                val top = m.rank(text)
                if (com.bayyinah.app.BuildConfig.DEBUG) android.util.Log.d("BayyinahOcr", "pass ${img.width}x${img.height}: '$text' -> ${top.take(2)}")
                passes += top
                MatchDecider.decisive(top, near)?.let { return it }
            }
        } finally {
            variants.forEach { it.recycle() }
        }
        return MatchDecider.decide(passes, near)
    }

    /** Debug builds only: frames come from this image file instead of the camera. */
    private var debugFrameFile: String? = null

    fun debugScanFile(path: String) {
        if (!com.bayyinah.app.BuildConfig.DEBUG) return
        debugFrameFile = path
        openScanner()
    }

    private suspend fun nextFrame(): Bitmap? {
        debugFrameFile?.let { path ->
            return withContext(Dispatchers.IO) {
                val src = android.graphics.BitmapFactory.decodeFile(path) ?: return@withContext null
                Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888).also { gray ->
                    android.graphics.Canvas(gray).drawBitmap(src, 0f, 0f, android.graphics.Paint().apply {
                        colorFilter = android.graphics.ColorMatrixColorFilter(android.graphics.ColorMatrix().apply { setSaturation(0f) })
                    })
                    src.recycle()
                }
            }
        }
        val deferred = CompletableDeferred<Bitmap?>()
        analyzer.requestFrame { deferred.complete(it) }
        return withTimeoutOrNull(1500) { deferred.await() }.also { if (it == null) analyzer.cancel() }
    }

    /**
     * Debug builds only: run the on-device OCR pipeline on an image file instead of a camera frame
     * (`adb shell am start -n com.bayyinah.app/.MainActivity --es ocr_test <path in files/>`).
     */
    fun debugRecognizeFile(path: String) {
        if (!com.bayyinah.app.BuildConfig.DEBUG) return
        prepareScanner()
        viewModelScope.launch {
            while (recognizer == null || matcher == null) delay(200)
            val id = withContext(ocrDispatcher) {
                val src = android.graphics.BitmapFactory.decodeFile(path) ?: return@withContext null
                val gray = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
                android.graphics.Canvas(gray).drawBitmap(src, 0f, 0f, android.graphics.Paint().apply {
                    colorFilter = android.graphics.ColorMatrixColorFilter(android.graphics.ColorMatrix().apply { setSaturation(0f) })
                })
                src.recycle()
                recognizeFrame(gray).also { android.util.Log.d("BayyinahOcr", "RESULT $path -> $it") }
            }
            id?.let { repository.byId(it) }?.let { showVerse(it, autoplay = false) }
        }
    }

    // ---------------------------------------------------------------- quick jump

    fun setJumpSurah(surah: Surah) =
        _index.update { it.copy(jumpSurah = surah, jumpAyah = "", jumpError = false) }

    /** Keeps digits only (Arabic-Indic digits are converted), max 3. */
    fun setJumpAyah(text: String) {
        val digits = toLatinDigits(text).filter { it.isDigit() }.take(3)
        _index.update { it.copy(jumpAyah = digits, jumpError = false) }
    }

    /** Opens the player on surah:ayah; an empty ayah field means ayah 1. */
    fun goToJump() {
        val s = _index.value.jumpSurah ?: return
        val ayah = _index.value.jumpAyah.toIntOrNull() ?: 1
        if (ayah !in 1..s.ayahCount) { _index.update { it.copy(jumpError = true) }; return }
        viewModelScope.launch { repository.byRef(s.number, ayah)?.let { showVerse(it, autoplay = true) } }
    }

    // ---------------------------------------------------------------- her book

    /** Opens a lesson: the surah page filtered to the lesson's ayat. */
    fun openLesson(lesson: BookLesson) {
        val surah = _index.value.surahs.firstOrNull { it.number == lesson.surah } ?: return
        _index.update { it.copy(openSurah = surah, openLesson = lesson, surahVerses = emptyList(), jumpSurah = surah, pageError = false) }
        viewModelScope.launch {
            val verses = repository.versesOf(surah.number).filter { it.ayah in lesson.firstAyah..lesson.lastAyah }
            _index.update { if (it.openLesson == lesson) it.copy(surahVerses = verses) else it }
        }
    }

    /** Book page number → the lesson printed on (or just before) that page. */
    fun goToBookPage(text: String) {
        val page = toLatinDigits(text).filter { it.isDigit() }.toIntOrNull()
        val lessons = _index.value.lessons
        val lesson = page?.let { p -> lessons.lastOrNull { it.page <= p }?.takeIf { p - it.page <= 3 } }
        if (lesson == null) { _index.update { it.copy(pageError = true) }; return }
        openLesson(lesson)
    }

    fun clearPageError() = _index.update { it.copy(pageError = false) }

    // ---------------------------------------------------------------- index & search

    fun openSurah(surah: Surah) {
        _index.update { it.copy(openSurah = surah, openLesson = null, surahVerses = emptyList(), jumpSurah = surah) }
        viewModelScope.launch {
            val verses = repository.versesOf(surah.number)
            _index.update { if (it.openSurah?.number == surah.number) it.copy(surahVerses = verses) else it }
        }
    }

    /** Back: surah detail → home. Returns false when already on the home list. */
    fun back(): Boolean {
        if (_index.value.openSurah == null) return false
        _index.update { it.copy(openSurah = null, openLesson = null, surahVerses = emptyList()) }
        return true
    }

    fun clearQuery() {
        _index.update { it.copy(clearSignal = it.clearSignal + 1) }
        setQuery("")
    }

    fun setQuery(q: String) {
        if (q == _index.value.query) return
        _index.update { it.copy(query = q, searching = q.isNotBlank()) }
        queryFlow.value = q
    }

    private suspend fun runSearch(q: String) {
        val query = q.trim()
        if (query.isEmpty()) {
            _index.update { it.copy(surahHits = emptyList(), verseHits = emptyList(), searching = false) }
            return
        }
        // "18:10", "18 10" or "18" jump straight to a surah / ayah.
        val ref = Regex("^(\\d{1,3})(?:\\s*[:.\\- ]\\s*(\\d{1,3}))?$").find(toLatinDigits(query))
        val all = _index.value.surahs
        val surahHits: List<Surah>
        val verseHits: List<Verse>
        if (ref != null) {
            val s = ref.groupValues[1].toInt(); val a = ref.groupValues[2].toIntOrNull()
            surahHits = all.filter { it.number == s }
            verseHits = if (a != null) listOfNotNull(repository.byRef(s, a)) else emptyList()
        } else {
            val folded = foldLatin(query)
            val arabic = ArabicNormalizer.normalize(query)
            surahHits = all.filter {
                (arabic.isNotEmpty() && ArabicNormalizer.normalize(it.nameAr).contains(arabic)) ||
                    (folded.isNotEmpty() && foldLatin(it.nameEn).contains(folded))
            }
            verseHits = if (arabic.length >= 2) repository.searchVerses(query) else emptyList()
        }
        _index.update { if (it.query.trim() == query) it.copy(surahHits = surahHits, verseHits = verseHits, searching = false) else it }
    }

    /** Opens the player sheet for a verse chosen from a list, search or quick jump. */
    fun selectVerse(verse: Verse) {
        viewModelScope.launch { showVerse(verse, autoplay = true) }
    }

    // ---------------------------------------------------------------- app updates

    /** Background check (throttled) on start; [force] when she taps the logo. Silent when offline. */
    fun checkForUpdates(force: Boolean) {
        if (updateJob?.isActive == true) return
        updateJob = viewModelScope.launch {
            if (force) _update.value = UpdateUi.Checking
            val info = updates.check(force)
            _update.value = when {
                info != null -> UpdateUi.Available(info)
                force -> UpdateUi.UpToDate
                else -> UpdateUi.Hidden
            }
            if (info == null && force) { delay(2500); if (_update.value == UpdateUi.UpToDate) _update.value = UpdateUi.Hidden }
        }
    }

    fun downloadUpdate() {
        val info = when (val u = _update.value) { is UpdateUi.Available -> u.info; is UpdateUi.Failed -> u.info; else -> return }
        updateJob = viewModelScope.launch {
            _update.value = UpdateUi.Downloading(info, 0f)
            _update.value = try {
                val apk = updates.download(info) { p -> _update.value = UpdateUi.Downloading(info, p) }
                UpdateUi.Ready(info, apk)
            } catch (e: Exception) {
                android.util.Log.w("BayyinahUpdate", "Download failed", e)
                UpdateUi.Failed(info)
            }
        }
    }

    /** Opens the system installer, or first the "allow from this source" setting if needed. */
    fun installUpdate(activity: android.app.Activity) {
        val ready = _update.value as? UpdateUi.Ready ?: return
        if (!updates.canInstall()) updates.openInstallPermissionSettings(activity)
        else updates.install(activity, ready.apk)
    }

    fun dismissUpdate() { updateJob?.cancel(); _update.value = UpdateUi.Hidden }

    // ---------------------------------------------------------------- verse & audio

    fun selectReciter(reciter: Reciter) {
        prefs.edit().putInt(KEY_RECITER, reciter.id).apply()
        _state.update { it.copy(reciter = reciter) }
        // Reload the open ayah with the new voice, keeping play/pause as it was.
        _state.value.verse?.let { v -> viewModelScope.launch { showVerse(v, autoplay = true) } }
    }

    fun retryAudio() {
        _state.update { it.copy(audioError = false) }
        player.retry()
    }

    private suspend fun showVerse(verse: Verse, autoplay: Boolean) {
        val clip = _state.value.reciter?.let { repository.clip(it, verse) }
        val available = clip != null
        player.load(clip, playWhenReady = autoplay)
        val hasPrev = repository.previous(verse) != null
        val hasNext = repository.next(verse) != null
        prefs.edit().putInt(KEY_LAST_VERSE, verse.id).apply()
        _state.update {
            it.copy(
                verse = verse, audioAvailable = available, progress = 0f, audioError = false,
                isPlaying = false, hasPrevious = hasPrev, hasNext = hasNext,
            )
        }
        _index.update { st ->
            val recent = (listOf(verse) + st.recent.filter { it.id != verse.id }).take(MAX_RECENT)
            saveIds(KEY_RECENT, recent.map { it.id })
            st.copy(lastVerse = verse, recent = recent)
        }
    }

    fun togglePlay() = player.togglePlay()

    fun toggleRepeat() {
        val loop = !_state.value.repeat
        player.setLooping(loop)
        _state.update { it.copy(repeat = loop) }
    }

    fun setSlow(slow: Boolean) {
        player.setSpeed(if (slow) 0.75f else 1.0f)
        _state.update { it.copy(slow = slow) }
    }

    fun next() = step { repository.next(it) }
    fun previous() = step { repository.previous(it) }

    private fun step(find: suspend (Verse) -> Verse?) {
        val current = _state.value.verse ?: return
        viewModelScope.launch {
            val target = find(current) ?: return@launch
            // Stepping always plays the new ayah; repeat mode now loops it.
            showVerse(target, autoplay = true)
        }
    }

    /** Closes the player; from the full-screen reader this goes back to scanning. */
    fun dismissVerse() {
        if (_state.value.screen == Screen.Reader) { openScanner(); return }
        player.pause()
        _state.update { it.copy(verse = null, isPlaying = false, buffering = false, progress = 0f) }
    }

    fun onAppBackgrounded() {
        player.pause()
        _state.update { it.copy(torch = false) }
    }

    // ---------------------------------------------------------------- player callbacks

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        _state.update { it.copy(isPlaying = isPlaying) }
        progressJob?.cancel()
        if (isPlaying) progressJob = viewModelScope.launch {
            while (isActive) {
                _state.update { it.copy(progress = player.progress()) }
                delay(100)
            }
        }
    }

    override fun onBufferingChanged(buffering: Boolean) = _state.update { it.copy(buffering = buffering) }

    override fun onEnded() = _state.update { it.copy(progress = 1f) }

    override fun onError(network: Boolean) =
        _state.update { it.copy(isPlaying = false, buffering = false, audioError = true) }

    override fun onCleared() {
        scanJob?.cancel()
        analyzer.cancel()
        player.release()
        repository.close()
        // Release Tesseract on its own thread so we never race an in-flight recognize().
        val r = recognizer
        recognizer = null
        if (r != null) CoroutineScope(ocrDispatcher).launch { r.close() }
    }

    private companion object {
        const val KEY_LAST_VERSE = "last_verse"
        const val KEY_RECITER = "reciter"
        const val KEY_AUTO_UPDATE = "auto_update"
        const val KEY_FAVORITES = "favorites"
        const val KEY_RECENT = "recent"
        const val MAX_RECENT = 30
        const val SCAN_INTERVAL_MS = 450L
        /** OCR fallback on every 4th frame only (book matching runs on every frame). */
        const val OCR_EVERY = 4

        fun toLatinDigits(s: String) = buildString {
            for (c in s) append(
                when (c) {
                    in '٠'..'٩' -> '0' + (c - '٠')   // Arabic-Indic ٠-٩
                    in '۰'..'۹' -> '0' + (c - '۰')   // Extended (Persian) ۰-۹
                    else -> c
                }
            )
        }

        /** "Āl-‘Imrān" → "alimran" so "imran", "al imran" or "kahf" all match. */
        fun foldLatin(s: String): String =
            java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD)
                .filter { it in 'a'..'z' }
    }
}
