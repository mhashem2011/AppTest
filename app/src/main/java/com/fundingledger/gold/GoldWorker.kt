package com.fundingledger.gold

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalTime
import kotlin.math.abs

/**
 * Hourly background worker. Runs the shared refresh logic every hour, but only
 * NOTIFIES when the price has moved by at least ±0.5% versus the reading ~1 hour ago.
 * The fetch still runs each hour (so the cached reading / dashboard stays fresh) and
 * history keeps building — it just stays silent unless the move is significant.
 *
 * Notifications are also limited to waking hours (07:00–23:00 local); a significant
 * move outside that window updates the data silently and does not alert.
 */
class GoldWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        when (val outcome = GoldRepository.refresh(applicationContext)) {
            is GoldRepository.Result.Success -> {
                if (withinNotifyWindow() && isSignificantMove(outcome.snapshot)) {
                    GoldNotifier.show(applicationContext, outcome.snapshot)
                }
                Result.success()
            }
            // Source failure: stay silent (alerts fire only on significant moves);
            // just ask WorkManager to retry with the configured backoff.
            is GoldRepository.Result.Failure -> Result.retry()
        }
    }

    /** True between 07:00 and 23:00 local time (inclusive of both ends). */
    private fun withinNotifyWindow(): Boolean {
        val now = LocalTime.now()
        return !now.isBefore(NOTIFY_START) && !now.isAfter(NOTIFY_END)
    }

    /** True when |% change vs the ~1h-ago reading| is at least the alert threshold. */
    private fun isSignificantMove(s: GoldRepository.Snapshot): Boolean {
        val ref = s.ref1h ?: return false
        if (ref == 0.0) return false
        val pct = (s.sarRaw - ref) / ref * 100.0
        return abs(pct) >= THRESHOLD_PCT
    }

    private companion object {
        val NOTIFY_START: LocalTime = LocalTime.of(7, 0)   // 7:00 AM
        val NOTIFY_END: LocalTime = LocalTime.of(23, 0)     // 11:00 PM
        const val THRESHOLD_PCT = 0.5                        // alert on ±0.5% or more
    }
}
