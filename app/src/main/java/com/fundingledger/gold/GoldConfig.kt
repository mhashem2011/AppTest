package com.fundingledger.gold

import java.util.Locale
import kotlin.math.round

/**
 * Single source of truth for the conversion constants, the gold portfolio, the
 * sanity bounds, history tuning, and the US-locale number formatting used by
 * the gold dashboard and the hourly worker. Ported unchanged from GoldMonitor.
 */
object GoldConfig {
    // Conversion: SAR/g = USD_per_oz / 31.1 * 3.75
    const val OZ_GRAMS = 31.1
    const val USD_SAR = 3.75

    // Portfolio (24k)
    const val LOT1_G = 150.0
    const val LOT1_COST = 65_149.50
    const val LOT2_G = 50.0
    const val LOT2_COST = 28_350.00
    const val TOTAL_G = 200.0
    const val TOTAL_COST = 93_499.50

    // Accept a spot price only if 500 < USD/oz < 20000
    const val MIN_OZ = 500.0
    const val MAX_OZ = 20_000.0

    // History / staleness tuning (milliseconds)
    const val HISTORY_WINDOW_MS = 48L * 60 * 60 * 1000   // keep 48 h
    const val HISTORY_MIN_GAP_MS = 5L * 60 * 1000        // >= 5 min between stored points
    const val COMPARE_TOLERANCE_MS = 2L * 60 * 60 * 1000 // +/- 2 h match window
    const val STALE_MS = 2L * 60 * 1000                  // dashboard refreshes if older than 2 min

    fun sarPerGram(usdPerOz: Double): Double = usdPerOz / OZ_GRAMS * USD_SAR

    // Inverse of sarPerGram: recover USD/oz from a stored SAR/g value.
    fun ozFromSar(sarPerGram: Double): Double = sarPerGram * OZ_GRAMS / USD_SAR

    fun ozInRange(oz: Double): Boolean = oz > MIN_OZ && oz < MAX_OZ

    // ---- US-locale formatting ----
    fun round2(x: Double): Double = round(x * 100.0) / 100.0
    fun price2(x: Double): String = String.format(Locale.US, "%,.2f", x)   // 2 dp, thousands
    fun val0(x: Double): String = String.format(Locale.US, "%,.0f", x)     // 0 dp, thousands
    fun signed0(x: Double): String = String.format(Locale.US, "%+,.0f", x) // +/- 0 dp
    fun signed2(x: Double): String = String.format(Locale.US, "%+,.2f", x) // +/- 2 dp
    fun pct2(x: Double): String = String.format(Locale.US, "%+.2f%%", x)   // +8.57%
}
