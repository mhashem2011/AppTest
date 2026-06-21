package com.hifibt.player.audio

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The playback core, tuned for the cleanest possible PCM at the hand-off to
 * Bluetooth or Android Auto:
 *
 *  - ExoPlayer decodes the source (FLAC natively) to PCM.
 *  - [EqAudioProcessor] is inserted into the audio pipeline, so any EQ is baked
 *    into the PCM that Android Auto projects over Wi-Fi (guaranteed to reach the
 *    car); disabled = bit-perfect passthrough.
 *  - Digital volume is held at unity (1.0) so no bits are discarded by digital
 *    attenuation — the car/head unit does the final level.
 *  - Generous buffering absorbs Wi-Fi jitter on wireless Android Auto, preventing
 *    dropouts that would otherwise read as "bad quality".
 */
class AudioEngine(context: Context) {

    val equalizer = EqAudioProcessor()

    private val renderersFactory = object : DefaultRenderersFactory(context) {
        override fun buildAudioSink(
            context: Context,
            enableFloatOutput: Boolean,
            enableAudioTrackPlaybackParams: Boolean,
        ): AudioSink =
            DefaultAudioSink.Builder(context)
                .setAudioProcessors(arrayOf<AudioProcessor>(equalizer))
                .build()
    }

    private val loadControl = DefaultLoadControl.Builder()
        // min/max buffer, start, and resume-after-rebuffer (ms). Large window = stable Wi-Fi.
        .setBufferDurationsMs(30_000, 120_000, 2_500, 5_000)
        .build()

    val player: ExoPlayer = ExoPlayer.Builder(context, renderersFactory)
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
        .apply {
            volume = 1.0f // unity gain: never attenuate in the digital domain
        }

    private val _playbackError = MutableStateFlow<String?>(null)
    /** Last playback error message, surfaced to the UI so failures aren't silent. */
    val playbackError: StateFlow<String?> = _playbackError

    init {
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

    fun release() = player.release()
}
