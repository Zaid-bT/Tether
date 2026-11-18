package io.github.zaidbt.tether

import android.os.SystemClock

// Short window where our own activation flow may open the admin screen unguarded
object AdminGuard {
    private var allowUntil = 0L // elapsedRealtime

    fun allowFor(ms: Long) { allowUntil = SystemClock.elapsedRealtime() + ms }
    fun isAllowed() = SystemClock.elapsedRealtime() < allowUntil
}

// Decides which system screens need the passcode
object ScreenGuard {
    // Settings-like apps (stock Android and Xiaomi)
    private val systemPkgs = listOf("settings", "securitycenter", "packageinstaller", "permissioncontroller")

    // Class-name hints for device admin pages and uninstall dialogs
    private val classHints = listOf("DeviceAdmin", "Uninstall")

    fun isSystemScreen(pkg: String) = systemPkgs.any { pkg.contains(it, ignoreCase = true) }

    // Protected = a system screen that is an admin/uninstall page, or whose title mentions our app name
    fun isProtected(pkg: String, cls: String, title: String, appName: String): Boolean {
        if (!isSystemScreen(pkg)) return false
        return classHints.any { cls.contains(it, ignoreCase = true) } ||
                title.contains(appName, ignoreCase = true)
    }
}