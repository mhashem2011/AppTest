package com.fundingledger.gold

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Rolling 48 h history of (timestamp, SAR/g) points kept in SharedPreferences. */
object GoldHistory {
    private const val PREFS = "gold_history"
    private const val KEY = "points"

    data class Point(val t: Long, val v: Double)

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(ctx: Context): List<Point> {
        val raw = prefs(ctx).getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(Point(o.getLong("t"), o.getDouble("v")))
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun save(ctx: Context, points: List<Point>) {
        val arr = JSONArray()
        points.forEach { p -> arr.put(JSONObject().put("t", p.t).put("v", p.v)) }
        prefs(ctx).edit().putString(KEY, arr.toString()).apply()
    }

    /** Append honouring the 5-min minimum gap, then prune to the 48 h window. */
    fun append(ctx: Context, t: Long, v: Double) {
        val current = load(ctx).toMutableList()
        val last = current.lastOrNull()
        if (last != null && t - last.t < GoldConfig.HISTORY_MIN_GAP_MS) return
        current.add(Point(t, v))
        val cutoff = t - GoldConfig.HISTORY_WINDOW_MS
        save(ctx, current.filter { it.t >= cutoff })
    }

    /** SAR/g of the stored point closest to [targetMs], or null if none within tolerance. */
    fun closest(points: List<Point>, targetMs: Long, toleranceMs: Long): Double? {
        var best: Point? = null
        var bestDiff = Long.MAX_VALUE
        for (p in points) {
            val d = kotlin.math.abs(p.t - targetMs)
            if (d < bestDiff) { bestDiff = d; best = p }
        }
        return if (best != null && bestDiff <= toleranceMs) best.v else null
    }
}
