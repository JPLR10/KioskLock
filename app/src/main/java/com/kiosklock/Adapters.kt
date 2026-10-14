package com.kiosklock

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView

/** Tick-list of apps on the admin screen. */
class AppCheckAdapter(
    private val c: Context,
    private val apps: List<AppEntry>,
    private val selected: Set<String>,
) : BaseAdapter() {
    override fun getCount() = apps.size
    override fun getItem(position: Int) = apps[position]
    override fun getItemId(position: Int) = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val v = convertView ?: LayoutInflater.from(c).inflate(R.layout.item_app_check, parent, false)
        val app = apps[position]
        v.findViewById<ImageView>(R.id.icon).setImageDrawable(app.icon)
        v.findViewById<TextView>(R.id.label).text = app.label
        v.findViewById<TextView>(R.id.pkg).text = app.pkg
        v.findViewById<CheckBox>(R.id.check).isChecked = app.pkg in selected
        return v
    }
}
