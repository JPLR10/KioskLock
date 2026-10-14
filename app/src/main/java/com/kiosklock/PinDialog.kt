package com.kiosklock

import android.app.Activity
import android.app.AlertDialog
import android.text.InputType
import android.widget.EditText
import android.widget.FrameLayout

object PinDialog {
    fun ask(
        a: Activity,
        title: String,
        onCancel: (() -> Unit)? = null,
        onOk: (String) -> Unit,
    ) {
        val input = EditText(a).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = a.getString(R.string.pin_hint)
        }
        val pad = (20 * a.resources.displayMetrics.density).toInt()
        val box = FrameLayout(a).apply {
            setPadding(pad, pad / 2, pad, 0)
            addView(input)
        }
        AlertDialog.Builder(a)
            .setTitle(title)
            .setView(box)
            .setCancelable(onCancel == null)
            .setPositiveButton(android.R.string.ok) { _, _ -> onOk(input.text.toString()) }
            .setNegativeButton(android.R.string.cancel) { _, _ -> onCancel?.invoke() }
            .show()
    }
}
