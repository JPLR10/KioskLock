package com.kiosklock

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.GridView
import android.widget.TextView
import android.widget.Toast

/**
 * The kiosk home screen. Shows only the approved apps.
 * Long-press the title to open the PIN-protected admin screen.
 */
class MainActivity : Activity() {

    private lateinit var grid: GridView
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        grid = findViewById(R.id.grid)
        status = findViewById(R.id.status)

        findViewById<View>(R.id.title).setOnLongClickListener { openAdmin(); true }

        grid.setOnItemClickListener { _, _, position, _ ->
            val app = grid.adapter.getItem(position) as AppEntry
            val launch = packageManager.getLaunchIntentForPackage(app.pkg)
            if (launch != null) startActivity(launch)
        }
    }

    override fun onResume() {
        super.onResume()
        grid.adapter = AppGridAdapter(this, AppEntry.of(this, Prefs.allowed(this)))

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
