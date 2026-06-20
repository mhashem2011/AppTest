package com.hifibt.player.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.hifibt.player.HiFiApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Android Auto media service.
 *
 * Extending [MediaLibraryService] is what lets the app appear in Android Auto's
 * media picker and stream over the wireless AA (Wi-Fi/PCM) link. The browse tree
 * is: root → "Hi-Fi Radio" (top lossless/high-bitrate stations) and "Podcasts".
 *
 * Note on playback over IPC: Media3 strips a MediaItem's playback URI when sending
 * library items to a controller (e.g. the car), so playable items are sent without
 * a URI and the URL is re-attached in [onAddMediaItems] from [streamUrls]. The
 * shared ExoPlayer means EQ, unity gain and buffering apply identically in the car.
 */
class PlaybackService : MediaLibraryService() {

    private var session: MediaLibrarySession? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /** mediaId → directly playable stream URL, populated as the tree is browsed. */
    private val streamUrls = ConcurrentHashMap<String, String>()

    private val callback = object : MediaLibrarySession.Callback {

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(LibraryResult.ofItem(folder(ROOT, "HiFi Radio"), params))

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = when (parentId) {
            ROOT -> Futures.immediateFuture(
                LibraryResult.ofItemList(
                    ImmutableList.of(folder(RADIO, "Hi-Fi Radio"), folder(PODCASTS, "Podcasts")),
                    params,
                ),
            )
            RADIO -> radioChildren(params)
            else -> Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.of(), params))
        }

        /** Re-attach the playable URI that IPC dropped, looked up by mediaId. */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> {
            val resolved = mediaItems.map { item ->
                val url = streamUrls[item.mediaId]
                if (url != null) item.buildUpon().setUri(url).build() else item
            }.toMutableList()
            return Futures.immediateFuture(resolved)
        }
    }

    private fun radioChildren(params: LibraryParams?): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
        scope.launch {
            val items = runCatching { HiFiApp.instance.radio.topStations(limit = 50, hifiOnly = true) }
                .getOrDefault(emptyList())
                .map { station ->
                    val mediaId = "station:${station.id}"
                    streamUrls[mediaId] = station.streamUrl
                    playable(mediaId, station.name, station.qualityLabel)
                }
            future.set(LibraryResult.ofItemList(ImmutableList.copyOf(items), params))
        }
        return future
    }

    private fun folder(id: String, title: String): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                    .build(),
            )
            .build()

    private fun playable(mediaId: String, title: String, subtitle: String): MediaItem =
        MediaItem.Builder()
            .setMediaId(mediaId)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .build(),
            )
            .build()

    override fun onCreate() {
        super.onCreate()
        val player = HiFiApp.instance.audioEngine.player
        session = MediaLibrarySession.Builder(this, player, callback).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    override fun onDestroy() {
        session?.run { release() }
        session = null
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val ROOT = "root"
        const val RADIO = "radio_hifi"
        const val PODCASTS = "podcasts"
    }
}
