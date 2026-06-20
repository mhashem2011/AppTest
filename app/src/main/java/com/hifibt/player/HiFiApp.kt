package com.hifibt.player

import android.app.Application
import com.hifibt.player.audio.AudioEngine
import com.hifibt.player.bluetooth.BluetoothAudioMonitor
import com.hifibt.player.content.PodcastProvider
import com.hifibt.player.content.RadioBrowserProvider
import okhttp3.OkHttpClient

/**
 * Tiny manual service locator. Real apps would use Hilt/Koin; for a focused
 * scaffold a single owned instance of each collaborator keeps wiring obvious.
 */
class HiFiApp : Application() {

    lateinit var audioEngine: AudioEngine
        private set
    lateinit var bluetoothMonitor: BluetoothAudioMonitor
        private set
    lateinit var radio: RadioBrowserProvider
        private set
    lateinit var podcasts: PodcastProvider
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        val http = OkHttpClient()
        audioEngine = AudioEngine(this)
        bluetoothMonitor = BluetoothAudioMonitor(this)
        radio = RadioBrowserProvider(http)
        podcasts = PodcastProvider(http)
    }

    companion object {
        lateinit var instance: HiFiApp
            private set
    }
}
