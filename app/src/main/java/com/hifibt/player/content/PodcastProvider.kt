package com.hifibt.player.content

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser

/**
 * Podcast discovery + episode listing.
 *
 *  - Discovery uses the free, keyless iTunes Search API, which returns each show's
 *    public RSS feed URL.
 *  - Episodes come straight from that RSS feed — podcasts are open by design, and
 *    each <item> carries a directly playable <enclosure> audio URL.
 */
class PodcastProvider(
    private val http: OkHttpClient = OkHttpClient(),
) {
    suspend fun searchShows(query: String): List<PodcastShow> = withContext(Dispatchers.IO) {
        val q = java.net.URLEncoder.encode(query, "UTF-8")
        val url = "https://itunes.apple.com/search?media=podcast&limit=40&term=$q"
        val body = http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext emptyList()
            resp.body?.string() ?: return@withContext emptyList()
        }
        val results = JSONObject(body).optJSONArray("results") ?: return@withContext emptyList()
        buildList {
            for (i in 0 until results.length()) {
                val o = results.getJSONObject(i)
                val feed = o.optString("feedUrl")
                if (feed.isEmpty()) continue
                add(
                    PodcastShow(
                        id = o.optString("collectionId"),
                        title = o.optString("collectionName"),
                        author = o.optString("artistName"),
                        feedUrl = feed,
                        artworkUrl = o.optString("artworkUrl600").ifEmpty { o.optString("artworkUrl100").ifEmpty { null } },
                    ),
                )
            }
        }
    }

    suspend fun episodes(show: PodcastShow): List<Episode> = withContext(Dispatchers.IO) {
        val xml = http.newCall(Request.Builder().url(show.feedUrl).build()).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext emptyList()
            resp.body?.byteStream()?.let { parseFeed(it, show) } ?: emptyList()
        }
        xml
    }

    private fun parseFeed(input: java.io.InputStream, show: PodcastShow): List<Episode> {
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(input, null)
        }
        val episodes = mutableListOf<Episode>()
        var inItem = false
        var title = ""
        var audioUrl = ""
        var duration = ""
        var pubDate = ""
        var index = 0

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            val name = parser.name
            when (event) {
                XmlPullParser.START_TAG -> when {
                    name.equals("item", true) -> {
                        inItem = true; title = ""; audioUrl = ""; duration = ""; pubDate = ""
                    }
                    inItem && name.equals("title", true) -> title = parser.nextText().trim()
                    inItem && name.equals("enclosure", true) ->
                        audioUrl = parser.getAttributeValue(null, "url") ?: audioUrl
                    inItem && name.equals("duration", true) -> duration = parser.nextText().trim()
                    inItem && name.equals("pubDate", true) -> pubDate = parser.nextText().trim()
                }
                XmlPullParser.END_TAG -> if (name.equals("item", true)) {
                    inItem = false
                    if (audioUrl.isNotEmpty()) {
                        episodes += Episode(
                            id = "${show.id}-${index++}",
                            title = title,
                            audioUrl = audioUrl,
                            durationLabel = duration,
                            publishedDate = pubDate,
                            showTitle = show.title,
                            artworkUrl = show.artworkUrl,
                        )
                    }
                }
            }
            event = parser.next()
        }
        return episodes
    }
}
