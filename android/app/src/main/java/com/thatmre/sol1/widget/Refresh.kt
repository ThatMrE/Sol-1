package com.thatmre.sol1.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/**
 * Keeps the day count honest. The widget's updatePeriodMillis handles routine refreshes; this adds
 * an inexact alarm just after local midnight so the number ticks over when the date does, and
 * re-arms after boot, time or time zone changes.
 */
object Refresh {
    private const val ACTION = "com.thatmre.sol1.REFRESH"

    fun scheduleMidnight(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(context, RefreshReceiver::class.java).setAction(ACTION)
        val pi = PendingIntent.getBroadcast(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val nextMidnight = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        am.setAndAllowWhileIdle(AlarmManager.RTC, nextMidnight + 30_000L, pi)
    }

    suspend fun updateWidgets(context: Context) {
        Sol1Widget().updateAll(context)
    }
}

class RefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Refresh.updateWidgets(context)
                Refresh.scheduleMidnight(context)
            } finally {
                pending.finish()
            }
        }
    }
}
