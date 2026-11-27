//how long has an app been in foreground and is it open rn...

package io.github.zaidbt.tether.blocking

import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import java.time.LocalDate
import java.time.ZoneId

// Time used today, and whether the app is open right now
data class Usage(val usedMs: Long, val isOpen: Boolean) //usedMs: foreground in ms today, isOpen: if the app is in the foreground rn


object UsageTracker {
    //True if the user has granted "Usage access" to Tether
    fun hasUsageAccess(context: Context): Boolean {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        @Suppress("DEPRECATION") //hides the deprecation warning
        val mode = ops.checkOpNoThrow( //checks if this uid and package do GET_USAGE_STATS(replies with allowed/ignored/errored)
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
            //Process.myUid(): tether's own user id,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    //Foreground time of one app since local midnight, from the system's event log
    //(RESUMED/PAUSED have the same values as the older foreground/background events)
    @SuppressLint("InlinedApi") //hides lint warning
    fun usedToday(context: Context, pkg: String): Usage {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager //gets usage stats manager
        val now = System.currentTimeMillis()
        val midnight = LocalDate.now().atStartOfDay(ZoneId.systemDefault())
            .toInstant().toEpochMilli() //auto reset at midnight

        val events = usm.queryEvents(midnight, now) //returns system events log for this range
        val e = UsageEvents.Event() //each event in turn
        var total = 0L //total running sum of foreground time
        var resumedAt = -1L // -1 = app not currently open

        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.packageName != pkg) continue
            when (e.eventType) {
                //working similar to a stopwatch

                //app screen is at front now...record start time only if a session is not already active
                UsageEvents.Event.ACTIVITY_RESUMED -> if (resumedAt < 0) resumedAt = e.timeStamp

                //app left foreground...if session was active add its elapsed duration to total
                UsageEvents.Event.ACTIVITY_PAUSED -> if (resumedAt >= 0) {
                    total += e.timeStamp - resumedAt
                    resumedAt = -1
                }
            }
        }
        // App is still open: count time up to now (log ends with ACTIVITY_RESUMED)
        if (resumedAt >= 0) total += now - resumedAt
        return Usage(total, resumedAt >= 0)
    }
}