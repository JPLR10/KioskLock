package com.kiosklock

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

/** Stores the allowed-app list, kiosk on/off state and the hashed admin PIN. */
object Prefs {
    private const val MAX_TRIES = 5
    private const val LOCKOUT_MS = 60_000L

    private fun sp(c: Context) = c.getSharedPreferences("kiosk", Context.MODE_PRIVATE)

    fun allowed(c: Context): Set<String> =
        sp(c).getStringSet("allowed", emptySet())?.toSet() ?: emptySet()

    fun setAllowed(c: Context, pkgs: Set<String>) =
        sp(c).edit().putStringSet("allowed", HashSet(pkgs)).apply()

    fun kioskEnabled(c: Context) = sp(c).getBoolean("enabled", false)
    fun setKioskEnabled(c: Context, on: Boolean) = sp(c).edit().putBoolean("enabled", on).apply()

    // ---- PIN ----
    fun hasPin(c: Context) = sp(c).contains("pin_hash")

    fun setPin(c: Context, pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        sp(c).edit()
            .putString("pin_salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("pin_hash", hash(salt, pin))
            .apply()
    }

    /** Seconds left before another PIN attempt is allowed (0 = allowed now). */
    fun lockoutSeconds(c: Context): Long {
        val left = sp(c).getLong("locked_until", 0) - System.currentTimeMillis()
        return if (left > 0) (left + 999) / 1000 else 0
    }

    fun checkPin(c: Context, pin: String): Boolean {
        if (lockoutSeconds(c) > 0) return false
        val salt = Base64.decode(sp(c).getString("pin_salt", "") ?: "", Base64.NO_WRAP)
        val ok = hash(salt, pin) == sp(c).getString("pin_hash", null)
        val e = sp(c).edit()
        if (ok) {
            e.putInt("fails", 0)
        } else {
            val fails = sp(c).getInt("fails", 0) + 1
            if (fails >= MAX_TRIES) {
                e.putInt("fails", 0).putLong("locked_until", System.currentTimeMillis() + LOCKOUT_MS)
            } else {
                e.putInt("fails", fails)
            }
        }
        e.apply()
        return ok
    }

    private fun hash(salt: ByteArray, pin: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(salt)
        return Base64.encodeToString(md.digest(pin.toByteArray()), Base64.NO_WRAP)
    }
}
