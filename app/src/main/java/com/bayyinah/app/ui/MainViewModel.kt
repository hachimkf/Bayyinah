package com.bayyinah.app.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bayyinah.app.audio.RecitationPlayer
import com.bayyinah.app.data.Surah
import com.bayyinah.app.data.Verse
import com.bayyinah.app.data.VerseRepository
import com.bayyinah.app.match.MatchDecider
import com.bayyinah.app.match.VerseMatcher
import com.bayyinah.app.ocr.ArabicTextRecognizer
import com.bayyinah.app.ocr.OcrAnalyzer
import com.bayyinah.app.ocr.TesseractArabicRecognizer
import com.bayyinah.app.ocr.TextImagePrep
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

enum class ScanState { Loading, Idle, Scanning, NoMatch }

/** In-app update banner state (GitHub Releases). */
sealed interface UpdateUi {
    data object Hidden : UpdateUi
    data object Checking : UpdateUi
    data object UpToDate : UpdateUi
    data class Available(val info: com.bayyinah.app.update.UpdateInfo) : UpdateUi
    data class Downloading(val info: com.bayyinah.app.update.UpdateInfo, val progress: Float) : UpdateUi
    data class Ready(val info: com.bayyinah.app.update.UpdateInfo, val apk: java.io.File) : UpdateUi
    data class Failed(val info: com.bayyinah.app.update.UpdateInfo) : UpdateUi
}
enum class Screen { Camera, Index }

data class UiState(
    val screen: Screen = Screen.Camera,
    val scanState: ScanState = ScanState.Loading,
    val verse: Verse? = null,
    val audioAvailable: Boolean = false,
    val isPlaying: Boolean = false,
    val repeat: Boolean = false,
    val slow: Boolean = false,
    val progress: Float = 0f,
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
    val torch: Boolean = false,
    val setupError: String? = null,
)

