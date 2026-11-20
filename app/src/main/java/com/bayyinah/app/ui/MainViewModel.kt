package com.bayyinah.app.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bayyinah.app.audio.RecitationPlayer
import com.bayyinah.app.data.Reciter
import com.bayyinah.app.data.Surah
import com.bayyinah.app.data.Verse
import com.bayyinah.app.data.VerseRepository
import com.bayyinah.app.match.ArabicNormalizer
import com.bayyinah.app.update.UpdateInfo
import com.bayyinah.app.update.UpdateManager
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

/** Player sheet state. */
data class UiState(
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
    /** Last verse opened, offered as "continue" on the home screen. */
    val lastVerse: Verse? = null,
)

@OptIn(FlowPreview::class)
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

    init {
        viewModelScope.launch {
            try {
                val reciters = repository.reciters()
                val chosen = reciters.firstOrNull { it.id == prefs.getInt(KEY_RECITER, -1) } ?: reciters.firstOrNull()
                _state.update { it.copy(reciters = reciters, reciter = chosen) }
                val surahs = repository.surahs()
                val last = prefs.getInt(KEY_LAST_VERSE, 0).takeIf { it > 0 }?.let { repository.byId(it) }
                val jumpSurah = surahs.firstOrNull { it.number == (last?.surah ?: 1) } ?: surahs.firstOrNull()
                _index.update { it.copy(surahs = surahs, jumpSurah = jumpSurah, lastVerse = last) }
            } catch (e: Exception) {
                android.util.Log.e("Bayyinah", "Setup failed", e)
                _state.update { it.copy(setupError = e.message ?: e.javaClass.simpleName) }
            }
        }
        viewModelScope.launch { queryFlow.debounce(250).collect { runSearch(it) } }
        checkForUpdates(force = false)
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

    // ---------------------------------------------------------------- index & search

    fun openSurah(surah: Surah) {
        _index.update { it.copy(openSurah = surah, surahVerses = emptyList(), jumpSurah = surah) }
        viewModelScope.launch {
            val verses = repository.versesOf(surah.number)
            _index.update { if (it.openSurah?.number == surah.number) it.copy(surahVerses = verses) else it }
        }
    }

    /** Back: surah detail → home. Returns false when already on the home list. */
    fun back(): Boolean {
        if (_index.value.openSurah == null) return false
        _index.update { it.copy(openSurah = null, surahVerses = emptyList()) }
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
        _index.update { it.copy(lastVerse = verse) }
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

    fun dismissVerse() {
        player.pause()
        _state.update { it.copy(verse = null, isPlaying = false, buffering = false, progress = 0f) }
    }

    fun onAppBackgrounded() = player.pause()

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
        player.release()
        repository.close()
    }

    private companion object {
        const val KEY_LAST_VERSE = "last_verse"
        const val KEY_RECITER = "reciter"

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
