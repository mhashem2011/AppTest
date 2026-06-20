package com.hifibt.player.content

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

/**
 * Internet-radio catalog backed by the free, keyless Radio Browser API
 * (https://api.radio-browser.info). Stations expose their codec and bitrate, so
 * the app can surface the genuinely hi-fi ones — including stations that broadcast
 * lossless FLAC.
 *
 * The API is mirrored across several servers; we use one primary host with a
 * couple of fallbacks, and send a descriptive User-Agent as the project requests.
 */
class RadioBrowserProvider(
    private val http: OkHttpClient = OkHttpClient(),
) {
    private val hosts = listOf(
        "https://de1.api.radio-browser.info",
        "https://nl1.api.radio-browser.info",
        "https://at1.api.radio-browser.info",
    )

    /** Most-played stations, optionally restricted to hi-fi streams. */
    suspend fun topStations(limit: Int = 60, hifiOnly: Boolean = false): List<Station> =
        get("/json/stations/topclick/$limit?hidebroken=true").let { if (hifiOnly) it.filter(Station::isHiFi) else it }

    /** Search by name, highest-bitrate first, optionally hi-fi only. */
    suspend fun search(query: String, hifiOnly: Boolean = false): List<Station> {
        val q = java.net.URLEncoder.encode(query, "UTF-8")
        val stations = get("/json/stations/search?name=$q&order=bitrate&reverse=true&hidebroken=true&limit=80")
        return if (hifiOnly) stations.filter(Station::isHiFi) else stations
    }

    private suspend fun get(path: String): List<Station> = withContext(Dispatchers.IO) {
        for (host in hosts) {
            try {
                val req = Request.Builder()
                    .url(host + path)
                    .header("User-Agent", "HiFiBT/0.1 (Android Auto radio)")
                    .build()
                val body = http.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    resp.body?.string()
                } ?: continue
                return@withContext parse(body)
            } catch (_: Exception) {
                // try next mirror
            }
        }
        emptyList()
    }

    private fun parse(json: String): List<Station> {
        val arr = JSONArray(json)
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val url = o.optString("url_resolved").ifEmpty { o.optString("url") }
                if (url.isEmpty()) continue
                add(
                    Station(
                        id = o.optString("stationuuid"),
                        name = o.optString("name").trim(),
                        streamUrl = url,
                        codec = o.optString("codec").ifEmpty { "?" },
                        bitrateKbps = o.optInt("bitrate"),
                        country = o.optString("country"),
                        tags = o.optString("tags"),
                        faviconUrl = o.optString("favicon").ifEmpty { null },
                    ),
                )
            }
        }
    }
}
