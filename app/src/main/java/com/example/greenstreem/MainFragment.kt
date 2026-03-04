package com.example.greenstreem

import android.os.Bundle
import android.util.Log
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MainFragment : BrowseSupportFragment() {

    private lateinit var rowsAdapter: ArrayObjectAdapter
    private val cardPresenter = CardPresenter()
    private val favoritesAdapter = ArrayObjectAdapter(cardPresenter)
    private lateinit var favoritesRow: ListRow

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        setupUIElements()
        rowsAdapter = ArrayObjectAdapter(ListRowPresenter())
        adapter = rowsAdapter

        setupFavoritesRow()
        setupEventListeners()
        observeFavorites()
    }

    override fun onResume() {
        super.onResume()
        refreshContent()
    }

    private fun setupUIElements() {
        title = "Live TV"
        headersState = HEADERS_ENABLED
        isHeadersTransitionOnBackEnabled = true
        brandColor = ContextCompat.getColor(requireContext(), R.color.fastlane_background)
    }

    private fun setupFavoritesRow() {
        val header = HeaderItem(0, "Favorites")
        favoritesRow = ListRow(header, favoritesAdapter)
    }

    private fun observeFavorites() {
        val db = AppDatabase.getDatabase(requireContext())
        lifecycleScope.launch {
            db.favoriteDao().getAll().collectLatest { favorites ->
                favoritesAdapter.clear()
                favorites.forEach { fav ->
                    favoritesAdapter.add(
                        Channel(
                            id = fav.streamId.toLong(),
                            name = fav.name,
                            group = "Favorite",
                            logoUrl = fav.streamIcon,
                            streamUrl = "",
                            epgId = fav.epgId
                        )
                    )
                }
                updateFavoritesRowVisibility(favorites.isNotEmpty())
            }
        }
    }

    private fun updateFavoritesRowVisibility(visible: Boolean) {
        val index = rowsAdapter.indexOf(favoritesRow)
        if (visible && index == -1) {
            rowsAdapter.add(0, favoritesRow)
        } else if (!visible && index != -1) {
            rowsAdapter.remove(favoritesRow)
        }
    }

    private fun refreshContent() {
        val favInRows = rowsAdapter.indexOf(favoritesRow) != -1
        rowsAdapter.clear()
        if (favInRows) rowsAdapter.add(favoritesRow)
        fetchCategories()
    }

    private fun fetchCategories() {
        val db = AppDatabase.getDatabase(requireContext())
        val service = XtreamManager.getService()
        
        lifecycleScope.launch {
            val hiddenIds = db.groupDao().getAllHidden().first().map { it.groupId }.toSet()

            service?.getLiveCategories(XtreamManager.username, XtreamManager.password)
                ?.enqueue(object : Callback<List<XtreamCategory>> {
                    override fun onResponse(call: Call<List<XtreamCategory>>, response: Response<List<XtreamCategory>>) {
                        if (response.isSuccessful) {
                            val categories = response.body() ?: emptyList()
                            categories.filter { it.id !in hiddenIds }.forEachIndexed { index, category ->
                                val listRowAdapter = ArrayObjectAdapter(cardPresenter)
                                val header = HeaderItem((index + 1).toLong(), category.name)
                                val row = ListRow(header, listRowAdapter)
                                rowsAdapter.add(row)
                                
                                fetchChannelsForCategory(category.id, listRowAdapter)
                            }
                        }
                    }

                    override fun onFailure(call: Call<List<XtreamCategory>>, t: Throwable) {
                        Log.e("MainFragment", "Failed to fetch categories: ${t.message}")
                    }
                })
        }
    }

    private fun fetchChannelsForCategory(categoryId: String, rowAdapter: ArrayObjectAdapter) {
        val service = XtreamManager.getService()
        service?.getLiveStreams(XtreamManager.username, XtreamManager.password, categoryId)
            ?.enqueue(object : Callback<List<XtreamLiveStream>> {
                override fun onResponse(call: Call<List<XtreamLiveStream>>, response: Response<List<XtreamLiveStream>>) {
                    if (response.isSuccessful) {
                        val streams = response.body() ?: emptyList()
                        streams.forEach { stream ->
                            rowAdapter.add(
                                Channel(
                                    id = stream.streamId.toLong(),
                                    name = stream.name,
                                    group = "",
                                    logoUrl = stream.streamIcon,
                                    streamUrl = "",
                                    epgId = stream.epgId
                                )
                            )
                        }
                    }
                }

                override fun onFailure(call: Call<List<XtreamLiveStream>>, t: Throwable) {
                    Log.e("MainFragment", "Failed to fetch streams: ${t.message}")
                }
            })
    }

    private fun setupEventListeners() {
        onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
            if (item is Channel) {
                (activity as? MainActivity)?.playChannel(item)
            }
        }
    }
}
