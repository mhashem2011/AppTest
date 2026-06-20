package com.hifibt.player.streaming

/** A single playable track, normalised across whatever backend produced it. */
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationSeconds: Int,
    val artworkUrl: String?,
    /** Provider that owns this track — used to resolve the stream later. */
    val providerId: String,
)

/** Audio quality of a resolved stream, so the UI can show what's actually playing. */
enum class StreamQuality { PREVIEW_LOSSY, MP3_320, FLAC_CD, FLAC_HIRES }

/** The result of asking a provider for the actual bytes of a track. */
data class ResolvedStream(
    val url: String,
    val quality: StreamQuality,
    val mimeType: String,
)

/**
 * Pluggable music backend. Everything the rest of the app needs from a streaming
 * service lives behind this interface, so swapping or adding a provider (Deezer
 * today, something else tomorrow) never touches the audio engine or UI.
 */
interface StreamingProvider {
    val id: String
    val displayName: String

    /** Whether the user is signed in / entitled to full-length streams. */
    val isAuthenticated: Boolean

    suspend fun search(query: String): List<Track>

    /**
     * Turn a track into bytes the player can decode.
     *
     * Returns the highest-quality stream the current entitlement allows. With only
     * the public Deezer API this is the 30s preview; with full streaming access
     * (partner credentials) it returns the lossless FLAC URL — the audio engine
     * downstream is identical either way.
     */
    suspend fun resolveStream(track: Track): ResolvedStream
}
