package com.kiosklock

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast

/** PIN-protected screen: pick allowed apps, start/stop the kiosk, change PIN. */
class AdminActivity : Activity() {

    private val selected = mutableSetOf<String>()
    private lateinit var adapter: AppCheckAdapter
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin)
        status = findViewById(R.id.status)

        selected += Prefs.allowed(this)
        val apps = AppEntry.launchable(this)
        adapter = AppCheckAdapter(this, apps, selected)

        val list = findViewById<ListView>(R.id.list)
        list.adapter = adapter
        list.setOnItemClickListener { _, _, position, _ ->
            val pkg = apps[position].pkg
            if (!selected.remove(pkg)) selected.add(pkg)
            adapter.notifyDataSetChanged()
        }

        findViewById<Button>(R.id.btn_start).setOnClickListener { saveAndLock() }
        findViewById<Button>(R.id.btn_pin).setOnClickListener { changePin(mandatory = false) }
        findViewById<Button>(R.id.btn_exit).setOnClickListener { exitKiosk() }
        findViewById<Button>(R.id.btn_remove).setOnClickListener { confirmRemoveOwner() }

        if (!Prefs.hasPin(this)) changePin(mandatory = true)
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    /** Close the admin screen if someone walks away from it (home button etc.). */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        finish()
    }

    private fun updateStatus() {
        status.text = when {
            !KioskPolicy.isDeviceOwner(this) -> getString(R.string.status_not_owner)
            Prefs.kioskEnabled(this) -> getString(R.string.status_on, selected.size)
            else -> getString(R.string.status_off_admin)
        }
    }

    private fun saveAndLock() {
        Prefs.setAllowed(this, selected)
        if (!KioskPolicy.isDeviceOwner(this)) {
            Toast.makeText(this, R.string.status_not_owner, Toast.LENGTH_LONG).show()
            return
        }
        KioskPolicy.enable(this)
        Toast.makeText(this, R.string.kiosk_started, Toast.LENGTH_SHORT).show()
        finish() // back to the kiosk screen, which enters lock task
    }

    private fun exitKiosk() {
        try { stopLockTask() } catch (_: Exception) { }
        KioskPolicy.disable(this)
        updateStatus()
        Toast.makeText(this, R.string.kiosk_stopped, Toast.LENGTH_LONG).show()
    }

    private fun confirmRemoveOwner() {
        AlertDialog.Builder(this)
            .setTitle(R.string.remove_owner_title)
            .setMessage(R.string.remove_owner_msg)
            .setPositiveButton(R.string.remove) { _, _ ->
                try { stopLockTask() } catch (_: Exception) { }
                KioskPolicy.removeDeviceOwner(this)
                updateStatus()
                Toast.makeText(this, R.string.owner_removed, Toast.LENGTH_LONG).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun changePin(mandatory: Boolean) {
        val onCancel: (() -> Unit)? = if (mandatory) ({ finish() }) else null
        PinDialog.ask(this, getString(R.string.new_pin), onCancel) { first ->
            if (first.length < 4) {
                Toast.makeText(this, R.string.pin_too_short, Toast.LENGTH_SHORT).show()
                changePin(mandatory)
                return@ask
            }
            PinDialog.ask(this, getString(R.string.confirm_pin), onCancel) { second ->
                if (first == second) {
                    Prefs.setPin(this, first)
                    Toast.makeText(this, R.string.pin_saved, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, R.string.pin_mismatch, Toast.LENGTH_SHORT).show()
                    changePin(mandatory)
                }
            }
        }
    }
}
