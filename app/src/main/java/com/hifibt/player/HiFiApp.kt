package com.hifibt.player

import android.app.Application
import com.hifibt.player.audio.AudioEngine
import com.hifibt.player.bluetooth.BluetoothAudioMonitor
import com.hifibt.player.streaming.DeezerProvider
import com.hifibt.player.streaming.StreamingProvider

/**
 * Tiny manual service locator. Real apps would use Hilt/Koin; for a focused
 * scaffold a single owned instance of each collaborator keeps wiring obvious.
 */
class HiFiApp : Application() {

    lateinit var audioEngine: AudioEngine
        private set
    lateinit var bluetoothMonitor: BluetoothAudioMonitor
        private set
    lateinit var provider: StreamingProvider
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        audioEngine = AudioEngine(this)
        bluetoothMonitor = BluetoothAudioMonitor(this)
        provider = DeezerProvider()
    }

    companion object {
        lateinit var instance: HiFiApp
            private set
    }
}
