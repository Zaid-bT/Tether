package io.github.zaidbt.tether

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class TetherAccessibilityService : AccessibilityService() {

    // Background scope for database work, cancelled when the service dies
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Runs every time the service starts, including after Xiaomi restarts the process
    override fun onServiceConnected() {
        scope.coroutineContext.cancelChildren()
        scope.launch {
            // Keep the in-memory block list in sync with the saved rules
            TetherDatabase.get(applicationContext).blockRuleDao().observeAll()
                .collect { rules -> BlockedApps.packages.value = rules.map { it.packageName }.toSet() }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Only react when a new window comes to the front
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return

        Log.d("Tether", "foreground: $pkg")
        ForegroundMonitor.current.value = pkg

        // Blocked app opened: cover it with the block screen
        if (pkg in BlockedApps.packages.value) {
            val intent = Intent(this, BlockActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(BlockActivity.EXTRA_PACKAGE, pkg)
            startActivity(intent)
        }
    }

    // Required override; nothing to do
    override fun onInterrupt() {}

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}