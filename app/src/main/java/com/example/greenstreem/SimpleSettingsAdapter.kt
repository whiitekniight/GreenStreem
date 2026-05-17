package com.example.greenstreem

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.RecyclerView

class SimpleSettingsAdapter(
    private val items: List<String>,
    private val onClick: (String) -> Unit
) : RecyclerView.Adapter<SimpleSettingsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val text: TextView = view.findViewById(android.R.id.text1)
        val toggle: SwitchCompat? = view.findViewById(android.R.id.toggle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val context = parent.context
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val text = TextView(context).apply {
            id = android.R.id.text1
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val toggle = SwitchCompat(context).apply {
            id = android.R.id.toggle
            isClickable = false
            isFocusable = false
        }
        row.addView(text)
        row.addView(toggle)
        return ViewHolder(row)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val toggleState = parseToggle(item)
        holder.text.text = toggleState?.first ?: item
        holder.text.setTextColor(android.graphics.Color.WHITE)
        holder.text.textSize = 18f
        holder.toggle?.visibility = if (toggleState == null) View.GONE else View.VISIBLE
        holder.toggle?.isChecked = toggleState?.second == true
        holder.itemView.isFocusable = true
        holder.itemView.background = AppearanceTheme.buttonBackground(holder.itemView.context)
        holder.itemView.setPadding(32, 24, 32, 24)
        
        holder.itemView.setOnClickListener { onClick(item) }
        
        holder.itemView.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                holder.itemView.animate().scaleX(1.05f).scaleY(1.05f).setDuration(200).start()
            } else {
                holder.itemView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start()
            }
        }
    }

    override fun getItemCount() = items.size

    private fun parseToggle(item: String): Pair<String, Boolean>? {
        return when {
            item.endsWith(": On") -> item.removeSuffix(": On") to true
            item.endsWith(": Off") -> item.removeSuffix(": Off") to false
            else -> null
        }
    }
}
