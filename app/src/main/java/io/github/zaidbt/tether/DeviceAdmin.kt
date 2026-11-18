package io.github.zaidbt.tether

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

object DeviceAdmin {
    private fun component(c: Context) = ComponentName(c, TetherAdminReceiver::class.java)

    fun isActive(c: Context): Boolean =
        c.getSystemService(DevicePolicyManager::class.java).isAdminActive(component(c))

    // System screen asking the user to activate Tether as device admin
    fun activationIntent(c: Context): Intent =
        Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component(c))
            .putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Stops Tether from being uninstalled without your passcode."
            )
}