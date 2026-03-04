package com.example.greenstreem

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class PosterAdapter(
    private val onClick: (Any) -> Unit,
    private val onFocus: (Any, Int) -> Unit,
    private val onLongClick: (Any) -> Unit
) : RecyclerView.Adapter<PosterAdapter.VH>() {

    private var items: List<Any> = emptyList()

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val ivPoster: ImageView = v.findViewById(R.id.ivPoster)
        val tvTitle: TextView = v.findViewById(R.id.tvPosterTitle)
    }

    fun submitList(newItems: List<Any>) {
        items = newItems
        notifyDataSetChanged()
    }

    fun getItemAt(position: Int): Any? = items.getOrNull(position)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_poster, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        
        val title: String
        val imageUrl: String?
        
        when (item) {
            is XtreamVodStream -> {
                title = item.name
                imageUrl = item.streamIcon
            }
            is XtreamSeries -> {
                title = item.name
                imageUrl = item.cover
            }
            else -> {
                title = "Unknown"
                imageUrl = null
            }
        }

        holder.tvTitle.text = title
        Glide.with(holder.itemView.context)
            .load(imageUrl)
            .placeholder(android.R.drawable.ic_menu_report_image)
            .centerCrop()
            .into(holder.ivPoster)

        holder.itemView.isFocusable = true
        holder.itemView.isLongClickable = true
        holder.itemView.setBackgroundResource(R.drawable.selector_poster_bg)
        
        holder.itemView.setOnClickListener { onClick(item) }
        holder.itemView.setOnLongClickListener {
            onLongClick(item)
            true
        }
        holder.itemView.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                holder.itemView.animate().scaleX(1.13f).scaleY(1.13f).setDuration(180).start()
                holder.itemView.elevation = 10f
                val pos = holder.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onFocus(item, pos)
                }
            } else {
                holder.itemView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(160).start()
                holder.itemView.elevation = 0f
            }
        }
    }

    override fun getItemCount() = items.size
}
