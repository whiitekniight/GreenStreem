package com.example.greenstreem

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
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

    private fun refreshList() {
        lifecycleScope.launch {
            val hiddenIds = db.groupDao().getAllHidden().first().map { it.groupId }.toSet()
            rvGroups.adapter = GroupToggleAdapter(allCategories, hiddenIds) { category, isVisible ->
                toggleGroup(category, isVisible)
            }
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
        }
    }

    private class GroupToggleAdapter(
        private val items: List<XtreamCategory>,
        private val hiddenIds: Set<String>,
        private val onToggle: (XtreamCategory, Boolean) -> Unit
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
            holder.swVisible.isChecked = !hiddenIds.contains(item.id)
            
            holder.itemView.isFocusable = true
            holder.itemView.setBackgroundResource(R.drawable.selector_button_bg)

            holder.itemView.setOnClickListener {
                val newState = !holder.swVisible.isChecked
                holder.swVisible.isChecked = newState
                onToggle(item, newState)
            }
        }

        override fun getItemCount() = items.size
    }
}
