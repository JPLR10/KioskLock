package com.kiosklock

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * The BlackBox7 kiosk home screen. Shows only the approved apps in a dock.
 * Long-press the small logo (top-left) to open the PIN-protected admin screen.
 */
class MainActivity : Activity() {

    private lateinit var dock: LinearLayout
    private lateinit var dockCard: View
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        dock = findViewById(R.id.dock)
        dockCard = findViewById(R.id.dock_card)
        status = findViewById(R.id.status)

        findViewById<View>(R.id.title).setOnLongClickListener { openAdmin(); true }
    }

    override fun onResume() {
        super.onResume()
        showApps()

        val owner = KioskPolicy.isDeviceOwner(this)
        val msg = when {
            !owner -> getString(R.string.status_not_owner)
            !Prefs.kioskEnabled(this) -> getString(R.string.status_off)
            else -> ""
        }
        status.text = msg
        status.visibility = if (msg.isEmpty()) View.GONE else View.VISIBLE

        // Re-enter lock task every time we come back (also covers reboots).
        if (owner && Prefs.kioskEnabled(this) && !KioskPolicy.inLockTask(this)) {
            try {
                startLockTask()
            } catch (e: Exception) {
                Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    /** Fill the dock with one tile per allowed app. */
    private fun showApps() {
        dock.removeAllViews()
        val apps = AppEntry.of(this, Prefs.allowed(this))
        dockCard.visibility = if (apps.isEmpty()) View.GONE else View.VISIBLE
        val inflater = LayoutInflater.from(this)
        for (app in apps) {
            val tile = inflater.inflate(R.layout.item_app, dock, false)
            tile.findViewById<ImageView>(R.id.icon).setImageDrawable(app.icon)
            tile.findViewById<TextView>(R.id.label).text = app.label
            tile.setOnClickListener {
                packageManager.getLaunchIntentForPackage(app.pkg)?.let { startActivity(it) }
            }
            dock.addView(tile)
        }
    }

    private fun openAdmin() {
        if (!Prefs.hasPin(this)) {
            startActivity(Intent(this, AdminActivity::class.java))
            return
        }
        val wait = Prefs.lockoutSeconds(this)
        if (wait > 0) {
            Toast.makeText(this, getString(R.string.locked_out, wait), Toast.LENGTH_SHORT).show()
            return
        }
        PinDialog.ask(this, getString(R.string.enter_pin)) { pin ->
            if (Prefs.checkPin(this, pin)) {
                startActivity(Intent(this, AdminActivity::class.java))
            } else {
                Toast.makeText(this, R.string.wrong_pin, Toast.LENGTH_SHORT).show()
            }
        }
    }

    @Deprecated("Back is disabled on the kiosk home screen")
    override fun onBackPressed() {
        // Do nothing – there is nowhere to go back to.
    }
}
