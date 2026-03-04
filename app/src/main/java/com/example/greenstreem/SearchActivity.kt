package com.example.greenstreem

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class SearchActivity : AppCompatActivity() {

    private lateinit var etSearch: EditText
    private lateinit var rvResults: RecyclerView
    private val prefs by lazy { getSharedPreferences("iptv_prefs", MODE_PRIVATE) }
    
    private var allChannels: List<Channel> = emptyList()
    private var allMovies: List<XtreamVodStream> = emptyList()
    private var allSeries: List<XtreamSeries> = emptyList()
    private var recentQueries: MutableList<String> = mutableListOf()

    private val searchAdapter = GlobalSearchAdapter(
        onRecentClick = { query ->
            etSearch.setText(query)
            etSearch.setSelection(query.length)
            performSearch(query)
        },
        onChannelClick = { channel -> playChannel(channel) },
        onMovieClick = { movie -> playMovie(movie) },
        onSeriesClick = { series -> openSeries(series) }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        etSearch = findViewById(R.id.etSearch)
        rvResults = findViewById(R.id.rvSearchResults)
        
        val layoutManager = GridLayoutManager(this, 6)
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return if (searchAdapter.getItemViewType(position) == GlobalSearchAdapter.TYPE_HEADER) 6 else 1
            }
        }
        
        rvResults.layoutManager = layoutManager
        rvResults.adapter = searchAdapter

        recentQueries = loadRecentQueries().toMutableList()
        searchAdapter.setRecentQueries(recentQueries)
        fetchAllData()

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                performSearch(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        etSearch.setOnEditorActionListener { _, actionId, event ->
            val enterPressed = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP
            if (actionId == EditorInfo.IME_ACTION_SEARCH || enterPressed) {
                saveRecentQuery(etSearch.text?.toString().orEmpty())
                true
            } else {
                false
            }
        }
    }

    private fun fetchAllData() {
        val service = XtreamManager.getService() ?: return
        val user = XtreamManager.username
        val pass = XtreamManager.password

        // Fetch Live Channels
        service.getLiveStreams(user, pass).enqueue(object : Callback<List<XtreamLiveStream>> {
            override fun onResponse(call: Call<List<XtreamLiveStream>>, response: Response<List<XtreamLiveStream>>) {
                if (response.isSuccessful) {
                    allChannels = response.body()?.map { stream ->
                        Channel(id = stream.streamId.toLong(), name = stream.name, group = "", logoUrl = stream.streamIcon, streamUrl = "", epgId = stream.epgId, number = stream.num)
                    } ?: emptyList()
                }
            }
            override fun onFailure(call: Call<List<XtreamLiveStream>>, t: Throwable) {}
        })

        // Fetch Movies
        service.getVodStreams(user, pass).enqueue(object : Callback<List<XtreamVodStream>> {
            override fun onResponse(call: Call<List<XtreamVodStream>>, response: Response<List<XtreamVodStream>>) {
                if (response.isSuccessful) {
                    allMovies = response.body() ?: emptyList()
                }
            }
            override fun onFailure(call: Call<List<XtreamVodStream>>, t: Throwable) {}
        })

        // Fetch Series
        service.getSeries(user, pass).enqueue(object : Callback<List<XtreamSeries>> {
            override fun onResponse(call: Call<List<XtreamSeries>>, response: Response<List<XtreamSeries>>) {
                if (response.isSuccessful) {
                    allSeries = response.body() ?: emptyList()
                }
            }
            override fun onFailure(call: Call<List<XtreamSeries>>, t: Throwable) {}
        })
    }

    private fun performSearch(query: String) {
        val normalized = query.trim()
        if (normalized.length < 2) {
            searchAdapter.setRecentQueries(recentQueries)
            return
        }

        val filteredChannels = allChannels.asSequence()
            .filter { it.name.contains(normalized, ignoreCase = true) }
            .take(120)
            .toList()
        val filteredMovies = allMovies.asSequence()
            .filter { it.name.contains(normalized, ignoreCase = true) }
            .take(120)
            .toList()
        val filteredSeries = allSeries.asSequence()
            .filter { it.name.contains(normalized, ignoreCase = true) }
            .take(120)
            .toList()

        searchAdapter.setResults(filteredChannels, filteredMovies, filteredSeries)
        saveRecentQuery(normalized)
    }

    private fun loadRecentQueries(): List<String> {
        return prefs.getString(KEY_RECENT_SEARCHES, "")
            .orEmpty()
            .split("|")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(MAX_RECENT_SEARCHES)
    }

    private fun saveRecentQuery(raw: String) {
        val query = raw.trim()
        if (query.length < 2) return
        val next = mutableListOf<String>()
        next.add(query)
        next.addAll(recentQueries.filterNot { it.equals(query, ignoreCase = true) })
        recentQueries = next.take(MAX_RECENT_SEARCHES).toMutableList()
        prefs.edit().putString(KEY_RECENT_SEARCHES, recentQueries.joinToString("|")).apply()
    }

    private fun playChannel(channel: Channel) {
        val intent = Intent(this, MainActivity::class.java)
        intent.putExtra("play_channel", channel)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(intent)
        finish()
    }

    private fun playMovie(movie: XtreamVodStream) {
        val url = "${XtreamManager.baseUrl}/movie/${XtreamManager.username}/${XtreamManager.password}/${movie.streamId}.${movie.containerExtension ?: "mp4"}"
        val intent = Intent(this, MainActivity::class.java)
        intent.putExtra("play_url", url)
        intent.putExtra("media_title", movie.name)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(intent)
        finish()
    }

    private fun openSeries(series: XtreamSeries) {
        val intent = Intent(this, SeriesDetailsActivity::class.java)
        intent.putExtra("series_id", series.seriesId)
        intent.putExtra("series_name", series.name)
        intent.putExtra("series_cover", series.cover)
        startActivity(intent)
    }

    private class GlobalSearchAdapter(
        private val onRecentClick: (String) -> Unit,
        private val onChannelClick: (Channel) -> Unit,
        private val onMovieClick: (XtreamVodStream) -> Unit,
        private val onSeriesClick: (XtreamSeries) -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        companion object {
            const val TYPE_HEADER = 0
            const val TYPE_CHANNEL = 1
            const val TYPE_MOVIE = 2
            const val TYPE_SERIES = 3
            const val TYPE_RECENT_QUERY = 4
        }

        private var items: List<Any> = emptyList()

        fun setRecentQueries(queries: List<String>) {
            if (queries.isEmpty()) {
                items = emptyList()
                notifyDataSetChanged()
                return
            }
            val newList = mutableListOf<Any>()
            newList.add("Recent Searches")
            newList.addAll(queries)
            items = newList
            notifyDataSetChanged()
        }

        fun setResults(channels: List<Channel>, movies: List<XtreamVodStream>, series: List<XtreamSeries>) {
            val newList = mutableListOf<Any>()
            if (channels.isNotEmpty()) {
                newList.add("Live Channels")
                newList.addAll(channels)
            }
            if (movies.isNotEmpty()) {
                newList.add("Movies")
                newList.addAll(movies)
            }
            if (series.isNotEmpty()) {
                newList.add("Series")
                newList.addAll(series)
            }
            items = newList
            notifyDataSetChanged()
        }

        override fun getItemViewType(position: Int): Int {
            return when (items[position]) {
                is String -> TYPE_HEADER
                is Channel -> TYPE_CHANNEL
                is XtreamVodStream -> TYPE_MOVIE
                is XtreamSeries -> TYPE_SERIES
                is RecentSearchQuery -> TYPE_RECENT_QUERY
                else -> -1
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return when (viewType) {
                TYPE_HEADER -> HeaderVH(inflater.inflate(android.R.layout.simple_list_item_1, parent, false))
                TYPE_RECENT_QUERY -> RecentVH(inflater.inflate(android.R.layout.simple_list_item_1, parent, false))
                TYPE_CHANNEL -> ItemVH(inflater.inflate(R.layout.item_channel_epg, parent, false))
                else -> ItemVH(inflater.inflate(R.layout.item_poster, parent, false))
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val item = items[position]
            holder.itemView.isFocusable = true
            holder.itemView.setBackgroundResource(R.drawable.selector_button_bg)

            when (holder) {
                is HeaderVH -> {
                    val tv = holder.itemView.findViewById<TextView>(android.R.id.text1)
                    tv.text = item as String
                    tv.setTextColor(android.graphics.Color.YELLOW)
                    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                    holder.itemView.isFocusable = false
                }
                is RecentVH -> {
                    val tv = holder.itemView.findViewById<TextView>(android.R.id.text1)
                    val query = (item as RecentSearchQuery).query
                    tv.text = query
                    tv.setTextColor(android.graphics.Color.WHITE)
                    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                    holder.itemView.setOnClickListener { onRecentClick(query) }
                }
                is ItemVH -> {
                    val ivLogo = holder.itemView.findViewById<ImageView>(R.id.ivChannelLogo) ?: holder.itemView.findViewById<ImageView>(R.id.ivPoster)
                    val tvName = holder.itemView.findViewById<TextView>(R.id.tvChannelName) ?: holder.itemView.findViewById<TextView>(R.id.tvPosterTitle)
                    
                    when (item) {
                        is Channel -> {
                            tvName.text = item.name
                            Glide.with(holder.itemView).load(item.logoUrl).into(ivLogo)
                            holder.itemView.setOnClickListener { onChannelClick(item) }
                        }
                        is XtreamVodStream -> {
                            tvName.text = item.name
                            Glide.with(holder.itemView).load(item.streamIcon).into(ivLogo)
                            holder.itemView.setOnClickListener { onMovieClick(item) }
                        }
                        is XtreamSeries -> {
                            tvName.text = item.name
                            Glide.with(holder.itemView).load(item.cover).into(ivLogo)
                            holder.itemView.setOnClickListener { onSeriesClick(item) }
                        }
                    }
                }
            }
        }

        override fun getItemCount() = items.size

        class HeaderVH(v: View) : RecyclerView.ViewHolder(v)
        class RecentVH(v: View) : RecyclerView.ViewHolder(v)
        class ItemVH(v: View) : RecyclerView.ViewHolder(v)

        private data class RecentSearchQuery(val query: String)

        override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
            super.onViewRecycled(holder)
            holder.itemView.setOnClickListener(null)
        }

        private fun MutableList<Any>.addAll(queries: List<String>) {
            queries.forEach { add(RecentSearchQuery(it)) }
        }
    }

    companion object {
        private const val KEY_RECENT_SEARCHES = "recent_searches"
        private const val MAX_RECENT_SEARCHES = 12
    }
}
