package com.kiosklock

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager
import android.widget.TextView

/**
 * Battery, Wi-Fi and mobile-data status on the kiosk screen (the clock and date are TextClocks
 * in activity_main.xml). The normal Android status bar is hidden in kiosk mode, so this replaces it.
 *
 * Call [start] in onResume and [stop] in onPause.
 */
class StatusPanel(private val a: Activity) {

    private enum class Level(val colorRes: Int) {
        GOOD(R.color.status_good),   // teal
        WARN(R.color.status_warn),   // amber
        BAD(R.color.status_bad),     // red
        OFF(R.color.status_off),     // grey
    }

    private val battery: TextView = a.findViewById(R.id.battery)
    private val wifi: TextView = a.findViewById(R.id.wifi)
    private val data: TextView = a.findViewById(R.id.data)

    private val handler = Handler(Looper.getMainLooper())
    private val cm = a.getSystemService(ConnectivityManager::class.java)
    private val wm = a.applicationContext.getSystemService(WifiManager::class.java)
    private val tm = a.getSystemService(TelephonyManager::class.java)
    private val hasPhone = a.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)

    private var running = false

    /** Signal strength has no "changed" event worth listening to, so re-read it every 5 seconds. */
    private val poll = object : Runnable {
        override fun run() {
            updateNetwork()
            handler.postDelayed(this, 5_000)
        }
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) = updateBattery(i)
    }

    /** Instant update when a network connects, disconnects or changes. */
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = refreshNetworkSoon()
        override fun onLost(network: Network) = refreshNetworkSoon()
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) =
            refreshNetworkSoon()
    }

    fun start() {
        if (running) return
        running = true
        // Battery changes are a "sticky" broadcast: registering returns the current state at once.
        a.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?.let { updateBattery(it) }
        try {
            cm?.registerNetworkCallback(NetworkRequest.Builder().build(), networkCallback)
        } catch (_: Exception) { }
        handler.post(poll)
    }

    fun stop() {
        if (!running) return
        running = false
        handler.removeCallbacksAndMessages(null)
        try { a.unregisterReceiver(batteryReceiver) } catch (_: Exception) { }
        try { cm?.unregisterNetworkCallback(networkCallback) } catch (_: Exception) { }
    }

    // -------------------------------------------------------------- battery

    private fun updateBattery(i: Intent) {
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        if (level < 0 || scale <= 0) {
            show(battery, a.getString(R.string.unknown), Level.OFF)
            return
        }
        val pct = level * 100 / scale
        val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val overheating =
            i.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) == BatteryManager.BATTERY_HEALTH_OVERHEAT

        val source = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> a.getString(R.string.src_ac)
            BatteryManager.BATTERY_PLUGGED_USB -> a.getString(R.string.src_usb)
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> a.getString(R.string.src_wireless)
            else -> null
        }
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING
        val full = status == BatteryManager.BATTERY_STATUS_FULL
        var state = when {
            charging && source != null -> a.getString(R.string.bat_charging_from, source)
            charging -> a.getString(R.string.bat_charging)
            full -> a.getString(R.string.bat_full)
            plugged != 0 -> a.getString(R.string.bat_plugged)
            else -> a.getString(R.string.bat_on_battery)
        }
        if (overheating) state += a.getString(R.string.bat_hot)

        val lvl = when {
            overheating -> Level.BAD
            charging || full -> Level.GOOD
            pct <= 15 -> Level.BAD
            pct <= 30 -> Level.WARN
            else -> Level.GOOD
        }
        show(battery, a.getString(R.string.bat_line, pct, state), lvl)
    }

    // -------------------------------------------------------------- network

    private fun refreshNetworkSoon() {
        // Network callbacks arrive on a background thread – hop to the UI thread.
        handler.post { if (running) updateNetwork() }
    }

    @Suppress("DEPRECATION") // allNetworks / connectionInfo / calculateSignalLevel still work on API 28–34
    private fun updateNetwork() {
        var wifiCaps: NetworkCapabilities? = null
        var cellCaps: NetworkCapabilities? = null
        val conn = cm
        if (conn != null) {
            for (n in conn.allNetworks) {
                val caps = conn.getNetworkCapabilities(n) ?: continue
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) wifiCaps = caps
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) cellCaps = caps
            }
        }

        // ---- Wi-Fi ----
        val w = wifiCaps
        val wman = wm
        when {
            wman == null -> show(wifi, a.getString(R.string.net_unavailable), Level.OFF)
            !wman.isWifiEnabled -> show(wifi, a.getString(R.string.net_off), Level.OFF)
            w == null -> show(wifi, a.getString(R.string.wifi_not_connected), Level.WARN)
            else -> {
                val bars = WifiManager.calculateSignalLevel(wman.connectionInfo.rssi, 5) // 0..4
                connected(wifi, w, bars)
            }
        }

        // ---- Mobile data ----
        val c = cellCaps
        val tel = tm
        when {
            !hasPhone || tel == null -> show(data, a.getString(R.string.net_unavailable), Level.OFF)
            tel.simState != TelephonyManager.SIM_STATE_READY ->
                show(data, a.getString(R.string.data_no_sim), Level.OFF)
            !dataEnabled(tel) -> show(data, a.getString(R.string.net_off), Level.OFF)
            c != null -> connected(data, c, tel.signalStrength?.level ?: 0)
            w != null -> show(data, a.getString(R.string.data_standby), Level.OFF)
            else -> show(data, a.getString(R.string.data_no_signal), Level.WARN)
        }
    }

    private fun connected(view: TextView, caps: NetworkCapabilities, bars: Int) {
        val online = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val b = bars.coerceIn(0, 4)
        if (online) {
            show(view, a.getString(R.string.net_connected, b), if (b <= 1) Level.WARN else Level.GOOD)
        } else {
            show(view, a.getString(R.string.net_no_internet, b), Level.WARN)
        }
    }

    private fun dataEnabled(tel: TelephonyManager): Boolean = try {
        tel.isDataEnabled
    } catch (_: SecurityException) {
        true // some builds want an extra permission; assume on and let the network state speak
    }

    private fun show(view: TextView, text: String, level: Level) {
        view.text = text
        view.setTextColor(a.getColor(level.colorRes))
    }
}
