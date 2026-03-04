package com.example.greenstreem

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class CategoryAdapter(
    private val onCategoryClick: (XtreamCategory) -> Unit,
    private val onCategoryFocus: (XtreamCategory, Int) -> Unit
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
        holder.tvName.text = item.name
        holder.tvName.setTextColor(android.graphics.Color.WHITE)
        holder.tvName.textSize = 14f
        holder.itemView.isFocusable = true
        holder.itemView.setBackgroundResource(R.drawable.selector_button_bg)

        holder.itemView.setOnClickListener { onCategoryClick(item) }
        
        holder.itemView.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                val pos = holder.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) onCategoryFocus(items[pos], pos)
            }
        }
    }

    override fun getItemCount() = items.size
}
