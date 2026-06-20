package com.hifibt.player.audio

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.hifibt.player.streaming.ResolvedStream

/**
 * The playback core. ExoPlayer (Media3) decodes the source — including native FLAC —
 * and renders it through the system audio sink with the smallest possible
 * processing chain, so the bytes handed to the Bluetooth encoder stay as close to
 * the source as the link allows.
 *
 * Quality choices made here:
 *  - USAGE_MEDIA / CONTENT_TYPE_MUSIC so the OS routes to the high-quality A2DP path.
 *  - Audio offload left to ExoPlayer where the device supports it (lower jitter).
 *  - The [Equalizer10Band] is attached to this player's audio session, applied in
 *    the OS pipeline ahead of Bluetooth encoding; disabled = bit-perfect.
 */
class AudioEngine(context: Context) {

    val equalizer = Equalizer10Band()

    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            /* handleAudioFocus = */ true,
        )
        .setHandleAudioBecomingNoisy(true)
        .build()
        .also { exo ->
            equalizer.attach(exo.audioSessionId)
        }

    fun play(stream: ResolvedStream, title: String, artist: String) {
        val item = MediaItem.Builder()
            .setUri(stream.url)
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
