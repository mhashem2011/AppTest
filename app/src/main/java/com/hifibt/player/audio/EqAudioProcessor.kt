package com.hifibt.player.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import kotlin.math.cos
import kotlin.math.sin

/**
 * A parametric EQ that runs *inside* ExoPlayer's audio pipeline, so its result is
 * baked into the PCM bytes the player outputs.
 *
 * Why this matters for wireless Android Auto: Android Auto captures the PCM the
 * player produces and carries it losslessly over Wi-Fi to the car. Because this
 * processor edits that PCM directly (rather than relying on the system
 * `audiofx.Equalizer` effect, whose interaction with the projection layer is not
 * guaranteed), the EQ is certain to reach the car.
 *
 * Implementation: a cascade of RBJ peaking biquads (Direct Form I) per channel,
 * processed in float for headroom and clamped before quantising back to 16-bit.
 * When disabled it is a transparent copy — i.e. bit-perfect passthrough.
 */
class EqAudioProcessor : BaseAudioProcessor() {

    /** Center frequencies of each band, in Hz. */
    val bandFrequencies = intArrayOf(60, 150, 400, 1000, 2500, 6000, 12000)
    private val q = 1.0

    @Volatile private var enabled = false
    private val gainsDb = FloatArray(bandFrequencies.size) // per-band gain in dB

    private var sampleRate = 0
    private var channelCount = 0

    // coeffs[band] = {b0, b1, b2, a1, a2} (a0 normalised to 1)
    private var coeffs = Array(bandFrequencies.size) { FloatArray(5) }
    // state[band][channel] = {x1, x2, y1, y2}
    private var state = Array(0) { Array(0) { FloatArray(4) } }

    fun enable() { enabled = true }
    fun disable() { enabled = false }
    fun isEnabled() = enabled

    /** UI-facing API (millibels, matching the system equalizer convention). */
    fun setBand(band: Int, millibels: Short) {
        if (band !in gainsDb.indices) return
        gainsDb[band] = millibels / 100f
        recomputeCoefficients()
    }

    val bandCount get() = bandFrequencies.size

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        state = Array(bandFrequencies.size) { Array(channelCount) { FloatArray(4) } }
        recomputeCoefficients()
        return inputAudioFormat // same format out; we only edit sample values
    }

    private fun recomputeCoefficients() {
        if (sampleRate == 0) return
        for (band in bandFrequencies.indices) {
            val f0 = bandFrequencies[band].toDouble()
            val a = Math.pow(10.0, gainsDb[band] / 40.0)        // amplitude
            val w0 = 2.0 * Math.PI * f0 / sampleRate
            val alpha = sin(w0) / (2.0 * q)
            val cosW0 = cos(w0)

            val b0 = 1 + alpha * a
            val b1 = -2 * cosW0
            val b2 = 1 - alpha * a
            val a0 = 1 + alpha / a
            val a1 = -2 * cosW0
            val a2 = 1 - alpha / a

            coeffs[band][0] = (b0 / a0).toFloat()
            coeffs[band][1] = (b1 / a0).toFloat()
            coeffs[band][2] = (b2 / a0).toFloat()
            coeffs[band][3] = (a1 / a0).toFloat()
            coeffs[band][4] = (a2 / a0).toFloat()
        }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val limit = inputBuffer.limit()
        val size = limit - inputBuffer.position()
        val output = replaceOutputBuffer(size)

        if (!enabled) {
            output.put(inputBuffer)        // transparent passthrough
            output.flip()
            return
        }

        // Android PCM16 is native (little-endian) order; ByteBuffer default matches.
        while (inputBuffer.hasRemaining()) {
            for (ch in 0 until channelCount) {
                var sample = inputBuffer.short / 32768f
                for (band in coeffs.indices) {
                    sample = processBiquad(band, ch, sample)
                }
                val clamped = sample.coerceIn(-1f, 1f)
                output.putShort((clamped * 32767f).toInt().toShort())
            }
        }
        output.flip()
    }

    private fun processBiquad(band: Int, ch: Int, x0: Float): Float {
        val c = coeffs[band]
        val s = state[band][ch]
        val y0 = c[0] * x0 + c[1] * s[0] + c[2] * s[1] - c[3] * s[2] - c[4] * s[3]
        // shift state: x2=x1, x1=x0, y2=y1, y1=y0
        s[1] = s[0]; s[0] = x0
        s[3] = s[2]; s[2] = y0
        return y0
    }

    override fun onFlush() = resetState()
    override fun onReset() = resetState()

    private fun resetState() {
        for (band in state.indices) for (ch in state[band].indices) state[band][ch].fill(0f)
    }
}
