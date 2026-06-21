package com.hifibt.player.audio

import android.media.audiofx.Equalizer

/**
 * EQ via Android's built-in [Equalizer] audio effect, attached to the player's
 * audio session. It lives *outside* ExoPlayer's core decode path, so it can never
 * mute or break playback — when disabled it is simply not attached.
 */
class SystemEqualizer {

    private var equalizer: Equalizer? = null
    private var sessionId: Int = 0
    private val gains = HashMap<Int, Short>() // band -> millibels

    var isEnabled = false
        private set

    fun attach(sessionId: Int) {
        this.sessionId = sessionId
    }

    fun enable() {
        isEnabled = true
        build()
    }

    fun disable() {
        isEnabled = false
        equalizer?.release()
        equalizer = null
    }

    fun setBand(band: Int, millibels: Short) {
        gains[band] = millibels
        equalizer?.let { if (band < it.numberOfBands) it.setBandLevel(band.toShort(), millibels) }
    }

    private fun build() {
        if (sessionId == 0) return
        try {
            val eq = Equalizer(0, sessionId).apply { enabled = true }
            equalizer = eq
            gains.forEach { (band, gain) -> if (band < eq.numberOfBands) eq.setBandLevel(band.toShort(), gain) }
        } catch (_: Exception) {
            // Some devices restrict effects on certain outputs; fail quietly.
            equalizer = null
        }
    }

    fun release() {
        equalizer?.release()
        equalizer = null
    }
}
