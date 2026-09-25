package com.example.greenstreem

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class EmbyLibraryActivity : AppCompatActivity() {
    private lateinit var list: RecyclerView
    private lateinit var heading: TextView
    private lateinit var guideDetailsTitle: TextView
    private lateinit var guideDetailsBody: TextView
    private lateinit var guideDetailsPanel: LinearLayout
    private lateinit var guideGroupScroller: ScrollView
    private lateinit var guideGroupButtons: LinearLayout
    private lateinit var guideTimeHeader: LinearLayout
    private lateinit var guideTimeScroller: HorizontalScrollView
    private lateinit var guideNowLine: View
    private lateinit var categoryRail: LinearLayout
    private lateinit var liveTvTab: Button
    private var credentials: EmbySecureStore.Credentials? = null
    private lateinit var guidePreviewRow: LinearLayout
    private lateinit var previewView: androidx.media3.ui.PlayerView
    private lateinit var previewStatus: TextView
    private lateinit var previewHost: FrameLayout
    private lateinit var guideRoot: LinearLayout
    private lateinit var fullscreenHost: FrameLayout
    private var liveFullscreen = false
    private var guideFocusBeforeFullscreen: View? = null
    private var resumePreviewChannel: EmbyApiClient.MediaEntry? = null
    private var previewPlayer: androidx.media3.exoplayer.ExoPlayer? = null
    private var previewChannel: EmbyApiClient.MediaEntry? = null
    private var activeGroupId = ""
    private val lastGuideFocus = mutableMapOf<String, Pair<String, Long>>()
    private var liveRequest = 0
    private var liveGroups = emptyList<EmbyApiClient.ChannelGroup>()
    private var screen = EmbyScreen.HOME
    private var guideTimelineStartMs = 0L
    private val guideScrollers = mutableListOf<HorizontalScrollView>()
    private var syncingGuideScroll = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        credentials = EmbySecureStore.load(this)
        if (!EmbyConnectEntitlement.isUnlocked(this) || credentials == null) {
            finish()
            return
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        categoryRail = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(26), dp(22), dp(26))
            setBackgroundColor(0xFF171A20.toInt())
        }
        categoryRail.addView(TextView(this).apply {
            text = "Emby"
            textSize = 30f
            setTextColor(Color.WHITE)
            setPadding(dp(8), 0, 0, dp(28))
        })
        liveTvTab = tabButton("Live TV") { loadLiveTv() }
        categoryRail.addView(liveTvTab)
        categoryRail.addView(tabButton("Movies") { loadMovies() })
        categoryRail.addView(tabButton("TV Shows") { loadSeries() })
        categoryRail.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))
        root.addView(categoryRail, LinearLayout.LayoutParams(dp(250), LinearLayout.LayoutParams.MATCH_PARENT))

        val contentPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(16), dp(10))
        }
        heading = TextView(this).apply {
            text = "Emby Live TV"
            textSize = 22f
            setTextColor(Color.WHITE)
            setPadding(dp(4), 0, 0, dp(6))
        }
        contentPanel.addView(heading)
        guideGroupButtons = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, dp(2), dp(4))
        }
        guideGroupScroller = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(guideGroupButtons)
            visibility = View.GONE
        }
        root.addView(guideGroupScroller, LinearLayout.LayoutParams(dp(180), LinearLayout.LayoutParams.MATCH_PARENT).apply {
            setMargins(dp(8), dp(10), dp(8), dp(10))
        })
        guideDetailsTitle = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.WHITE)
            maxLines = 1
        }
        guideDetailsBody = TextView(this).apply {
            textSize = 11f
            setTextColor(0xFFB8C4D8.toInt())
            maxLines = 2
        }
        guideDetailsPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(5), dp(12), dp(5))
            background = roundedBackground(0xFF111722.toInt(), 10f, 0xFF29364A.toInt(), 1)
            addView(guideDetailsTitle)
            addView(guideDetailsBody)
        }
        guidePreviewRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        previewHost = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        previewView = androidx.media3.ui.PlayerView(this).apply {
            useController = false
            isFocusable = false
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        }
        previewStatus = TextView(this).apply {
            text = "Select a channel to preview"
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            textSize = 12f
        }
        previewHost.addView(previewView, FrameLayout.LayoutParams(-1, -1))
        previewHost.addView(previewStatus, FrameLayout.LayoutParams(-1, -1))
        guidePreviewRow.addView(previewHost, LinearLayout.LayoutParams(dp(254), -1).apply { marginEnd = dp(10) })
        guidePreviewRow.addView(guideDetailsPanel, LinearLayout.LayoutParams(0, -1, 1f))
        contentPanel.addView(guidePreviewRow, LinearLayout.LayoutParams(-1, dp(150)).apply { bottomMargin = dp(6) })
        guideTimeHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        guideTimeHeader.addView(TextView(this).apply {
            text = "CHANNELS"
            textSize = 13f
            setTextColor(0xFF7EC8FF.toInt())
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, 0, 0)
            background = roundedBackground(0xFF0D1118.toInt(), 6f, 0xFF263245.toInt(), 1)
        }, LinearLayout.LayoutParams(dp(180), dp(28)).apply { marginEnd = dp(3) })
        guideTimeScroller = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            setOnScrollChangeListener { _, scrollX, _, _, _ -> syncGuideScroll(scrollX, this) }
        }
        guideTimeHeader.addView(guideTimeScroller, LinearLayout.LayoutParams(0, dp(28), 1f))
        contentPanel.addView(guideTimeHeader)
        list = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@EmbyLibraryActivity)
            setPadding(0, dp(12), 0, 0)
        }
        val guideGridFrame = FrameLayout(this)
        guideGridFrame.addView(list, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        guideNowLine = View(this).apply {
            setBackgroundColor(0xFF4DB8FF.toInt())
            alpha = 0.9f
            isFocusable = false
        }
        guideGridFrame.addView(guideNowLine, FrameLayout.LayoutParams(dp(1), FrameLayout.LayoutParams.MATCH_PARENT).apply {
            leftMargin = dp(181)
        })
        contentPanel.addView(guideGridFrame, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(contentPanel, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        guideRoot = root
        val screenContainer = FrameLayout(this)
        screenContainer.addView(root, FrameLayout.LayoutParams(-1, -1))
        fullscreenHost = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            visibility = View.GONE
            isFocusable = true
            isFocusableInTouchMode = true
        }
        screenContainer.addView(fullscreenHost, FrameLayout.LayoutParams(-1, -1))
        setContentView(screenContainer)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (liveFullscreen) {
                    setLiveFullscreen(false)
                    return
                }
                if (screen == EmbyScreen.LIVE && !guideGroupButtons.hasFocus()) {
                    focusActiveCategory()
                    return
                }
                if (screen != EmbyScreen.HOME) {
                    showEmbyCategoryHome()
                    return
                }
                getSharedPreferences("iptv_prefs", MODE_PRIVATE)
                    .edit()
                    .putBoolean("return_to_live_guide_after_emby", true)
                    .apply()
                finish()
            }
        })
        showEmbyCategoryHome()
    }

    private val favoritesGroupId = "local:favorites"

    private fun favoriteKey(): String {
        val saved = credentials ?: return ""
        return "${saved.serverUrl.trimEnd('/')}|${saved.userId}"
    }

    private fun favoriteIds(): Set<String> =
        getSharedPreferences("emby_channel_favorites", MODE_PRIVATE)
            .getStringSet(favoriteKey(), emptySet()).orEmpty().toSet()

    private fun showFavoriteAction(channel: EmbyApiClient.MediaEntry) {
        val removing = channel.id in favoriteIds()
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(channel.name)
            .setItems(arrayOf(if (removing) "Remove from Favorites" else "Add to Favorites")) { _, _ ->
                val ids = favoriteIds().toMutableSet()
                if (removing) ids.remove(channel.id) else ids.add(channel.id)
                getSharedPreferences("emby_channel_favorites", MODE_PRIVATE).edit()
                    .putStringSet(favoriteKey(), ids).apply()
                Toast.makeText(this, if (removing) "Removed from Favorites" else "Added to Favorites", Toast.LENGTH_SHORT).show()
                if (activeGroupId == favoritesGroupId) loadLiveTv(favoritesGroupId)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadLiveTv(groupId: String? = null) {
        val saved = credentials ?: return
        val keepCategoryFocus = guideGroupButtons.hasFocus()
        val request = ++liveRequest
        screen = EmbyScreen.LIVE
        showFullscreenContent()
        heading.text = "Emby Live TV Guide"
        guideGroupScroller.visibility = View.VISIBLE
        guideDetailsPanel.visibility = View.VISIBLE
        setDetailsPanelHeight(50)
        guideTimeHeader.visibility = View.VISIBLE
        guideNowLine.visibility = View.VISIBLE
        list.layoutManager = LinearLayoutManager(this)
        guideDetailsTitle.text = "Live TV"
        guideDetailsBody.text = "Select a program to see details"
        guideScrollers.clear()
        list.adapter = EntryAdapter(listOf(EmbyApiClient.MediaEntry("", "Loading guide…", "", "", "", ""))) { }
        lifecycleScope.launch {
            if (groupId == null || liveGroups.isEmpty()) {
                val result = EmbyApiClient.channelGroups(saved)
                if (request != liveRequest || screen != EmbyScreen.LIVE) return@launch
                liveGroups = listOf(EmbyApiClient.ChannelGroup(favoritesGroupId, "Favorites")) + result.getOrElse {
                    guideDetailsBody.text = "Could not load categories. Reopen Live TV to retry."
                    list.adapter = EntryAdapter(emptyList()) { }
                    return@launch
                }.sortedBy { if (it.name.equals("Locals", true)) 0 else 1 }
            }
            val activeGroup = groupId?.let { id -> liveGroups.firstOrNull { it.id == id } } ?: liveGroups.firstOrNull()
            if (activeGroup == null) {
                guideDetailsBody.text = "No Emby channel categories are available."
                list.adapter = EntryAdapter(emptyList()) { }
                guideGroupButtons.removeAllViews()
                return@launch
            }
            activeGroupId = activeGroup.id
            renderLiveTvGroups()
            if (keepCategoryFocus) focusActiveCategory()
            heading.text = "Emby Live TV - ${activeGroup.name}"
            EmbyApiClient.liveGuide(saved, activeGroup.id.takeUnless { it == favoritesGroupId },
                if (activeGroup.id == favoritesGroupId) favoriteIds() else null)
                .onSuccess { channels ->
                    if (request != liveRequest || screen != EmbyScreen.LIVE) return@onSuccess
                    guideTimelineStartMs = (System.currentTimeMillis() / (30L * 60L * 1000L)) * (30L * 60L * 1000L)
                    renderGuideTimeHeader()
                    val adapter = GuideAdapter(channels)
                    list.adapter = adapter
                    guideDetailsBody.text = if (channels.isEmpty() && activeGroupId == favoritesGroupId)
                        "No favorites yet. Open a category, then hold OK on a program to add its channel."
                    else "Hold OK on a program to add or remove its channel from Favorites."
                    if (channels.isEmpty()) focusActiveCategory()
                    list.post {
                        if (request != liveRequest || screen != EmbyScreen.LIVE || list.adapter !== adapter) return@post

                        val now = System.currentTimeMillis()
                        val firstCurrentProgramStart = channels.firstOrNull()
                            ?.programs
                            ?.firstOrNull { now in it.startMs until it.endMs }
                            ?.startMs
                            ?: guideTimelineStartMs
                        scrollGuideToTime(firstCurrentProgramStart.coerceAtLeast(guideTimelineStartMs))
                        if (!keepCategoryFocus) adapter.restoreFocus()
                        if (previewChannel == null) channels.firstOrNull()?.channel?.let { preview(it) }
                    }
                }
                .onFailure { error ->
                    Toast.makeText(this@EmbyLibraryActivity, error.message ?: "Could not load Emby guide", Toast.LENGTH_LONG).show()
                }
        }
    }
    private fun loadMovies() = load("Emby Movies", EmbyScreen.MOVIES) { EmbyApiClient.movies(it) }
    private fun loadSeries() = load("Emby TV Shows", EmbyScreen.SERIES) { EmbyApiClient.series(it) }

    private fun load(
        title: String,
        destination: EmbyScreen,
        loader: suspend (EmbySecureStore.Credentials) -> Result<List<EmbyApiClient.MediaEntry>>
    ) {
        val saved = credentials ?: return
        stopPreview()
        screen = destination
        showFullscreenContent()
        heading.text = title
        guideGroupScroller.visibility = View.GONE
        guideDetailsPanel.visibility = View.VISIBLE
        setDetailsPanelHeight(64)
        guideDetailsTitle.text = title
        guideDetailsBody.text = "Highlight a title to see its details"
        guideTimeHeader.visibility = View.GONE
        guideNowLine.visibility = View.GONE
        list.adapter = EntryAdapter(listOf(EmbyApiClient.MediaEntry("", "Loading…", "", "", "", ""))) { }
        lifecycleScope.launch {
            loader(saved)
                .onSuccess { entries ->
                    list.layoutManager = posterLayoutManager()
                    list.adapter = PosterGridAdapter(entries) { entry ->
                        if (entry.type.equals("Series", true)) loadEpisodes(entry) else play(entry)
                    }
                    list.post { list.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus() }
                }
                .onFailure { error ->
                    Toast.makeText(this@EmbyLibraryActivity, error.message ?: "Could not load Emby", Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun loadEpisodes(series: EmbyApiClient.MediaEntry) {
        val saved = credentials ?: return
        screen = EmbyScreen.EPISODES
        showFullscreenContent()
        heading.text = series.name
        guideGroupScroller.visibility = View.GONE
        guideDetailsPanel.visibility = View.VISIBLE
        setDetailsPanelHeight(64)
        guideDetailsTitle.text = series.name
        guideDetailsBody.text = "Highlight an episode to see its details"
        guideTimeHeader.visibility = View.GONE
        guideNowLine.visibility = View.GONE
        lifecycleScope.launch {
            EmbyApiClient.episodes(saved, series.id)
                .onSuccess { entries ->
                    list.layoutManager = posterLayoutManager()
                    list.adapter = PosterGridAdapter(entries) { play(it) }
                    list.post { list.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus() }
                }
                .onFailure { Toast.makeText(this@EmbyLibraryActivity, it.message ?: "Could not load episodes", Toast.LENGTH_LONG).show() }
        }
    }

    private fun play(entry: EmbyApiClient.MediaEntry) {
        val saved = credentials ?: return
        val isLiveChannel = entry.type.equals("TvChannel", true)
        if (isLiveChannel) {
            launchPlayback(entry, EmbyApiClient.streamUrl(saved, entry.id), true)
            return
        }
        lifecycleScope.launch {
            EmbyApiClient.movieStreamUrl(saved, entry.id)
                .onSuccess { url -> launchPlayback(entry, url, false) }
                .onFailure { error ->
                    Toast.makeText(this@EmbyLibraryActivity, error.message ?: "Could not prepare Emby movie", Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun launchPlayback(entry: EmbyApiClient.MediaEntry, url: String, isLiveChannel: Boolean) {
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra("play_url", url)
                .putExtra("media_title", entry.name)
                .putExtra("resume_key", "emby_${entry.id}")
                .putExtra("play_live", isLiveChannel)
                .putExtra("return_to_emby", true)
        )
    }

    private fun showEmbyCategoryHome() {
        stopPreview()
        screen = EmbyScreen.HOME
        categoryRail.visibility = View.VISIBLE
        categoryRail.requestLayout()
        heading.text = "Your Emby Library"
        guideGroupScroller.visibility = View.GONE
        guidePreviewRow.visibility = View.GONE
        guideDetailsPanel.visibility = View.GONE
        guideTimeHeader.visibility = View.GONE
        guideNowLine.visibility = View.GONE
        val tiles = mutableListOf(
            EmbyHomeTile("Live TV", "Your antenna channels and TV guide", "", ::loadLiveTv),
            EmbyHomeTile("Movies", "Your Emby movie collection", "", ::loadMovies),
            EmbyHomeTile("TV Shows", "Your Emby series and episodes", "", ::loadSeries)
        )
        list.layoutManager = androidx.recyclerview.widget.GridLayoutManager(this, 2)
        list.adapter = EmbyHomeAdapter(tiles)
        loadHomeArtwork(tiles)
        liveTvTab.post { liveTvTab.requestFocus() }
    }

    private fun loadHomeArtwork(tiles: MutableList<EmbyHomeTile>) {
        val saved = credentials ?: return
        lifecycleScope.launch {
            EmbyApiClient.movies(saved).onSuccess { movies ->
                tiles.getOrNull(1)?.imageUrl = movies.firstOrNull()?.imageUrl.orEmpty()
                if (screen == EmbyScreen.HOME) list.adapter?.notifyItemChanged(1)
            }
        }
        lifecycleScope.launch {
            EmbyApiClient.series(saved).onSuccess { shows ->
                tiles.getOrNull(2)?.imageUrl = shows.firstOrNull()?.imageUrl.orEmpty()
                if (screen == EmbyScreen.HOME) list.adapter?.notifyItemChanged(2)
            }
        }
        lifecycleScope.launch {
            EmbyApiClient.liveChannels(saved).onSuccess { channels ->
                tiles.getOrNull(0)?.imageUrl = channels.firstOrNull()?.imageUrl.orEmpty()
                if (screen == EmbyScreen.HOME) list.adapter?.notifyItemChanged(0)
            }
        }
    }

    private fun showFullscreenContent() {
        categoryRail.visibility = View.GONE
        categoryRail.requestLayout()
    }

    private fun renderLiveTvGroups() {
        guideGroupButtons.removeAllViews()
        liveGroups.forEach { group ->
            addGuideGroupButton(group.id, group.name) { loadLiveTv(group.id) }
        }
    }

    private fun addGuideGroupButton(groupId: String, label: String, action: () -> Unit) {
        guideGroupButtons.addView(Button(this).apply {
            text = label
            tag = groupId
            id = View.generateViewId()
            isFocusable = true
            isFocusableInTouchMode = true
            isSingleLine = true
            ellipsize = android.text.TextUtils.TruncateAt.MARQUEE
            textSize = 13f
            setTextColor(Color.WHITE)
            isAllCaps = false
            // The heading identifies the loaded group; only remote focus gets a highlight.
            background = roundedBackground(0xFF182231.toInt(), 7f, 0xFF42546A.toInt(), 1)
            val restingBackground = background
            setOnFocusChangeListener { view, focused ->
                view.background = if (focused) focusedGuideBackground() else restingBackground
                if (focused) view.requestRectangleOnScreen(android.graphics.Rect(0, 0, view.width, view.height))
            }
            setOnKeyListener { _, key, event ->
                if (event.action != KeyEvent.ACTION_DOWN) false
                else when (key) {
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                        val next = guideGroupButtons.indexOfChild(this) + if (key == KeyEvent.KEYCODE_DPAD_UP) -1 else 1
                        guideGroupButtons.getChildAt(next)?.requestFocus()
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        (list.adapter as? GuideAdapter)?.restoreFocus()
                        true
                    }
                    else -> false
                }
            }
            setOnClickListener { action() }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(42)).apply {
            bottomMargin = dp(6)
        })
    }

    private fun setDetailsPanelHeight(heightDp: Int) {
        guidePreviewRow.visibility = View.VISIBLE
        val live = screen == EmbyScreen.LIVE
        previewHost.visibility = if (live) View.VISIBLE else View.GONE
        guideDetailsBody.maxLines = if (live) 6 else 2
        guidePreviewRow.layoutParams.height = dp(if (live) 150 else heightDp)
        guidePreviewRow.requestLayout()
    }

    private fun focusActiveCategory(): Boolean {
        val target = guideGroupButtons.findViewWithTag<View>(activeGroupId)
            ?: guideGroupButtons.getChildAt(0)
        return target?.requestFocus() == true
    }

    private fun stopPreview() {
        previewView.player = null
        previewPlayer?.release()
        previewPlayer = null
        previewChannel = null
    }

    private fun preview(entry: EmbyApiClient.MediaEntry) {
        val saved = credentials ?: return
        if (previewChannel?.id == entry.id && previewPlayer != null) return
        stopPreview()
        previewChannel = entry
        previewStatus.text = "Loading ${entry.name}…"
        previewStatus.visibility = View.VISIBLE
        val http = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(EmbyApiClient.playbackHeaders(saved))
            .setAllowCrossProtocolRedirects(true)
        val factory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(this)
            .setDataSourceFactory(http)
        previewPlayer = androidx.media3.exoplayer.ExoPlayer.Builder(this)
            .setMediaSourceFactory(factory).build().also { player ->
                previewView.player = player
                player.addListener(object : androidx.media3.common.Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == androidx.media3.common.Player.STATE_READY) previewStatus.visibility = View.GONE
                    }
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        previewStatus.text = "Preview unavailable. Select another channel to retry."
                        previewStatus.visibility = View.VISIBLE
                    }
                })
                player.setMediaItem(androidx.media3.common.MediaItem.fromUri(EmbyApiClient.streamUrl(saved, entry.id)))
                player.prepare()
                player.playWhenReady = true
            }
    }

    private fun selectLiveChannel(entry: EmbyApiClient.MediaEntry) {
        if (previewChannel?.id == entry.id && previewPlayer != null) {
            setLiveFullscreen(true)
        } else preview(entry)
    }

    private fun setLiveFullscreen(enabled: Boolean) {
        if (enabled == liveFullscreen) return
        if (enabled) guideFocusBeforeFullscreen = currentFocus
        liveFullscreen = enabled
        // Move the view, not the player: retain the stream, decoder, and playback position.
        (previewView.parent as? ViewGroup)?.removeView(previewView)
        (previewStatus.parent as? ViewGroup)?.removeView(previewStatus)
        val target = if (enabled) fullscreenHost else previewHost
        target.addView(previewView, FrameLayout.LayoutParams(-1, -1))
        target.addView(previewStatus, FrameLayout.LayoutParams(-1, -1))
        guideRoot.visibility = if (enabled) View.INVISIBLE else View.VISIBLE
        fullscreenHost.visibility = if (enabled) View.VISIBLE else View.GONE
        if (enabled) fullscreenHost.requestFocus()
        else {
            guideFocusBeforeFullscreen?.takeIf { it.isAttachedToWindow && it.isShown }?.requestFocus()
                ?: focusActiveCategory()
        }
    }

    override fun onStart() {
        super.onStart()
        resumePreviewChannel?.let { entry ->
            resumePreviewChannel = null
            if (screen == EmbyScreen.LIVE) preview(entry)
        }
    }

    override fun onStop() {
        resumePreviewChannel = previewChannel
        if (liveFullscreen) setLiveFullscreen(false)
        stopPreview()
        super.onStop()
    }

    private fun showMediaDetails(entry: EmbyApiClient.MediaEntry) {
        guideDetailsTitle.text = listOf(entry.number, entry.name).filter { it.isNotBlank() }.joinToString("  ")
        guideDetailsBody.text = entry.overview.ifBlank { "Select to play" }
    }

    private fun tabButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 17f
        setTextColor(Color.WHITE)
        isFocusable = true
        background = AppearanceTheme.buttonBackground(this@EmbyLibraryActivity)
        setOnClickListener { action() }
    }.also { button ->
        button.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(62)).apply {
            bottomMargin = dp(12)
        }
    }

    private inner class EntryAdapter(
        private val entries: List<EmbyApiClient.MediaEntry>,
        private val onClick: (EmbyApiClient.MediaEntry) -> Unit
    ) : RecyclerView.Adapter<EntryAdapter.Holder>() {
        inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
            private val group = view as ViewGroup
            val title: TextView = group.getChildAt(0) as TextView
            val description: TextView = group.getChildAt(1) as TextView
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val row = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                isFocusable = true
                isClickable = true
                setPadding(dp(20), dp(13), dp(20), dp(13))
                background = AppearanceTheme.buttonBackground(parent.context)
                addView(TextView(parent.context).apply { textSize = 21f; setTextColor(Color.WHITE) })
                addView(TextView(parent.context).apply { textSize = 15f; setTextColor(0xFFB8C4D8.toInt()); maxLines = 2 })
            }
            return Holder(row)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val entry = entries[position]
            holder.title.text = listOf(entry.number, entry.name).filter { it.isNotBlank() }.joinToString("  ")
            holder.description.text = entry.overview
            holder.itemView.setOnClickListener { if (entry.id.isNotBlank()) onClick(entry) }
        }

        override fun getItemCount(): Int = entries.size
    }

    private fun posterLayoutManager() = object : androidx.recyclerview.widget.GridLayoutManager(this, 6) {
        override fun onRequestChildFocus(parent: RecyclerView, state: RecyclerView.State, child: View, focused: View?): Boolean {
            // Remote navigation below positions the row once; avoid a second automatic smooth scroll.
            return true
        }
    }

    private inner class PosterGridAdapter(
        private val entries: List<EmbyApiClient.MediaEntry>,
        private val onClick: (EmbyApiClient.MediaEntry) -> Unit
    ) : RecyclerView.Adapter<PosterGridAdapter.Holder>() {
        private var pendingPosition: Int? = null
        private var navigationGeneration = 0

        private fun moveFocus(holder: Holder, key: Int): Boolean {
            val from = pendingPosition ?: holder.bindingAdapterPosition
            if (from !in entries.indices) return true
            val columns = 6
            val target = when (key) {
                KeyEvent.KEYCODE_DPAD_LEFT -> if (from % columns > 0) from - 1 else from
                KeyEvent.KEYCODE_DPAD_RIGHT -> if (from % columns < columns - 1 && from + 1 < entries.size) from + 1 else from
                KeyEvent.KEYCODE_DPAD_UP -> if (from >= columns) from - columns else from
                KeyEvent.KEYCODE_DPAD_DOWN -> if ((from / columns + 1) * columns < entries.size) minOf(from + columns, entries.lastIndex) else from
                else -> return false
            }
            if (target == from) return true
            val generation = ++navigationGeneration
            pendingPosition = target
            list.stopScroll()
            val manager = list.layoutManager as? androidx.recyclerview.widget.GridLayoutManager ?: return true
            val visible = manager.findViewByPosition(target)
            if (visible != null) {
                val top = manager.getDecoratedTop(visible)
                val bottom = manager.getDecoratedBottom(visible)
                val shift = when {
                    top < list.paddingTop -> top - list.paddingTop
                    bottom > list.height - list.paddingBottom -> bottom - (list.height - list.paddingBottom)
                    else -> 0
                }
                if (shift != 0) list.scrollBy(0, shift)
            } else {
                val rowHeight = manager.getDecoratedMeasuredHeight(holder.itemView).coerceAtLeast(dp(224))
                val offset = if (target > from) (list.height - list.paddingTop - list.paddingBottom - rowHeight).coerceAtLeast(0) else 0
                manager.scrollToPositionWithOffset(target, offset)
            }
            fun focusWhenReady(attempt: Int) {
                if (list.adapter !== this || generation != navigationGeneration) return
                val targetView = list.findViewHolderForAdapterPosition(target)?.itemView
                if (targetView != null && !list.isComputingLayout) {
                    pendingPosition = null
                    targetView.requestFocus()
                } else if (attempt < 5) list.postOnAnimation { focusWhenReady(attempt + 1) }
                else pendingPosition = null
            }
            list.postOnAnimation { focusWhenReady(0) }
            return true
        }

        inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val poster: ImageView = view.findViewById(R.id.ivPoster)
            val title: TextView = view.findViewById(R.id.tvPosterTitle)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            return Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_poster, parent, false))
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val entry = entries[position]
            holder.title.text = listOf(entry.number, entry.name).filter { it.isNotBlank() }.joinToString("  ")
            holder.title.isSelected = holder.itemView.hasFocus()
            holder.itemView.foreground = AppearanceTheme.posterBackground(holder.itemView.context)
            Glide.with(holder.itemView)
                .load(entry.imageUrl.takeIf { it.isNotBlank() })
                .override(320, 480)
                .centerCrop()
                .dontAnimate()
                .into(holder.poster)
            holder.itemView.setOnFocusChangeListener { view, focused ->
                holder.title.isSelected = focused
                if (focused) showMediaDetails(entry)
                view.alpha = if (focused) 1f else 0.92f
                view.translationZ = if (focused) dp(12).toFloat() else 0f
            }
            holder.itemView.setOnKeyListener { _, key, event ->
                if (key !in listOf(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN)) false
                else if (event.action == KeyEvent.ACTION_DOWN) moveFocus(holder, key) else true
            }
            holder.itemView.setOnClickListener { onClick(entry) }
        }

        override fun onViewRecycled(holder: Holder) {
            Glide.with(holder.itemView).clear(holder.poster)
            super.onViewRecycled(holder)
        }

        override fun getItemCount(): Int = entries.size
    }

    private data class EmbyHomeTile(
        val title: String,
        val subtitle: String,
        var imageUrl: String,
        val action: () -> Unit
    )

    private inner class EmbyHomeAdapter(
        private val tiles: List<EmbyHomeTile>
    ) : RecyclerView.Adapter<EmbyHomeAdapter.Holder>() {
        inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
            private val card = view as FrameLayout
            val art = card.getChildAt(0) as ImageView
            val title = card.getChildAt(2) as TextView
            val subtitle = card.getChildAt(3) as TextView
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val card = FrameLayout(parent.context).apply {
                isFocusable = false
                isClickable = true
                foreground = roundedBackground(0x00000000, 14f, 0xFF40536D.toInt(), 1)
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(250)).apply {
                    setMargins(dp(10), dp(10), dp(10), dp(10))
                }
            }
            card.addView(ImageView(parent.context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(0xFF152238.toInt())
            }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            card.addView(View(parent.context).apply {
                background = GradientDrawable(
                    GradientDrawable.Orientation.BOTTOM_TOP,
                    intArrayOf(0xF3162337.toInt(), 0x24162337)
                )
            }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            card.addView(TextView(parent.context).apply {
                setTextColor(Color.WHITE)
                textSize = 27f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setShadowLayer(6f, 0f, 2f, Color.BLACK)
            }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply {
                leftMargin = dp(20); rightMargin = dp(20); bottomMargin = dp(46)
            })
            card.addView(TextView(parent.context).apply {
                setTextColor(0xFFD6E5F5.toInt())
                textSize = 14f
                maxLines = 1
                setShadowLayer(4f, 0f, 2f, Color.BLACK)
            }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply {
                leftMargin = dp(20); rightMargin = dp(20); bottomMargin = dp(20)
            })
            return Holder(card)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val tile = tiles[position]
            holder.title.text = tile.title
            holder.subtitle.text = tile.subtitle
            Glide.with(holder.itemView)
                .load(tile.imageUrl.takeIf { it.isNotBlank() })
                .centerCrop()
                .into(holder.art)
            holder.itemView.setOnClickListener { tile.action() }
        }

        override fun onViewRecycled(holder: Holder) {
            Glide.with(holder.itemView).clear(holder.art)
            super.onViewRecycled(holder)
        }

        override fun getItemCount(): Int = tiles.size
    }

    private inner class GuideAdapter(
        private val channels: List<EmbyApiClient.GuideChannel>
    ) : RecyclerView.Adapter<GuideAdapter.Holder>() {
        inner class Holder(
            val row: LinearLayout,
            val channelButton: Button,
            val programs: LinearLayout,
            val scroller: HorizontalScrollView
        ) : RecyclerView.ViewHolder(row)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val row = LinearLayout(parent.context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, dp(3))
            }
            val channel = Button(parent.context).apply {
                isFocusable = false
                isClickable = false
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                setTextColor(Color.WHITE)
                textSize = 12f
                maxLines = 2
                background = AppearanceTheme.buttonBackground(parent.context)
            }
            row.addView(channel, LinearLayout.LayoutParams(dp(180), dp(46)).apply { marginEnd = dp(3) })
            val programStrip = LinearLayout(parent.context).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            val scroller = HorizontalScrollView(parent.context).apply {
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                setOnScrollChangeListener { _, scrollX, _, _, _ -> syncGuideScroll(scrollX, this) }
                addView(programStrip)
            }
            row.addView(scroller, LinearLayout.LayoutParams(0, dp(46), 1f))
            return Holder(row, channel, programStrip, scroller)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val guide = channels[position]
            val showNumber = liveGroups.any { it.id == activeGroupId && it.name.equals("Locals", true) }
            holder.channelButton.text = listOf(if (showNumber) guide.channel.number else "", guide.channel.name)
                .filter { it.isNotBlank() }
                .joinToString("  ")
            holder.channelButton.background = roundedBackground(0xFF151B25.toInt(), 7f, 0xFF2B3749.toInt(), 1)
            holder.programs.removeAllViews()
            if (!guideScrollers.contains(holder.scroller)) guideScrollers.add(holder.scroller)
            holder.scroller.scrollTo(guideTimeScroller.scrollX, 0)
            if (guide.programs.isEmpty()) {
                holder.programs.addView(
                    guideButton("No guide information", dp(320), position, null) { selectLiveChannel(guide.channel) }
                )
            } else {
                val firstStart = guide.programs.first().startMs
                val leadingMinutes = ((firstStart - guideTimelineStartMs) / 60_000L).coerceAtLeast(0L)
                if (leadingMinutes > 0L) {
                    holder.programs.addView(View(this@EmbyLibraryActivity), LinearLayout.LayoutParams(minutesToGuidePx(leadingMinutes), dp(46)))
                }
                guide.programs.forEach { program ->
                    val duration = (program.endMs - program.startMs).coerceAtLeast(15L * 60L * 1000L)
                    val width = minutesToGuidePx(duration / 60_000L).coerceAtLeast(dp(90))
                    val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(program.startMs))
                    holder.programs.addView(
                        guideButton("${program.name}\n$time", width, position, program) { selectLiveChannel(guide.channel) }
                    )
                }
            }
        }

        private fun guideButton(
            text: String,
            width: Int,
            rowPosition: Int,
            program: EmbyApiClient.GuideProgram? = null,
            action: () -> Unit
        ) = Button(this@EmbyLibraryActivity).apply {
            this.text = text
            isFocusable = true
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setTextColor(Color.WHITE)
            textSize = 11f
            maxLines = 2
            val isNow = program?.let { System.currentTimeMillis() in it.startMs until it.endMs } == true
            val resting = if (isNow) 0xFF18324B.toInt() else 0xFF151B25.toInt()
            val focusStart = program?.startMs ?: guideTimelineStartMs
            val focusEnd = program?.endMs ?: guideTimelineStartMs + 30L * 60L * 1000L
            tag = GuideFocusTag(focusStart, focusEnd)
            background = roundedBackground(resting, 7f, 0xFF334258.toInt(), 1)
            setOnFocusChangeListener { view, focused ->
                view.background = if (focused) focusedGuideBackground() else roundedBackground(resting, 7f, 0xFF334258.toInt(), 1)
                if (focused) {
                    channels.getOrNull(rowPosition)?.let { channel ->
                        lastGuideFocus[activeGroupId] = channel.channel.id to
                            System.currentTimeMillis().coerceIn(focusStart, maxOf(focusStart, focusEnd - 1))
                    }
                    if (program != null) showGuideDetails(program)
                }
            }
            var favoriteMenuOpened = false
            fun openFavoriteMenu() {
                if (!favoriteMenuOpened) {
                    favoriteMenuOpened = true
                    channels.getOrNull(rowPosition)?.channel?.let { showFavoriteAction(it) }
                }
            }
            setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                    if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) favoriteMenuOpened = false
                    if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount > 0) {
                        openFavoriteMenu()
                        return@setOnKeyListener true
                    }
                    if (event.action == KeyEvent.ACTION_UP && favoriteMenuOpened) {
                        isPressed = false
                        return@setOnKeyListener true
                    }
                }
                if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                val anchorTime = guideTimeAtFixedLine()
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        val strip = parent as? ViewGroup
                        if (strip?.indexOfChild(this) == 0 || guideTimeScroller.scrollX == 0) focusActiveCategory() else false
                    }
                    KeyEvent.KEYCODE_DPAD_UP -> focusProgramAt(rowPosition - 1, anchorTime)
                    KeyEvent.KEYCODE_DPAD_DOWN -> focusProgramAt(rowPosition + 1, anchorTime)
                    else -> false
                }
            }
            setOnLongClickListener {
                openFavoriteMenu()
                true
            }
            setOnClickListener { action() }
            layoutParams = LinearLayout.LayoutParams(width, dp(46)).apply { marginEnd = dp(2) }
        }

        override fun getItemCount(): Int = channels.size

        fun restoreFocus(): Boolean {
            val remembered = lastGuideFocus[activeGroupId]
            val rememberedIndex = channels.indexOfFirst { it.channel.id == remembered?.first }
            val playingIndex = channels.indexOfFirst { it.channel.id == previewChannel?.id }
            val row = when {
                rememberedIndex >= 0 -> rememberedIndex
                playingIndex >= 0 -> playingIndex
                else -> 0
            }
            return focusProgramAt(row, if (rememberedIndex >= 0) remembered!!.second else System.currentTimeMillis())
        }

        fun focusProgramAt(rowPosition: Int, anchorTime: Long): Boolean {
            if (rowPosition !in channels.indices) return true
            val lockedScrollX = guideTimeScroller.scrollX
            list.scrollToPosition(rowPosition)
            list.postDelayed({
                if (list.adapter !== this || screen != EmbyScreen.LIVE || liveFullscreen) return@postDelayed
                val holder = list.findViewHolderForAdapterPosition(rowPosition) as? Holder
                    ?: return@postDelayed
                val candidates = (0 until holder.programs.childCount)
                    .map { holder.programs.getChildAt(it) }
                    .filter { it.isFocusable }
                val target = candidates.firstOrNull { view ->
                    val range = view.tag as? GuideFocusTag ?: return@firstOrNull false
                    anchorTime in range.startMs until range.endMs
                } ?: candidates.minByOrNull { view ->
                    val range = view.tag as? GuideFocusTag
                    if (range == null) Long.MAX_VALUE
                    else kotlin.math.abs(((range.startMs + range.endMs) / 2L) - anchorTime)
                }
                target?.requestFocus()
                list.post { setGuideScrollX(lockedScrollX) }
                list.postDelayed({ setGuideScrollX(lockedScrollX) }, 90L)
            }, 40L)
            return true
        }
    }

    private data class GuideFocusTag(val startMs: Long, val endMs: Long)

    private fun renderGuideTimeHeader() {
        val ticks = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val formatter = SimpleDateFormat("h:mm a", Locale.getDefault())
        repeat(9) { index ->
            addGuideTimeTick(ticks, formatter.format(Date(guideTimelineStartMs + index * 30L * 60L * 1000L)))
        }
        guideTimeScroller.removeAllViews()
        guideTimeScroller.addView(ticks)
    }

    private fun addGuideTimeTick(parent: LinearLayout, label: String) {
        parent.addView(TextView(this).apply {
            text = label
            textSize = 12f
            setTextColor(0xFFE7EEF8.toInt())
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setPadding(dp(14), 0, 0, 0)
            background = roundedBackground(0xFF0D1118.toInt(), 4f, 0xFF263245.toInt(), 1)
        }, LinearLayout.LayoutParams(GUIDE_HALF_HOUR_WIDTH_DP.let(::dp), dp(28)).apply { marginEnd = dp(2) })
    }

    private fun minutesToGuidePx(minutes: Long): Int =
        ((minutes.coerceAtLeast(0L) / 30f) * dp(GUIDE_HALF_HOUR_WIDTH_DP)).toInt()

    private fun syncGuideScroll(scrollX: Int, source: HorizontalScrollView) {
        if (syncingGuideScroll) return
        syncingGuideScroll = true
        if (source !== guideTimeScroller) guideTimeScroller.scrollTo(scrollX, 0)
        guideScrollers.forEach { if (it !== source && it.scrollX != scrollX) it.scrollTo(scrollX, 0) }
        syncingGuideScroll = false
    }

    private fun setGuideScrollX(scrollX: Int) {
        if (syncingGuideScroll) return
        syncingGuideScroll = true
        guideTimeScroller.scrollTo(scrollX, 0)
        guideScrollers.forEach { if (it.scrollX != scrollX) it.scrollTo(scrollX, 0) }
        syncingGuideScroll = false
    }

    private fun scrollGuideToTime(timeMs: Long) {
        val minutes = ((timeMs - guideTimelineStartMs) / 60_000L).coerceAtLeast(0L)
        setGuideScrollX(minutesToGuidePx(minutes))
    }

    private fun guideTimeAtFixedLine(): Long {
        val minutes = (guideTimeScroller.scrollX.toFloat() / dp(GUIDE_HALF_HOUR_WIDTH_DP)) * 30f
        return guideTimelineStartMs + (minutes * 60_000L).toLong()
    }

    private fun showGuideDetails(program: EmbyApiClient.GuideProgram) {
        val formatter = SimpleDateFormat("h:mm a", Locale.getDefault())
        guideDetailsTitle.text = program.name
        val range = "${formatter.format(Date(program.startMs))} – ${formatter.format(Date(program.endMs))}"
        guideDetailsBody.text = if (program.overview.isBlank()) range else "$range  •  ${program.overview}"
    }

    private fun focusedGuideBackground(): GradientDrawable =
        roundedBackground(0xFF1778C8.toInt(), 7f, Color.WHITE, 2)

    private fun roundedBackground(fill: Int, radiusDp: Float, stroke: Int, strokeDp: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(fill)
        cornerRadius = dp(radiusDp.toInt()).toFloat()
        setStroke(dp(strokeDp), stroke)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val GUIDE_HALF_HOUR_WIDTH_DP = 230
    }

    private enum class EmbyScreen { HOME, LIVE, MOVIES, SERIES, EPISODES }
}
