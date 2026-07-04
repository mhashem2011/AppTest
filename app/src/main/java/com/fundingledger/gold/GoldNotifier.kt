package com.fundingledger.gold

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.fundingledger.MainActivity
import com.fundingledger.R

/** Posts/updates the single hourly status notification (fixed id, updates in place). */
object GoldNotifier {
    private const val CHANNEL_ID = "gold_move_alert"
    private const val CHANNEL_NAME = "Gold Move Alerts"
    private const val NOTIF_ID = 1001
    private const val TITLE = "Gold Tracker (24k)"

    private fun ensureChannel(ctx: Context) {
        val mgr = ctx.getSystemService(NotificationManager::class.java)
        if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Alerts when gold moves ±0.5% or more in an hour" }
            mgr.createNotificationChannel(channel)
        }
    }

    private fun contentIntent(ctx: Context): PendingIntent {
        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            ctx, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun post(ctx: Context, collapsed: String, full: String) {
        ensureChannel(ctx)
        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(TITLE)
            .setContentText(collapsed)
            .setStyle(NotificationCompat.BigTextStyle().bigText(full))
            .setContentIntent(contentIntent(ctx))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOnlyAlertOnce(false)   // re-alert (sound/heads-up) on every significant move
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(NOTIF_ID, notification)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS not granted yet; nothing to show.
        }
    }

    fun show(ctx: Context, snap: GoldRepository.Snapshot) {
        post(ctx, snap.message.substringBefore('\n'), snap.message)
    }

    fun showUnavailable(ctx: Context) {
        val msg = "⚠️ Price Unavailable — all sources failed"
        post(ctx, msg, msg)
    }
}
