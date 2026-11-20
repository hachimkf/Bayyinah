package com.bayyinah.app.audio

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import com.bayyinah.app.BuildConfig
import com.bayyinah.app.data.AyahClip

/**
 * Streams one ayah at a time: a clip [start, end) of the reciter's whole-surah MP3.
 *
 * - Nothing is stored on the phone (no cache): only the ayah's bytes are fetched, via HTTP range
 *   requests, thanks to constant-bitrate seeking (all supported recordings are CBR MP3s).
 * - A small buffer makes playback start in well under a second on mobile data.
 * - Repeat loops the clip; speed 0.75× / 1.0× applies immediately.
 */
class RecitationPlayer(context: Context, private val listener: Listener) {

    interface Listener {
        fun onIsPlayingChanged(isPlaying: Boolean)
        fun onBufferingChanged(buffering: Boolean)
        fun onEnded()
        fun onError(network: Boolean)
    }

    private val app = context.applicationContext

    val player: ExoPlayer = run {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent("Bayyinah-Android/${BuildConfig.VERSION_NAME}")
            .setConnectTimeoutMs(10_000)
            .setReadTimeoutMs(15_000)
            .setAllowCrossProtocolRedirects(true)
        // CBR seeking lets ExoPlayer jump straight to the ayah's byte offset (no Xing table needed).
        val extractors = DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
            .setConstantBitrateSeekingAlwaysEnabled(true)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 4_000,
                /* maxBufferMs = */ 12_000,      // never fetch far past the ayah
                /* bufferForPlaybackMs = */ 400,  // start almost immediately
                /* bufferForPlaybackAfterRebufferMs = */ 1_000,
            )
            .build()
        ExoPlayer.Builder(app)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(app, http), extractors))
            .setLoadControl(loadControl)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true) // pause when headphones are unplugged
            .build()
            .apply {
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) = listener.onIsPlayingChanged(isPlaying)
                    override fun onPlaybackStateChanged(state: Int) {
                        listener.onBufferingChanged(state == Player.STATE_BUFFERING)
                        if (state == Player.STATE_ENDED) listener.onEnded()
                    }
                    override fun onPlayerError(error: PlaybackException) {
                        android.util.Log.w("BayyinahAudio", "Playback error ${error.errorCodeName}", error)
                        listener.onError(network = error.errorCode in 2000..2999)
                    }
                })
            }
    }

    /** Loads (and optionally starts) the ayah clip; null clears the player. */
    fun load(clip: AyahClip?, playWhenReady: Boolean) {
        if (clip == null) { player.stop(); player.clearMediaItems(); return }
        player.setMediaItem(
            MediaItem.Builder()
                .setUri(clip.url)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(clip.startMs)
                        .setEndPositionMs(clip.endMs)
                        .build()
                )
                .build()
        )
        player.prepare()
        player.playWhenReady = playWhenReady
    }

    /** After a network error: try the same ayah again. */
    fun retry() { player.prepare(); player.play() }

    fun togglePlay() {
        if (player.mediaItemCount == 0) return
        if (player.isPlaying) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
            if (player.playbackState == Player.STATE_IDLE) player.prepare()
            player.play()
        }
    }

    fun pause() = player.pause()

    /** REPEAT_MODE_ONE loops the current ayah until toggled off or the ayah changes. */
    fun setLooping(loop: Boolean) {
        player.repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    /** Applies immediately to current playback and persists for following ayat. */
    fun setSpeed(speed: Float) = player.setPlaybackSpeed(speed)

    fun progress(): Float {
        val d = player.duration
        return if (d == C.TIME_UNSET || d <= 0) 0f else (player.currentPosition.toFloat() / d).coerceIn(0f, 1f)
    }

    fun release() = player.release()
}
