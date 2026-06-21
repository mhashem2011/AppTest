package com.hifibt.player.audio

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The playback core: a standard ExoPlayer that decodes the source (FLAC natively)
 * and plays it through the system audio output to phone speaker, headphones,
 * Bluetooth, or Android Auto.
 *
 *  - Digital volume held at unity (1.0) so no bits are lost to attenuation.
 *  - Generous buffering absorbs network/Wi-Fi jitter and prevents dropouts.
 *  - EQ is provided by [SystemEqualizer], which sits outside this decode path and
 *    therefore can never mute or break playback.
 */
class AudioEngine(context: Context) {

    val equalizer = SystemEqualizer()

    private val loadControl = DefaultLoadControl.Builder()
        // min/max buffer, start, and resume-after-rebuffer (ms).
        .setBufferDurationsMs(15_000, 60_000, 2_000, 4_000)
        .build()

    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setLoadControl(loadControl)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            /* handleAudioFocus = */ true,
        )
        .setHandleAudioBecomingNoisy(true)
        .build()
        .apply { volume = 1.0f }

    private val _playbackError = MutableStateFlow<String?>(null)
    /** Last playback error message, surfaced to the UI so failures aren't silent. */
    val playbackError: StateFlow<String?> = _playbackError

    init {
        equalizer.attach(player.audioSessionId)
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                _playbackError.value = "Playback failed: ${error.errorCodeName} — ${error.message}"
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) _playbackError.value = null
            }
        })
    }

    /** Play any direct audio URL (radio stream or podcast episode). */
    fun play(url: String, title: String, artist: String) {
        val item = MediaItem.Builder()
            .setUri(url)
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .build(),
            )
            .build()
        player.setMediaItem(item)
        player.prepare()
        player.playWhenReady = true
    }

    fun release() {
        equalizer.release()
        player.release()
    }
}
