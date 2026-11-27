//It shows the countdown notification during the 12-hour forgot-code wait,
//so the wait is visible even when Tether isn't open

package io.github.zaidbt.tether.passcode

import android.R //R is android resource class
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

// Ongoing notification with a countdown while a code reset is pending
object ResetNotifier {
    private const val CHANNEL = "code_reset"  //CHANNEL: id of notification channel(every notification must belong to a category)
    private const val ID = 1  //

    fun show(c: Context, availableAtWallMs: Long) {
        val nm = c.getSystemService(NotificationManager::class.java)  //nm: systems notification manager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Passcode reset", NotificationManager.IMPORTANCE_LOW)
        )//IMPORTANCE_LOW: now sound or pop-up
        val n = Notification.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_lock_idle_lock)
            .setContentTitle("Passcode reset pending")
            .setContentText("A new code can be set when the countdown ends")
            .setOngoing(true)  //the user cant swipe it away
            .setShowWhen(true)
            .setWhen(availableAtWallMs)  //set target wall-clock time when reset becomes available
            .setUsesChronometer(true) //Render the timestamp as a live chronometer (updates automatically)
            .setChronometerCountDown(true)  //live countdown towards availableAtWallMs
            .build()
        nm.notify(ID, n) //notify() posts it, silently does nothing if notifications are not allowed (no crash)
    }

    fun cancel(c: Context) {  //removes the notification
        c.getSystemService(NotificationManager::class.java).cancel(ID)
    }
}