/** Index / search screen state. */
data class IndexState(
    val surahs: List<Surah> = emptyList(),
    val openSurah: Surah? = null,
    val surahVerses: List<Verse> = emptyList(),
    val query: String = "",
    val surahHits: List<Surah> = emptyList(),
    val verseHits: List<Verse> = emptyList(),
    val searching: Boolean = false,
    /** True when opened from the Search button: focus the field and show the keyboard. */
    val focusSearch: Boolean = false,
    /** Incremented to ask the search field to clear itself (the field owns its text). */
    val clearSignal: Int = 0,
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class MainViewModel(app: Application) : AndroidViewModel(app), RecitationPlayer.Listener {

    private val repository = VerseRepository(app)
    private val player = RecitationPlayer(app, this)
    val analyzer = OcrAnalyzer()

    /** Tesseract is not thread-safe and is heavy: one dedicated worker. */
    private val ocrDispatcher = Dispatchers.Default.limitedParallelism(1)
    private var recognizer: ArabicTextRecognizer? = null
    private var matcher: VerseMatcher? = null

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _index = MutableStateFlow(IndexState())
    val index: StateFlow<IndexState> = _index.asStateFlow()
    private val queryFlow = MutableStateFlow("")

    private val updates = com.bayyinah.app.update.UpdateManager(app)
    private val _update = MutableStateFlow<UpdateUi>(UpdateUi.Hidden)
    val update: StateFlow<UpdateUi> = _update.asStateFlow()
    private var updateJob: Job? = null

    private var scanJob: Job? = null
    private var progressJob: Job? = null

    init {
        viewModelScope.launch {
            try {
                _index.update { it.copy(surahs = repository.surahs()) }
                val entries = repository.loadMatchEntries()
                matcher = withContext(Dispatchers.Default) {
                    VerseMatcher(entries.map { VerseMatcher.Entry(it.first, it.second) })
                }
                // Warm up OCR off the main thread so the first scan is quick.
                withContext(ocrDispatcher) { recognizer = TesseractArabicRecognizer(getApplication()) }
                _state.update { it.copy(scanState = ScanState.Idle) }
            } catch (e: Exception) {
                android.util.Log.e("Bayyinah", "Setup failed", e)
                _state.update { it.copy(setupError = e.message ?: e.javaClass.simpleName) }
            }
        }
        viewModelScope.launch {
            queryFlow.debounce(250).collect { runSearch(it) }
        }
        checkForUpdates(force = false)
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

    // ---------------------------------------------------------------- navigation

    fun openIndex(search: Boolean) {
        scanJob?.cancel(); analyzer.cancel()
        _index.update { it.copy(focusSearch = search, openSurah = if (search) null else it.openSurah) }
        _state.update { it.copy(screen = Screen.Index, torch = false, scanState = idleIfReady(it)) }
    }

    fun closeIndex() {
        _index.update { it.copy(openSurah = null, surahVerses = emptyList()) }
        _state.update { it.copy(screen = Screen.Camera) }
    }

    /** Back inside the index: surah detail → surah list → camera. */
    fun indexBack() {
        if (_index.value.openSurah != null) _index.update { it.copy(openSurah = null, surahVerses = emptyList()) }
        else closeIndex()
    }

    private fun idleIfReady(s: UiState) =
        if (s.scanState == ScanState.Loading) ScanState.Loading else ScanState.Idle

    fun toggleTorch() = _state.update { it.copy(torch = !it.torch) }

    // ---------------------------------------------------------------- index & search

    fun openSurah(surah: Surah) {
        _index.update { it.copy(openSurah = surah, surahVerses = emptyList()) }
        viewModelScope.launch {
            val verses = repository.versesOf(surah.number)
            _index.update { if (it.openSurah?.number == surah.number) it.copy(surahVerses = verses) else it }
        }
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
            val arabic = com.bayyinah.app.match.ArabicNormalizer.normalize(query)
            surahHits = all.filter {
                (arabic.isNotEmpty() && com.bayyinah.app.match.ArabicNormalizer.normalize(it.nameAr).contains(arabic)) ||
                    (folded.isNotEmpty() && foldLatin(it.nameEn).contains(folded))
            }
            verseHits = if (arabic.length >= 2) repository.searchVerses(query) else emptyList()
        }
        _index.update { if (it.query.trim() == query) it.copy(surahHits = surahHits, verseHits = verseHits, searching = false) else it }
    }

    /** Opens the player sheet for a verse chosen manually (index or search). */
    fun selectVerse(verse: Verse) {
        viewModelScope.launch { showVerse(verse, autoplay = true) }
    }

    // ---------------------------------------------------------------- scanning

    fun scan() {
        if (_state.value.scanState == ScanState.Loading || scanJob?.isActive == true) return
        _state.update { it.copy(scanState = ScanState.Scanning) }
        scanJob = viewModelScope.launch {
            var found: Verse? = null
            // Several successive frames: the first may be blurry while autofocus settles.
            for (attempt in 0 until MAX_ATTEMPTS) {
                if (!isActive) break
                if (attempt > 0) delay(200)
                val frame = nextFrame() ?: continue
                val id = withContext(ocrDispatcher) { recognizeFrame(frame) }
                if (id != null) { found = repository.byId(id); break }
            }
            val verse = found
            if (verse != null) {
                showVerse(verse, autoplay = true)
                _state.update { it.copy(scanState = ScanState.Idle) }
            } else {
                _state.update { it.copy(scanState = ScanState.NoMatch) }
                delay(3000)
                _state.update { if (it.scanState == ScanState.NoMatch) it.copy(scanState = ScanState.Idle) else it }
            }
        }
    }

    /** OCR the crop at each calibrated text size; stop early on a decisive pass. */
    private fun recognizeFrame(frame: Bitmap): Int? {
        val ocr = recognizer ?: return null
        val m = matcher ?: return null
        val variants = try { TextImagePrep.prepare(frame) } finally { frame.recycle() }
        val passes = ArrayList<List<VerseMatcher.Candidate>>(variants.size)
        try {
            for (img in variants) {
                val text = ocr.recognize(img)
                val top = m.rank(text)
                if (com.bayyinah.app.BuildConfig.DEBUG) {
                    android.util.Log.d("BayyinahOcr", "pass ${img.width}x${img.height}: '$text' -> ${top.take(2)}")
                }
                passes += top
                MatchDecider.decisive(top, near = _state.value.verse?.id)?.let { return it }
            }
        } finally {
            variants.forEach { it.recycle() }
        }
        return MatchDecider.decide(passes, near = _state.value.verse?.id)
    }

    /**
     * Debug builds only: run the exact on-device OCR pipeline on an image file instead of a camera
     * frame (`adb shell am start -n com.bayyinah.app/.MainActivity --es ocr_test <path in files/>`).
     */
    fun debugRecognizeFile(path: String) {
        if (!com.bayyinah.app.BuildConfig.DEBUG) return
        viewModelScope.launch {
            while (recognizer == null || matcher == null) delay(200)
            val id = withContext(ocrDispatcher) {
                val src = android.graphics.BitmapFactory.decodeFile(path) ?: return@withContext null
                val gray = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
                android.graphics.Canvas(gray).drawBitmap(src, 0f, 0f, android.graphics.Paint().apply {
                    colorFilter = android.graphics.ColorMatrixColorFilter(android.graphics.ColorMatrix().apply { setSaturation(0f) })
                })
                src.recycle()
                val t0 = System.currentTimeMillis()
                recognizeFrame(gray).also {
                    android.util.Log.d("BayyinahOcr", "RESULT $path -> $it in ${System.currentTimeMillis() - t0} ms")
                }
            }
            id?.let { repository.byId(it) }?.let { showVerse(it, autoplay = false) }
        }
    }

    private suspend fun nextFrame(): Bitmap? {
        val deferred = CompletableDeferred<Bitmap?>()
        analyzer.requestFrame { deferred.complete(it) }
        return withTimeoutOrNull(1500) { deferred.await() }.also { if (it == null) analyzer.cancel() }
    }

    // ---------------------------------------------------------------- verse & audio

    private suspend fun showVerse(verse: Verse, autoplay: Boolean) {
        val available = player.load(verse.audioFile, playWhenReady = autoplay)
        val hasPrev = repository.previous(verse) != null
        val hasNext = repository.next(verse) != null
        _state.update {
            it.copy(
                verse = verse, audioAvailable = available, progress = 0f,
                isPlaying = false, hasPrevious = hasPrev, hasNext = hasNext,
            )
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
            // Keep playing if she was listening; repeat mode now loops the new verse.
            showVerse(target, autoplay = _state.value.isPlaying)
        }
    }

    fun dismissVerse() {
        player.pause()
        _state.update { it.copy(verse = null, isPlaying = false, progress = 0f) }
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

    override fun onEnded() = _state.update { it.copy(progress = 1f) }

    override fun onError(message: String) =
        _state.update { it.copy(isPlaying = false, audioAvailable = false) }

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
        const val MAX_ATTEMPTS = 3

        fun toLatinDigits(s: String) = buildString {
            for (c in s) append(if (c in '٠'..'٩') '0' + (c - '٠') else c)
        }

        /** "Āl-‘Imrān" → "alimran" so "imran", "al imran" or "kahf" all match. */
        fun foldLatin(s: String): String =
            java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD)
                .filter { it in 'a'..'z' }
    }
}
