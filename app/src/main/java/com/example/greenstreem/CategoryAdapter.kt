package com.example.greenstreem

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class CategoryAdapter(
    private val onCategoryClick: (XtreamCategory) -> Unit,
    private val onCategoryFocus: (XtreamCategory, Int) -> Unit,
    private val onCategoryLongClick: (XtreamCategory, Int, View) -> Unit
) : RecyclerView.Adapter<CategoryAdapter.VH>() {

    private var items: List<XtreamCategory> = emptyList()

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvName: TextView = v.findViewById(android.R.id.text1)
    }

    fun submitList(newItems: List<XtreamCategory>) {
        items = newItems
        notifyDataSetChanged()
    }

    fun getItemAt(pos: Int): XtreamCategory? = items.getOrNull(pos)
    fun findPositionById(id: String): Int = items.indexOfFirst { it.id == id }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_1, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val isPlaylistHeader = item.id.startsWith(PLAYLIST_HEADER_PREFIX)
        holder.tvName.text = item.name
        holder.tvName.setTextColor(if (isPlaylistHeader) AppearanceTheme.accentColor(holder.itemView.context) else android.graphics.Color.WHITE)
        holder.tvName.textSize = if (isPlaylistHeader) 17f else 16f
        holder.tvName.setTypeface(null, if (isPlaylistHeader) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        holder.tvName.setPadding(
            holder.itemView.context.dp(if (isPlaylistHeader) 12 else 28),
            holder.itemView.context.dp(if (isPlaylistHeader) 12 else 10),
            holder.itemView.context.dp(12),
            holder.itemView.context.dp(if (isPlaylistHeader) 12 else 10)
        )
        holder.itemView.isFocusable = true
        holder.itemView.background = if (isPlaylistHeader) {
            AppearanceTheme.categoryHeaderBackground(holder.itemView.context)
        } else {
            AppearanceTheme.categoryBackground(holder.itemView.context)
        }
        (holder.itemView.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
            val verticalMargin = holder.itemView.context.dp(3)
            params.setMargins(0, verticalMargin, 0, verticalMargin)
            holder.itemView.layoutParams = params
        }

        holder.itemView.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                onCategoryClick(items[pos])
            }
        }
        holder.itemView.setOnLongClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                onCategoryLongClick(items[pos], pos, holder.itemView)
                true
            } else {
                false
            }
        }
        
        holder.itemView.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                holder.itemView.animate()
                    .scaleX(1.03f)
                    .scaleY(1.03f)
                    .translationZ(2f)
                    .setDuration(150)
                    .start()
                val pos = holder.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) onCategoryFocus(items[pos], pos)
            } else {
                holder.itemView.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .translationZ(0f)
                    .setDuration(150)
                    .start()
            }
        }
    }

    override fun getItemCount() = items.size

    private fun android.content.Context.dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        const val PLAYLIST_HEADER_PREFIX = "__playlist__:"
    }
}
