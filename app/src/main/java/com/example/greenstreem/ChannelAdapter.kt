package com.example.greenstreem

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ChannelAdapter(private val items: List<Channel>) : RecyclerView.Adapter<ChannelAdapter.VH>() {
    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvNumber: TextView = v.findViewById(R.id.tvNumber)
        val tvName: TextView = v.findViewById(R.id.tvName)
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_channel, parent, false)
        return VH(v)
    }
    override fun onBindViewHolder(holder: VH, position: Int) {
        val c = items[position]
        holder.tvNumber.text = c.number.toString()
        holder.tvName.text = c.name
    }
    override fun getItemCount() = items.size
}
