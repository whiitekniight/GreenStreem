package com.example.greenstreem

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.CompoundButton
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ManageGroupsActivity : AppCompatActivity() {

    private lateinit var rvGroups: RecyclerView
    private lateinit var db: AppDatabase
    private var allCategories: List<XtreamCategory> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ProFeatureGate.require(this, "Group management is available in GreenStreem Pro.")) return
        setContentView(R.layout.activity_manage_groups)

        db = AppDatabase.getDatabase(this)
        XtreamManager.initFromPrefs(this)
        
        rvGroups = findViewById(R.id.rvGroups)
        rvGroups.layoutManager = LinearLayoutManager(this)

        findViewById<Button>(R.id.btnHideAll).setOnClickListener { bulkAction(false) }
        findViewById<Button>(R.id.btnUnhideAll).setOnClickListener { bulkAction(true) }

        fetchAndDisplayGroups()
    }

    private fun fetchAndDisplayGroups() {
        if (isPlainM3uPlaylist()) {
            lifecycleScope.launch {
                allCategories = M3uGroupLoader.loadLiveGroups(this@ManageGroupsActivity)
                refreshList()
            }
            return
        }
        val service = XtreamManager.getService() ?: return
        
        service.getLiveCategories(XtreamManager.username, XtreamManager.password)
            .enqueue(object : Callback<List<XtreamCategory>> {
                override fun onResponse(call: Call<List<XtreamCategory>>, response: Response<List<XtreamCategory>>) {
                    if (response.isSuccessful) {
                        allCategories = response.body() ?: emptyList()
                        refreshList()
                    }
                }
                override fun onFailure(call: Call<List<XtreamCategory>>, t: Throwable) {}
            })
    }

    private fun isPlainM3uPlaylist(): Boolean {
        val prefs = getSharedPreferences("iptv_prefs", MODE_PRIVATE)
        return prefs.getString("playlist_type", "xtream") == "m3u"
    }

    private fun refreshList() {
        lifecycleScope.launch {
            val hiddenIds = db.groupDao().getAllHidden().first().map { it.groupId }.toSet()
            rvGroups.adapter = GroupToggleAdapter(
                allCategories,
                hiddenIds,
                onToggle = { category, isVisible -> toggleGroup(category, isVisible) },
                onFocused = { position -> centerGroupPosition(position) }
            )
        }
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

    private fun bulkAction(showAll: Boolean) {
        lifecycleScope.launch {
            allCategories.forEach { category ->
                if (showAll) {
                    db.groupDao().showGroup(HiddenGroup(category.id, category.name))
                } else {
                    db.groupDao().hideGroup(HiddenGroup(category.id, category.name))
                }
            }
            markGroupsChanged()
            refreshList()
        }
    }

    private fun toggleGroup(category: XtreamCategory, isVisible: Boolean) {
        lifecycleScope.launch {
            if (isVisible) {
                db.groupDao().showGroup(HiddenGroup(category.id, category.name))
            } else {
                db.groupDao().hideGroup(HiddenGroup(category.id, category.name))
            }
            markGroupsChanged()
        }
    }

    private fun markGroupsChanged() {
        getSharedPreferences(PREFS, MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_GROUPS_CHANGED, true)
            .apply()
    }

    private class GroupToggleAdapter(
        private val items: List<XtreamCategory>,
        private val hiddenIds: Set<String>,
        private val onToggle: (XtreamCategory, Boolean) -> Unit,
        private val onFocused: (Int) -> Unit
    ) : RecyclerView.Adapter<GroupToggleAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tvGroupName)
            val swVisible: SwitchCompat = view.findViewById(R.id.swGroupVisible)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_group_toggle, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.tvName.text = item.name
            holder.swVisible.setOnCheckedChangeListener(null)
            holder.swVisible.isChecked = !hiddenIds.contains(item.id)
            holder.swVisible.isClickable = true
            holder.swVisible.isFocusable = false
            
            holder.itemView.isFocusable = true
            holder.itemView.setBackgroundResource(R.drawable.selector_button_bg)
            holder.itemView.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    val currentPosition = holder.bindingAdapterPosition
                    if (currentPosition != RecyclerView.NO_POSITION) onFocused(currentPosition)
                }
            }

            holder.itemView.setOnClickListener {
                val newState = !holder.swVisible.isChecked
                holder.swVisible.isChecked = newState
            }
            holder.swVisible.setOnCheckedChangeListener { _: CompoundButton, isChecked: Boolean ->
                onToggle(item, isChecked)
            }
        }

        override fun getItemCount() = items.size
    }

    companion object {
        const val PREFS = "iptv_prefs"
        const val KEY_GROUPS_CHANGED = "groups_changed"
    }
}
