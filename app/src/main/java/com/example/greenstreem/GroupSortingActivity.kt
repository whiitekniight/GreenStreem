package com.example.greenstreem

import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.Collections

class GroupSortingActivity : AppCompatActivity() {

    private lateinit var rvGroups: RecyclerView
    private lateinit var db: AppDatabase
    private var sortedCategories: MutableList<XtreamCategory> = mutableListOf()
    private var adapter: GroupSortAdapter? = null
    private var itemTouchHelper: ItemTouchHelper? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ProFeatureGate.require(this, "Group sorting is available in GreenStreem Pro.")) return
        setContentView(R.layout.activity_manage_groups)

        // Set screen title and hide bulk action buttons
        findViewById<TextView>(R.id.tvTitle)?.text = "Sort Groups"
        findViewById<View>(R.id.llBulkActions)?.visibility = View.GONE

        db = AppDatabase.getDatabase(this)
        XtreamManager.initFromPrefs(this)
        
        rvGroups = findViewById(R.id.rvGroups)
        rvGroups.layoutManager = LinearLayoutManager(this)

        fetchAndDisplayVisibleGroups()
    }

    private fun fetchAndDisplayVisibleGroups() {
        if (isPlainM3uPlaylist()) {
            lifecycleScope.launch {
                val rawCategories = M3uGroupLoader.loadLiveGroups(this@GroupSortingActivity)
                displaySortableGroups(rawCategories)
            }
            return
        }
        val service = XtreamManager.getService() ?: return
        service.getLiveCategories(XtreamManager.username, XtreamManager.password)
            .enqueue(object : Callback<List<XtreamCategory>> {
                override fun onResponse(call: Call<List<XtreamCategory>>, response: Response<List<XtreamCategory>>) {
                    if (response.isSuccessful) {
                        lifecycleScope.launch { displaySortableGroups(response.body() ?: emptyList()) }
                    }
                }
                override fun onFailure(call: Call<List<XtreamCategory>>, t: Throwable) {}
            })
    }

    private suspend fun displaySortableGroups(rawCategories: List<XtreamCategory>) {
        val hiddenIds = db.groupDao().getAllHidden().first().map { it.groupId }.toSet()
        val savedOrder = db.groupOrderDao().getAllOrder().first().associateBy({ it.groupId }, { it.position })

        sortedCategories = rawCategories.filter { it.id !in hiddenIds }
            .sortedBy { savedOrder[it.id] ?: Int.MAX_VALUE }
            .toMutableList()

        adapter = GroupSortAdapter(
            sortedCategories,
            onOrderChanged = { saveOrder() },
            onDragRequested = { holder -> itemTouchHelper?.startDrag(holder) },
            onFocused = { position -> centerGroupPosition(position) }
        )
        rvGroups.adapter = adapter
        attachTouchDragSupportIfNeeded()
    }

    private fun centerGroupPosition(position: Int) {
        rvGroups.post {
            val adapter = rvGroups.adapter ?: return@post
            if (position !in 0 until adapter.itemCount) return@post
            val layout = rvGroups.layoutManager as? LinearLayoutManager ?: return@post
            val itemHeight = rvGroups.findViewHolderForAdapterPosition(position)?.itemView?.height
                ?: rvGroups.getChildAt(0)?.height
                ?: 72
            val offset = ((rvGroups.height - itemHeight) / 2).coerceAtLeast(0)
            layout.scrollToPositionWithOffset(position, offset)
        }
    }

    private fun isPlainM3uPlaylist(): Boolean {
        val prefs = getSharedPreferences("iptv_prefs", MODE_PRIVATE)
        return prefs.getString("playlist_type", "xtream") == "m3u"
    }

    private fun saveOrder() {
        lifecycleScope.launch {
            val orders = sortedCategories.mapIndexed { index, category ->
                GroupOrder(category.id, index)
            }
            db.groupOrderDao().clearAllOrder()
            db.groupOrderDao().saveOrder(orders)
            markGroupsChanged()
        }
    }

    private fun markGroupsChanged() {
        getSharedPreferences(PREFS, MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_GROUPS_CHANGED, true)
            .apply()
    }

    private fun attachTouchDragSupportIfNeeded() {
        if (itemTouchHelper != null) return
        val helper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                val a = adapter ?: return false
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
                return a.onItemMove(from, to)
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun isLongPressDragEnabled(): Boolean = true

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                saveOrder()
            }
        })
        helper.attachToRecyclerView(rvGroups)
        itemTouchHelper = helper
    }

    private class GroupSortAdapter(
        private val items: MutableList<XtreamCategory>,
        private val onOrderChanged: () -> Unit,
        private val onDragRequested: (RecyclerView.ViewHolder) -> Unit,
        private val onFocused: (Int) -> Unit
    ) : RecyclerView.Adapter<GroupSortAdapter.ViewHolder>() {
        
        private var grabbedPosition: Int = RecyclerView.NO_POSITION

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tvGroupName)
            val dragHandle: View = view.findViewById(R.id.ivReorder)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_group_toggle, parent, false)
            view.findViewById<View>(R.id.swGroupVisible).visibility = View.GONE
            view.findViewById<View>(R.id.ivReorder).visibility = View.VISIBLE
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.tvName.text = item.name
            holder.itemView.isFocusable = true
            holder.itemView.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    val currentPosition = holder.bindingAdapterPosition
                    if (currentPosition != RecyclerView.NO_POSITION) onFocused(currentPosition)
                }
            }
            
            updateBackground(holder, position)
            holder.dragHandle.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    onDragRequested(holder)
                    true
                } else {
                    false
                }
            }

            holder.itemView.setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN) {
                    when (keyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                            val currentPos = holder.bindingAdapterPosition
                            if (currentPos == RecyclerView.NO_POSITION) return@setOnKeyListener false

                            if (grabbedPosition == RecyclerView.NO_POSITION) {
                                // GRAB
                                grabbedPosition = currentPos
                                notifyItemChanged(currentPos)
                            } else if (grabbedPosition == currentPos) {
                                // DROP
                                grabbedPosition = RecyclerView.NO_POSITION
                                notifyItemChanged(currentPos)
                                onOrderChanged()
                            }
                            return@setOnKeyListener true
                        }
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            if (grabbedPosition != RecyclerView.NO_POSITION) {
                                val currentPos = holder.bindingAdapterPosition
                                if (currentPos > 0 && currentPos == grabbedPosition) {
                                    Collections.swap(items, currentPos, currentPos - 1)
                                    grabbedPosition = currentPos - 1
                                    notifyItemMoved(currentPos, currentPos - 1)
                                    // No notifyItemChanged here to keep focus on the moving ViewHolder
                                    return@setOnKeyListener true
                                }
                            }
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (grabbedPosition != RecyclerView.NO_POSITION) {
                                val currentPos = holder.bindingAdapterPosition
                                if (currentPos < items.size - 1 && currentPos == grabbedPosition) {
                                    Collections.swap(items, currentPos, currentPos + 1)
                                    grabbedPosition = currentPos + 1
                                    notifyItemMoved(currentPos, currentPos + 1)
                                    return@setOnKeyListener true
                                }
                            }
                        }
                    }
                }
                // If grabbed, don't allow moving focus to other items via DPAD
                if (grabbedPosition != RecyclerView.NO_POSITION && event.action == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                        return@setOnKeyListener true
                    }
                }
                false
            }
        }

        fun onItemMove(from: Int, to: Int): Boolean {
            if (from !in items.indices || to !in items.indices) return false
            Collections.swap(items, from, to)
            notifyItemMoved(from, to)
            onOrderChanged()
            return true
        }

        private fun updateBackground(holder: ViewHolder, position: Int) {
            if (position == grabbedPosition) {
                holder.itemView.setBackgroundColor(ContextCompat.getColor(holder.itemView.context, R.color.tivimate_accent_green))
            } else {
                holder.itemView.setBackgroundResource(R.drawable.selector_button_bg)
            }
        }

        override fun getItemCount() = items.size
    }

    companion object {
        const val PREFS = "iptv_prefs"
        const val KEY_GROUPS_CHANGED = "groups_changed"
    }
}
