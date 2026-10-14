package com.kiosklock

import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.UserManager

/** Applies and removes the device-owner lockdown policies. */
object KioskPolicy {

    /** Settings the staff/customers cannot change while the kiosk is on. */
    private val RESTRICTIONS = listOf(
        UserManager.DISALLOW_FACTORY_RESET,
        UserManager.DISALLOW_SAFE_BOOT,
        UserManager.DISALLOW_ADD_USER,
        UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA,
        UserManager.DISALLOW_USB_FILE_TRANSFER,
        UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES,
        UserManager.DISALLOW_APPS_CONTROL,
        UserManager.DISALLOW_DEBUGGING_FEATURES,
    )

    private fun dpm(c: Context) =
        c.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    private fun admin(c: Context) = ComponentName(c, KioskAdminReceiver::class.java)

    fun isDeviceOwner(c: Context) = dpm(c).isDeviceOwnerApp(c.packageName)

    fun inLockTask(c: Context): Boolean {
        val am = c.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return am.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
    }

    /** Turn the kiosk on (or re-apply after the allowed list changes). */
    fun enable(c: Context) {
        if (!isDeviceOwner(c)) return
        val dpm = dpm(c)
        val admin = admin(c)

        // Only these packages may run. Everything else – including Settings – is blocked.
        val pkgs = (Prefs.allowed(c) + c.packageName).toTypedArray()
        dpm.setLockTaskPackages(admin, pkgs)

        // Home button returns to the kiosk screen; power menu stays so the device can be restarted.
        // No recents, no notifications, no quick settings.
        dpm.setLockTaskFeatures(
            admin,
            DevicePolicyManager.LOCK_TASK_FEATURE_HOME or
                DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS
        )

        RESTRICTIONS.forEach { dpm.addUserRestriction(admin, it) }

        // Make this app the permanent home screen.
        val home = IntentFilter(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        dpm.addPersistentPreferredActivity(admin, home, ComponentName(c, MainActivity::class.java))

        dpm.setStatusBarDisabled(admin, true)
        dpm.setKeyguardDisabled(admin, true) // only works if no screen lock PIN/password is set

        Prefs.setKioskEnabled(c, true)
    }

    /** Maintenance mode: remove all lockdown so the device can be serviced. */
    fun disable(c: Context) {
        if (!isDeviceOwner(c)) return
        val dpm = dpm(c)
        val admin = admin(c)
        RESTRICTIONS.forEach { dpm.clearUserRestriction(admin, it) }
        dpm.clearPackagePersistentPreferredActivities(admin, c.packageName)
        dpm.setStatusBarDisabled(admin, false)
        dpm.setKeyguardDisabled(admin, false)
        dpm.setLockTaskPackages(admin, arrayOf(c.packageName))
        Prefs.setKioskEnabled(c, false)
    }

    /** Fully hand the device back: after this the app can be uninstalled normally. */
    fun removeDeviceOwner(c: Context) {
        disable(c)
        dpm(c).setLockTaskPackages(admin(c), emptyArray())
        dpm(c).clearDeviceOwnerApp(c.packageName)
    }
}
