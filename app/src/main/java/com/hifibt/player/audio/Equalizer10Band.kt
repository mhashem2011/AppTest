package com.hifibt.player.audio

import android.media.audiofx.Equalizer

/**
 * Thin wrapper over Android's system [Equalizer] effect, attached to the player's
 * audio session. Applied in the OS audio pipeline *before* Bluetooth encoding, so
 * the EQ shapes the sound that the car ultimately decodes.
 *
 * Kept off the hot path: when [enabled] is false the effect is released entirely,
 * leaving a bit-perfect path from FLAC decode to the Bluetooth encoder.
 */
class Equalizer10Band {

    private var equalizer: Equalizer? = null
    private var audioSessionId: Int = 0

    var enabled: Boolean = false
        private set

    /** Gains in millibels, one per band, in band order. */
    private val pendingGains = mutableListOf<Short>()

    fun attach(sessionId: Int) {
        audioSessionId = sessionId
        if (enabled) enable()
    }

    fun enable() {
        enabled = true
        if (audioSessionId == 0) return
        val eq = Equalizer(0, audioSessionId).apply { this.enabled = true }
        equalizer = eq
        pendingGains.forEachIndexed { band, gain ->
            if (band < eq.numberOfBands) eq.setBandLevel(band.toShort(), gain)
        }
    }

    fun disable() {
        enabled = false
        equalizer?.release()
        equalizer = null
    }

    /** Center frequencies (Hz) of each band, for labelling the UI sliders. */
    fun bandFrequencies(): IntArray {
        val eq = equalizer ?: return intArrayOf()
        return IntArray(eq.numberOfBands.toInt()) { eq.getCenterFreq(it.toShort()) / 1000 }
    }

    fun levelRangeMillibels(): Pair<Short, Short> {
        val r = equalizer?.bandLevelRange ?: return -1500.toShort() to 1500.toShort()
        return r[0] to r[1]
    }

    fun setBand(band: Int, millibels: Short) {
        while (pendingGains.size <= band) pendingGains.add(0)
        pendingGains[band] = millibels
        equalizer?.let { if (band < it.numberOfBands) it.setBandLevel(band.toShort(), millibels) }
    }

    fun release() {
        equalizer?.release()
        equalizer = null
    }
}
