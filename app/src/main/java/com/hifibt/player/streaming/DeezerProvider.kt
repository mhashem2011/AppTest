package com.hifibt.player.streaming

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Deezer backend.
 *
 * IMPORTANT — read this before expecting hi-res to "just play":
 * Deezer retired its public third-party streaming SDK, so the open API
 * (https://api.deezer.com) returns rich metadata + a 30-second `preview` URL,
 * but NOT full-length lossless streams. Full FLAC playback through a non-Deezer
 * app requires partner/commercial streaming access from Deezer.
 *
 * This class therefore does the legitimate thing:
 *   - search & metadata via the public API (works today, no key needed for search),
 *   - resolves the preview as the playable stream,
 *   - exposes [resolveFullStream] as the single seam where real lossless access
 *     plugs in once you have entitled credentials — nothing else changes.
 */
class DeezerProvider(
    private val http: OkHttpClient = OkHttpClient(),
) : StreamingProvider {

    override val id = "deezer"
    override val displayName = "Deezer"

    // Becomes true once an OAuth access token / partner entitlement is wired in.
    override val isAuthenticated: Boolean = false

    override suspend fun search(query: String): List<Track> = withContext(Dispatchers.IO) {
        val url = "https://api.deezer.com/search?q=" + java.net.URLEncoder.encode(query, "UTF-8")
        val body = http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext emptyList()
            resp.body?.string() ?: return@withContext emptyList()
        }
        val data = JSONObject(body).optJSONArray("data") ?: return@withContext emptyList()
        buildList {
            for (i in 0 until data.length()) {
                val o = data.getJSONObject(i)
                add(
                    Track(
                        id = o.getLong("id").toString(),
                        title = o.optString("title"),
                        artist = o.optJSONObject("artist")?.optString("name") ?: "",
                        album = o.optJSONObject("album")?.optString("title") ?: "",
                        durationSeconds = o.optInt("duration"),
                        artworkUrl = o.optJSONObject("album")?.optString("cover_medium"),
                        providerId = id,
                    ),
                )
            }
        }
    }

    override suspend fun resolveStream(track: Track): ResolvedStream = withContext(Dispatchers.IO) {
        resolveFullStream(track) ?: resolvePreview(track)
    }

    /** Public-API fallback: the 30-second preview, always available. */
    private fun resolvePreview(track: Track): ResolvedStream {
        val url = "https://api.deezer.com/track/${track.id}"
        val body = http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            resp.body?.string()
        } ?: throw IllegalStateException("Deezer track lookup failed for ${track.id}")
        val preview = JSONObject(body).optString("preview")
        check(preview.isNotEmpty()) { "No preview available for ${track.id}" }
        return ResolvedStream(url = preview, quality = StreamQuality.PREVIEW_LOSSY, mimeType = "audio/mpeg")
    }

    /**
     * The single integration point for real lossless streaming.
     *
     * Return null until entitled credentials are available. Once Deezer (or a
     * licensed gateway) grants full-stream access, return the FLAC URL here and
     * the rest of the app delivers it bit-perfect to Bluetooth automatically.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun resolveFullStream(track: Track): ResolvedStream? = null
}
