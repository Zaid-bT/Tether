package io.github.zaidbt.tether.blocking

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.os.SystemClock
import io.github.zaidbt.tether.protection.AdminGuard
import io.github.zaidbt.tether.emergency.EmergencyManager
import io.github.zaidbt.tether.protection.GuardActivity
import io.github.zaidbt.tether.R
import io.github.zaidbt.tether.protection.ScreenGuard
import io.github.zaidbt.tether.data.TetherDatabase
import io.github.zaidbt.tether.passcode.PasscodeManager

class TetherAccessibilityService : AccessibilityService() {

    // Background scope for database and timer work
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Timer watching the currently limited app
    private var watchJob: Job? = null
    // Avoids opening the prompt twice for back-to-back events
    private var lastGuardAt = 0L

    // Runs every time the service starts, including after Xiaomi restarts the process
    override fun onServiceConnected() {
        scope.coroutineContext.cancelChildren()
        scope.launch {
            // Keep the in-memory rules in sync with the saved rules
            TetherDatabase.get(applicationContext).blockRuleDao().observeAll()
                .collect { rules ->
                    BlockedApps.limits.value = rules.associate { it.packageName to it.limitMinutes }
                }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Only react when a new window comes to the front
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        val cls = event.className?.toString() ?: ""
        val title = event.text.joinToString(" ")

        Log.d("Tether", "foreground: $pkg")
        ForegroundMonitor.current.value = pkg

        // Discovery log: lets us tune the guard rules per phone
        if (ScreenGuard.isSystemScreen(pkg)) Log.d("Tether", "screen: $pkg | $cls | $title")

        // Protected settings screen while locked: cover it with the passcode prompt
        if (ScreenGuard.isProtected(pkg, cls, title, getString(R.string.app_name)) &&
            PasscodeManager.isSet(this) && !PasscodeManager.isUnlocked() && !AdminGuard.isAllowed()
        ) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastGuardAt > 1_500) {
                lastGuardAt = now
                startActivity(
                    Intent(this, GuardActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            return
        }

        // Only apps with a rule get watched; other windows (keyboard, shade) are ignored
        val limitMinutes = BlockedApps.limits.value[pkg] ?: return
        watchJob?.cancel()
        watchJob = scope.launch { watch(pkg, limitMinutes) }
    }

    // Block now if the limit is used up, otherwise sleep until it would be
    private suspend fun watch(pkg: String, limitMinutes: Int) {
        var first = true
        while (true) {
            val usage = UsageTracker.usedToday(applicationContext, pkg)

            // After waking, stop if the user already left the app
            // (skipped on the first pass: the usage log may lag behind the event)
            if (!first && !usage.isOpen) return
            first = false

            // Emergency window active: do nothing until it ends, then re-check
            val grantLeft = EmergencyManager.remainingMs(applicationContext, pkg)
            if (grantLeft > 0) {
                Log.d("Tether", "$pkg emergency window ${grantLeft / 1000}s left")
                delay(grantLeft.coerceAtLeast(1_000))
                continue
            }

            val remaining = limitMinutes * 60_000L - usage.usedMs
            Log.d("Tether", "$pkg used=${usage.usedMs / 1000}s remaining=${remaining / 1000}s")
            if (remaining <= 0) {
                showBlockScreen(pkg)
                return
            }
            delay(remaining.coerceAtLeast(1_000))
        }
    }

    private fun showBlockScreen(pkg: String) {
        val intent = Intent(this, BlockActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(BlockActivity.EXTRA_PACKAGE, pkg)
        startActivity(intent)
    }

    // Required override; nothing to do
    override fun onInterrupt() {}

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}