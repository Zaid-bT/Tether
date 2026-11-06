package io.github.zaidbt.tether

import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import java.time.LocalDate
import java.time.ZoneId

// Time used today, and whether the app is open right now
data class Usage(val usedMs: Long, val isOpen: Boolean)

object UsageTracker {
    //True if the user has granted "Usage access" to Tether
    fun hasUsageAccess(context: Context): Boolean {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        @Suppress("DEPRECATION")
        val mode = ops.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    //Foreground time of one app since local midnight, from the system's event log
    //(RESUMED/PAUSED have the same values as the older foreground/background events)
    @SuppressLint("InlinedApi")
    fun usedToday(context: Context, pkg: String): Usage {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val midnight = LocalDate.now().atStartOfDay(ZoneId.systemDefault())
            .toInstant().toEpochMilli()

        val events = usm.queryEvents(midnight, now)
        val e = UsageEvents.Event()
        var total = 0L
        var resumedAt = -1L // -1 = app not currently open

        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.packageName != pkg) continue
            when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> if (resumedAt < 0) resumedAt = e.timeStamp
                UsageEvents.Event.ACTIVITY_PAUSED -> if (resumedAt >= 0) {
                    total += e.timeStamp - resumedAt
                    resumedAt = -1
                }
            }
        }
        // App is still open: count time up to now
        if (resumedAt >= 0) total += now - resumedAt
        return Usage(total, resumedAt >= 0)
    }
}