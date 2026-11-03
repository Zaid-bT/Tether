package io.github.zaidbt.tether

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class TetherAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Only react when a new window comes to the front
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return

        Log.d("Tether", "foreground: $pkg")
        ForegroundMonitor.current.value = pkg
    }

    // Required override; nothing to do
    override fun onInterrupt() {}
}

