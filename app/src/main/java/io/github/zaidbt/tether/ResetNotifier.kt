package io.github.zaidbt.tether

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

// Ongoing notification with a countdown while a code reset is pending
object ResetNotifier {
    private const val CHANNEL = "code_reset"
    private const val ID = 1

    fun show(c: Context, availableAtWallMs: Long) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Passcode reset", NotificationManager.IMPORTANCE_LOW)
        )
        val n = Notification.Builder(c, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle("Passcode reset pending")
            .setContentText("A new code can be set when the countdown ends")
            .setOngoing(true)
            .setShowWhen(true)
            .setWhen(availableAtWallMs)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .build()
        nm.notify(ID, n) // silently does nothing if notifications are not allowed
    }

    fun cancel(c: Context) {
        c.getSystemService(NotificationManager::class.java).cancel(ID)
    }
}