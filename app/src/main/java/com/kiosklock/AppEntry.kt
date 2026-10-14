package com.kiosklock

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

data class AppEntry(val pkg: String, val label: String, val icon: Drawable) {
    companion object {
        /** Every app on the device that has a launcher icon (except this one). */
        fun launchable(c: Context): List<AppEntry> {
            val pm = c.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            return pm.queryIntentActivities(intent, 0)
                .map { it.activityInfo.packageName }
                .distinct()
                .filter { it != c.packageName }
                .mapNotNull { load(pm, it) }
                .sortedBy { it.label.lowercase() }
        }

        /** The given packages that are still installed. */
        fun of(c: Context, pkgs: Set<String>): List<AppEntry> =
            pkgs.mapNotNull { load(c.packageManager, it) }.sortedBy { it.label.lowercase() }

        private fun load(pm: PackageManager, pkg: String): AppEntry? = try {
            val ai = pm.getApplicationInfo(pkg, 0)
            AppEntry(pkg, pm.getApplicationLabel(ai).toString(), pm.getApplicationIcon(ai))
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }
}
