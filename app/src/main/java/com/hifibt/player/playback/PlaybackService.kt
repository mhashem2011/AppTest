package com.hifibt.player.playback

import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.hifibt.player.HiFiApp

/**
 * Hosts the [MediaSession] so playback keeps running in the background and the
 * BMW head unit can drive transport + see metadata over AVRCP. It wraps the same
 * shared ExoPlayer that the UI controls, so there is a single source of truth for
 * playback state.
 */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = HiFiApp.instance.audioEngine.player
        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
