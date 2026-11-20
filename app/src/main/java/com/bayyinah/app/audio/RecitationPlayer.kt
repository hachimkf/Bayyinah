package com.bayyinah.app.audio

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import java.io.File

/**
 * Thin wrapper over Media3 ExoPlayer for single-verse recitation files.
 *
 * Lookup order for `audio_file` (e.g. `020114.opus`):
 *  1. `filesDir/audio/<file>`  – side-loaded audio packs (keeps the APK small)
 *  2. `assets/audio/<file>`    – audio bundled in the APK
 * Missing files are reported via [isAvailable] so the UI can show a placeholder state.
 */
class RecitationPlayer(context: Context, private val listener: Listener) {

    interface Listener {
        fun onIsPlayingChanged(isPlaying: Boolean)
        fun onEnded()
        fun onError(message: String)
    }

    private val app = context.applicationContext
    private val bundledAudio: Set<String> = app.assets.list("audio")?.toSet().orEmpty()

    val player: ExoPlayer = ExoPlayer.Builder(app)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                .build(),
            /* handleAudioFocus = */ true,
        )
        .setHandleAudioBecomingNoisy(true) // pause when headphones are unplugged
        .build()
        .apply {
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) = listener.onIsPlayingChanged(isPlaying)
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_ENDED) listener.onEnded()
                }
                override fun onPlayerError(error: PlaybackException) =
                    listener.onError(error.errorCodeName)
            })
        }

    fun resolve(audioFile: String): Uri? {
        if (audioFile.isBlank()) return null
        val local = File(File(app.filesDir, "audio"), audioFile)
        if (local.isFile && local.length() > 0) return Uri.fromFile(local)
        if (audioFile in bundledAudio) return Uri.parse("asset:///audio/$audioFile")
        return null
    }

    fun isAvailable(audioFile: String): Boolean = resolve(audioFile) != null

    /** Loads the verse; returns false (and clears the player) if its audio is missing. */
    fun load(audioFile: String, playWhenReady: Boolean): Boolean {
        val uri = resolve(audioFile)
        if (uri == null) { player.stop(); player.clearMediaItems(); return false }
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
        player.playWhenReady = playWhenReady
        return true
    }

    fun togglePlay() {
        if (player.mediaItemCount == 0) return
        if (player.isPlaying) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
            player.play()
        }
    }

    fun pause() = player.pause()

    /** REPEAT_MODE_ONE loops the current verse until toggled off or the verse changes. */
    fun setLooping(loop: Boolean) {
        player.repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    /** Applies immediately to current playback and persists for following verses. */
    fun setSpeed(speed: Float) = player.setPlaybackSpeed(speed)

    fun progress(): Float {
        val d = player.duration
        return if (d == C.TIME_UNSET || d <= 0) 0f else (player.currentPosition.toFloat() / d).coerceIn(0f, 1f)
    }

    fun release() = player.release()
}
