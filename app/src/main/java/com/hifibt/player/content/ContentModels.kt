package com.hifibt.player.content

/** A live internet-radio station. The stream URL is directly playable. */
data class Station(
    val id: String,
    val name: String,
    val streamUrl: String,
    val codec: String,      // e.g. "FLAC", "MP3", "AAC"
    val bitrateKbps: Int,   // 0 when unknown (variable bitrate)
    val country: String,
    val tags: String,
    val faviconUrl: String?,
) {
    /** True for streams worth calling "hi-fi": lossless, or high-bitrate lossy. */
    val isHiFi: Boolean get() = codec.equals("FLAC", true) || bitrateKbps >= 256

    /** Human-readable quality label for the UI. */
    val qualityLabel: String get() = when {
        codec.equals("FLAC", true) -> "FLAC (lossless)"
        bitrateKbps > 0 -> "$codec ${bitrateKbps} kbps"
        else -> codec
    }
}

/** A podcast show, discovered via its RSS feed. */
data class PodcastShow(
    val id: String,
    val title: String,
    val author: String,
    val feedUrl: String,
    val artworkUrl: String?,
)

/** A single podcast episode with a directly playable audio enclosure. */
data class Episode(
    val id: String,
    val title: String,
    val audioUrl: String,
    val durationLabel: String,
    val publishedDate: String,
    val showTitle: String,
    val artworkUrl: String?,
)
