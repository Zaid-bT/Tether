package io.github.zaidbt.tether

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

object EmergencyManager {
    const val USES_PER_DAY = 3 //1          // emergency unlocks allowed per day (all apps together)
    const val GRANT_MS = 60_000L //10 * 60_000L   // how long one unlock lasts
    const val WAIT_SECONDS = 10 // 30         // forced wait before an unlock can be confirmed

    private const val PREFS = "tether_emergency"

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun bootCount(c: Context) =
        Settings.Global.getInt(c.contentResolver, Settings.Global.BOOT_COUNT, -1)

    // Unlocks still available today
    suspend fun usesLeft(c: Context): Int = withContext(Dispatchers.IO) {
        val midnight = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val used = TetherDatabase.get(c).emergencyGrantDao().countSince(midnight)
        (USES_PER_DAY - used).coerceAtLeast(0)
    }

    // Log the unlock and start the timed window for this app
    suspend fun grant(c: Context, pkg: String, reason: String) = withContext(Dispatchers.IO) {
        TetherDatabase.get(c).emergencyGrantDao()
            .insert(EmergencyGrant(packageName = pkg, grantedAt = System.currentTimeMillis(), reason = reason))
        prefs(c).edit()
            .putString("pkg", pkg)
            .putLong("el", SystemClock.elapsedRealtime() + GRANT_MS)
            .putInt("boot", bootCount(c))
            .apply()
    }

    // Time left on this app's window (0 if none; a reboot ends it, which is the stricter choice)
    fun remainingMs(c: Context, pkg: String): Long {
        val p = prefs(c)
        if (p.getString("pkg", null) != pkg) return 0
        if (p.getInt("boot", -1) != bootCount(c)) return 0
        return (p.getLong("el", 0) - SystemClock.elapsedRealtime()).coerceAtLeast(0)
    }
}