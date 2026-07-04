package com.fundingledger.gold

import android.content.Context
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * The single shared fetch + compute + persist path used by both the hourly worker
 * and the gold dashboard. Source is api.gold-api.com ONLY — the same feed
 * gold-price.live displayed: https://api.gold-api.com/price/XAU
 * (JSON field "price" = USD/oz). Ported unchanged from GoldMonitor.
 */
object GoldRepository {

    private const val PREFS = "gold_state"
    private const val KEY_SNAPSHOT = "snapshot"

    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private const val SRC_PRICE = "https://api.gold-api.com/price/XAU"
    private const val SOURCE_NAME = "gold-api.com"

    enum class Trend { UP, DOWN, FLAT }

    /** A fully computed reading; persisted so the dashboard paints instantly on open. */
    data class Snapshot(
        val ozUsd: Double,
        val sar: Double,        // 2dp SAR/g; every portfolio value derives from this
        val sarRaw: Double,
        val source: String,
        val time: Long,
        val ref1h: Double?,
        val ref3h: Double?,
        val yesterday: Double?,
        val val1: Double,
        val val2: Double,
        val total: Double,
        val pl1: Double,
        val pl2: Double,
        val totalPl: Double,
        val totalPct: Double,
        val trend: Trend,
        val message: String
    ) {
        fun toJson(): String = JSONObject().apply {
            put("ozUsd", ozUsd); put("sar", sar); put("sarRaw", sarRaw)
            put("source", source); put("time", time)
            put("ref1h", ref1h ?: JSONObject.NULL)
            put("ref3h", ref3h ?: JSONObject.NULL)
            put("yesterday", yesterday ?: JSONObject.NULL)
            put("val1", val1); put("val2", val2); put("total", total)
            put("pl1", pl1); put("pl2", pl2); put("totalPl", totalPl)
            put("totalPct", totalPct); put("trend", trend.name); put("message", message)
        }.toString()

        companion object {
            fun fromJson(s: String): Snapshot? = try {
                val o = JSONObject(s)
                fun optD(k: String): Double? = if (o.isNull(k)) null else o.getDouble(k)
                Snapshot(
                    ozUsd = o.getDouble("ozUsd"),
                    sar = o.getDouble("sar"),
                    sarRaw = o.optDouble("sarRaw", o.getDouble("sar")),
                    source = o.getString("source"),
                    time = o.getLong("time"),
                    ref1h = optD("ref1h"),
                    ref3h = optD("ref3h"),
                    yesterday = optD("yesterday"),
                    val1 = o.getDouble("val1"),
                    val2 = o.getDouble("val2"),
                    total = o.getDouble("total"),
                    pl1 = o.getDouble("pl1"),
                    pl2 = o.getDouble("pl2"),
                    totalPl = o.getDouble("totalPl"),
                    totalPct = o.getDouble("totalPct"),
                    trend = Trend.valueOf(o.getString("trend")),
                    message = o.getString("message")
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    sealed class Result {
        data class Success(val snapshot: Snapshot) : Result()
        object Failure : Result()
    }

    fun lastSnapshot(ctx: Context): Snapshot? =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SNAPSHOT, null)?.let { Snapshot.fromJson(it) }

    private fun saveSnapshot(ctx: Context, snap: Snapshot) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_SNAPSHOT, snap.toJson()).apply()
    }

    // ---------- networking ----------
    private fun httpGet(urlStr: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 15000
                setRequestProperty("User-Agent", UA)
                setRequestProperty("Accept", "application/json,text/plain,*/*")
            }
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.bufferedReader().use(BufferedReader::readText)
        } catch (e: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun parsePrice(body: String?): Double? {
        body ?: return null
        return try {
            val oz = JSONObject(body).getDouble("price")
            if (GoldConfig.ozInRange(oz)) oz else null
        } catch (e: Exception) {
            null
        }
    }

    /** USD/oz from api.gold-api.com, or null if it is unreachable / out of range. */
    private fun fetchOz(): Double? {
        return parsePrice(httpGet(SRC_PRICE))
    }

    // ---------- the shared refresh path ----------
    fun refresh(ctx: Context): Result {
        val oz = fetchOz() ?: return Result.Failure
        val now = System.currentTimeMillis()

        // Compare against PRIOR history (before appending the current point).
        val prior = GoldHistory.load(ctx)
        val ref1h = GoldHistory.closest(prior, now - 3_600_000L, GoldConfig.COMPARE_TOLERANCE_MS)
        val ref3h = GoldHistory.closest(prior, now - 10_800_000L, GoldConfig.COMPARE_TOLERANCE_MS)
        val ref24h = GoldHistory.closest(prior, now - 86_400_000L, GoldConfig.COMPARE_TOLERANCE_MS)

        val sarRaw = GoldConfig.sarPerGram(oz)
        val sar = GoldConfig.round2(sarRaw) // derive all displayed values from the 2dp price

        val yesterday: Double? = ref24h // feed exposes no close; use the 24 h history point

        val val1 = sar * GoldConfig.LOT1_G
        val val2 = sar * GoldConfig.LOT2_G
        val total = sar * GoldConfig.TOTAL_G
        val pl1 = val1 - GoldConfig.LOT1_COST
        val pl2 = val2 - GoldConfig.LOT2_COST
        val totalPl = total - GoldConfig.TOTAL_COST
        val totalPct = totalPl / GoldConfig.TOTAL_COST * 100.0

        val trend = when {
            ref1h == null -> Trend.FLAT
            sarRaw > ref1h -> Trend.UP
            sarRaw < ref1h -> Trend.DOWN
            else -> Trend.FLAT
        }

        // Change vs the ~1h-ago reading (null if there is no prior point to compare):
        // percentage, and the resulting SAR swing on the 200g holding.
        val chg1hPct = ref1h?.let { if (it == 0.0) null else (sarRaw - it) / it * 100.0 }
        val chg1hSar = ref1h?.let { (sarRaw - it) * GoldConfig.TOTAL_G }

        val message = compose(
            oz, sar, ref1h, ref3h, yesterday,
            val1, val2, total, pl1, pl2, totalPl, totalPct, trend, chg1hPct, chg1hSar
        )

        GoldHistory.append(ctx, now, sarRaw)

        val snap = Snapshot(
            ozUsd = oz, sar = sar, sarRaw = sarRaw, source = SOURCE_NAME, time = now,
            ref1h = ref1h, ref3h = ref3h, yesterday = yesterday,
            val1 = val1, val2 = val2, total = total,
            pl1 = pl1, pl2 = pl2, totalPl = totalPl, totalPct = totalPct,
            trend = trend, message = message
        )
        saveSnapshot(ctx, snap)
        return Result.Success(snap)
    }

    // ---------- notification body (must match the spec format exactly) ----------
    private object Glyph {
        const val DIAMOND = "💎"   // diamond
        const val GREEN = "🟢"     // green circle
        const val RED = "🔴"       // red circle
        const val UP = "↗️"        // up-right arrow
        const val DOWN = "↘️"      // down-right arrow
        const val RIGHT = "➡️"     // right arrow
        const val MONEY = "💰"     // money bag
        const val CHART = "📊"     // bar chart
        const val DASH = "—"            // em dash
    }

    private fun arrowText(t: Trend): String = when (t) {
        Trend.UP -> Glyph.GREEN + " " + Glyph.UP
        Trend.DOWN -> Glyph.RED + " " + Glyph.DOWN
        Trend.FLAT -> Glyph.RIGHT
    }

    private fun dash(x: Double?): String = if (x == null) Glyph.DASH else GoldConfig.price2(x)

    private fun compose(
        oz: Double, sar: Double, ref1h: Double?, ref3h: Double?, yst: Double?,
        val1: Double, val2: Double, total: Double,
        pl1: Double, pl2: Double, totalPl: Double, totalPct: Double, trend: Trend,
        chg1hPct: Double?, chg1hSar: Double?
    ): String = buildString {
        // Headline: % change + 200g SAR swing vs 1h ago (also the collapsed/lock-screen line).
        if (chg1hPct != null) {
            append(arrowText(trend)).append(' ').append(GoldConfig.pct2(chg1hPct)).append(" (1h)")
            if (chg1hSar != null) {
                append(" · ").append(GoldConfig.signed0(chg1hSar)).append(" SAR")
            }
            append('\n')
        }
        append(Glyph.DIAMOND).append(" NOW: ").append(GoldConfig.price2(sar)).append(" SAR ")
            .append(arrowText(trend)).append('\n')
        append("1h: ").append(dash(ref1h)).append(" | 3h: ").append(dash(ref3h)).append('\n')
        append("Yst: ").append(dash(yst)).append('\n')
        append("Val: ").append(GoldConfig.val0(val1)).append(" (150g) | ")
            .append(GoldConfig.val0(val2)).append(" (50g)").append('\n')
        append(Glyph.MONEY).append(" TOTAL (200g): ").append(GoldConfig.val0(total)).append(" SAR").append('\n')
        append("P/L: ").append(GoldConfig.signed0(pl1)).append(" (150g) | ")
            .append(GoldConfig.signed0(pl2)).append(" (50g)").append('\n')
        append(Glyph.CHART).append(" TOTAL P/L: ").append(GoldConfig.signed0(totalPl))
            .append(" (").append(GoldConfig.pct2(totalPct)).append(")")
    }
}
