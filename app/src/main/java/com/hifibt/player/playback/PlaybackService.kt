package com.hifibt.player.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.hifibt.player.HiFiApp

/**
 * Android Auto media service.
 *
 * Extending [MediaLibraryService] (rather than plain MediaSessionService) is what
 * lets the app appear in Android Auto's media app picker and stream over the
 * wireless AA (Wi-Fi) link — which carries PCM losslessly, beating the Bluetooth
 * codec ceiling. The same shared ExoPlayer drives both the phone UI and the car,
 * so EQ, gain staging and buffering from [com.hifibt.player.audio.AudioEngine]
 * apply identically in the car.
 *
 * The library tree below is intentionally minimal (a single browsable root). The
 * next step is to populate [onGetChildren]/[onSearch] from the Deezer provider so
 * the car can browse and search the catalog directly.
 */
class PlaybackService : MediaLibraryService() {

    private var session: MediaLibrarySession? = null

    private val callback = object : MediaLibrarySession.Callback {
        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val root = MediaItem.Builder()
                .setMediaId(ROOT_ID)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                        .setTitle("HiFi BT")
                        .build(),
                )
                .build()
            return Futures.immediateFuture(LibraryResult.ofItem(root, params))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            // TODO: map Deezer playlists/favorites/search results into browsable items.
            return Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.of(), params))
        }
    }

    override fun onCreate() {
        super.onCreate()
        val player = HiFiApp.instance.audioEngine.player
        session = MediaLibrarySession.Builder(this, player, callback).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    override fun onDestroy() {
        session?.run {
            release()
            session = null
        }
        super.onDestroy()
    }

    private companion object {
        const val ROOT_ID = "root"
    }
}
