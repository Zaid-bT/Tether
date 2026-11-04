package io.github.zaidbt.tether

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class TetherAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Only react when a new window comes to the front
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return

        Log.d("Tether", "foreground: $pkg")
        ForegroundMonitor.current.value = pkg

        //if blocked app opened, cover it with the block screen
        if (pkg in BlockedApps.packages.value) {
            val intent = Intent(this, BlockActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(BlockActivity.EXTRA_PACKAGE, pkg)
            startActivity(intent)
        }
    }

    // Required override; nothing to do
    override fun onInterrupt() {}
}