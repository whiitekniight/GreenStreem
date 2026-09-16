package com.example.greenstreem

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EmbyLibraryActivity : AppCompatActivity() {
    private lateinit var list: RecyclerView
    private lateinit var heading: TextView
    private lateinit var guideDetailsTitle: TextView
    private lateinit var guideDetailsBody: TextView
    private lateinit var guideDetailsPanel: LinearLayout
    private lateinit var guideTimeHeader: LinearLayout
    private lateinit var guideTimeScroller: HorizontalScrollView
    private lateinit var guideNowLine: View
    private lateinit var categoryRail: LinearLayout
    private lateinit var liveTvTab: Button
    private var credentials: EmbySecureStore.Credentials? = null
    private var liveGuideFullscreen = false
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
        contentPanel.addView(guideDetailsPanel, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(50)).apply {
            bottomMargin = dp(5)
        })
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
        setContentView(root)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (liveGuideFullscreen) {
                    showEmbyCategoryRail()
                    return
                }
                getSharedPreferences("iptv_prefs", MODE_PRIVATE)
                    .edit()
                    .putBoolean("return_to_live_guide_after_emby", true)
                    .apply()
                finish()
            }
        })
        loadLiveTv()
    }

    private fun loadLiveTv() {
        val saved = credentials ?: return
        liveGuideFullscreen = true
        categoryRail.visibility = View.GONE
        heading.text = "Emby Live TV Guide"
        guideTimeHeader.visibility = View.VISIBLE
        guideNowLine.visibility = View.VISIBLE
        guideDetailsTitle.text = "Live TV"
        guideDetailsBody.text = "Select a program to see details"
        guideScrollers.clear()
        list.adapter = EntryAdapter(listOf(EmbyApiClient.MediaEntry("", "Loading guide…", "", "", "", ""))) { }
        lifecycleScope.launch {
            EmbyApiClient.liveGuide(saved)
                .onSuccess { channels ->
                    guideTimelineStartMs = (System.currentTimeMillis() / (30L * 60L * 1000L)) * (30L * 60L * 1000L)
                    renderGuideTimeHeader()
                    val adapter = GuideAdapter(channels)
                    list.adapter = adapter
                    list.post {
                        val now = System.currentTimeMillis()
                        val firstCurrentProgramStart = channels.firstOrNull()
                            ?.programs
                            ?.firstOrNull { now in it.startMs until it.endMs }
                            ?.startMs
                            ?: guideTimelineStartMs
                        scrollGuideToTime(firstCurrentProgramStart.coerceAtLeast(guideTimelineStartMs))
                        adapter.focusProgramAt(0, now)
                    }
                }
                .onFailure { error ->
                    Toast.makeText(this@EmbyLibraryActivity, error.message ?: "Could not load Emby guide", Toast.LENGTH_LONG).show()
                }
        }
    }
    private fun loadMovies() = load("Emby Movies") { EmbyApiClient.movies(it) }
    private fun loadSeries() = load("Emby Series") { EmbyApiClient.series(it) }

    private fun load(
        title: String,
        loader: suspend (EmbySecureStore.Credentials) -> Result<List<EmbyApiClient.MediaEntry>>
    ) {
        val saved = credentials ?: return
        liveGuideFullscreen = false
        categoryRail.visibility = View.VISIBLE
        heading.text = title
        guideTimeHeader.visibility = View.GONE
        guideNowLine.visibility = View.GONE
        list.adapter = EntryAdapter(listOf(EmbyApiClient.MediaEntry("", "Loading…", "", "", "", ""))) { }
        lifecycleScope.launch {
            loader(saved)
                .onSuccess { entries ->
                    list.adapter = EntryAdapter(entries) { entry ->
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
        liveGuideFullscreen = false
        categoryRail.visibility = View.VISIBLE
        heading.text = series.name
        guideTimeHeader.visibility = View.GONE
        guideNowLine.visibility = View.GONE
        lifecycleScope.launch {
            EmbyApiClient.episodes(saved, series.id)
                .onSuccess { entries ->
                    list.adapter = EntryAdapter(entries) { play(it) }
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

    private fun showEmbyCategoryRail() {
        liveGuideFullscreen = false
        categoryRail.visibility = View.VISIBLE
        categoryRail.requestLayout()
        liveTvTab.post { liveTvTab.requestFocus() }
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
            holder.channelButton.text = listOf(guide.channel.number, guide.channel.name)
                .filter { it.isNotBlank() }
                .joinToString("  ")
            holder.channelButton.background = roundedBackground(0xFF151B25.toInt(), 7f, 0xFF2B3749.toInt(), 1)
            holder.programs.removeAllViews()
            if (!guideScrollers.contains(holder.scroller)) guideScrollers.add(holder.scroller)
            holder.scroller.scrollTo(guideTimeScroller.scrollX, 0)
            if (guide.programs.isEmpty()) {
                holder.programs.addView(
                    guideButton("No guide information", dp(320), position, null) { play(guide.channel) }
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
                        guideButton("${program.name}\n$time", width, position, program) { play(guide.channel) }
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
                if (focused && program != null) showGuideDetails(program)
            }
            setOnKeyListener { _, keyCode, event ->
                if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                val anchorTime = guideTimeAtFixedLine()
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> focusProgramAt(rowPosition - 1, anchorTime)
                    KeyEvent.KEYCODE_DPAD_DOWN -> focusProgramAt(rowPosition + 1, anchorTime)
                    else -> false
                }
            }
            setOnClickListener { action() }
            layoutParams = LinearLayout.LayoutParams(width, dp(46)).apply { marginEnd = dp(2) }
        }

        override fun getItemCount(): Int = channels.size

        fun focusProgramAt(rowPosition: Int, anchorTime: Long): Boolean {
            if (rowPosition !in channels.indices) return true
            val lockedScrollX = guideTimeScroller.scrollX
            list.scrollToPosition(rowPosition)
            list.postDelayed({
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
}
