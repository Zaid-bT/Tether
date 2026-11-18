package io.github.zaidbt.tether

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

class TetherAdminReceiver : DeviceAdminReceiver() {
    // Warning text the system shows when someone tries to deactivate admin
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        "Turning this off lets Tether be uninstalled and its limits removed."
}