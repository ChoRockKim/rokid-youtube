package com.chorockkim.rokidyoutube.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.chorockkim.rokidyoutube.data.VideoItem
import com.chorockkim.rokidyoutube.data.WatchHistory
import com.chorockkim.rokidyoutube.data.YouTubeRepository
import com.chorockkim.rokidyoutube.player.StreamSelector
import com.chorockkim.rokidyoutube.player.YoutubeMediaSources
import com.chorockkim.rokidyoutube.ui.GlassesInput.Action
import com.chorockkim.rokidyoutube.ui.Hud.dp
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.StyledPlayerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.stream.StreamInfo

/**
 * Player filling the HUD band. Controls (one row of buttons + progress) appear on tap and hide
 * after a few seconds. Hidden: swipe = seek ±10s, tap = show controls, back = close.
 * Shown: swipe walks the buttons, tap presses, back hides the controls.
 */
class PlayerScreen(
    context: Context,
    private val navigator: Navigator,
    private val scope: CoroutineScope,
    private val video: VideoItem,
    /** Watch history for "추천" / "이어보기"; null in snapshot tests. */
    private val history: WatchHistory? = null,
    /** null in snapshot tests: no ExoPlayer, just the layout. */
    private val playerFactory: (() -> ExoPlayer)? = { ExoPlayer.Builder(context).build() }
) : Screen {
    override val view = FrameLayout(context).apply {
        setBackgroundColor(Color.BLACK)
        keepScreenOn = true
    }

    internal val videoView = StyledPlayerView(context).apply {
        useController = false
        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        setShutterBackgroundColor(Color.BLACK)
        setKeepContentOnPlayerReset(true)
    }
    private val status = Hud.text(context, 15f, Hud.GREY).apply { gravity = Gravity.CENTER }

    private val controls = FrameLayout(context)
    private val titleView = Hud.text(context, 14f, bold = true).apply {
        text = video.title
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        setPadding(dp(12), dp(8), dp(12), dp(14))
        background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Hud.SCRIM, Color.TRANSPARENT)
        )
    }
    private val position = Hud.text(context, 12f)
    private val duration = Hud.text(context, 12f)
    val progress = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
        max = 1000
        progressTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#E62117"))
        progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.GRAY)
    }

    private val playPause = Hud.button(context, "일시정지", 12f)
    private val qualityButton = Hud.button(context, "480p", 12f)
    private val rail: List<Pair<TextView, () -> Unit>> = listOf(
        playPause to ::togglePlay,
        Hud.button(context, "-10초", 12f) to { seekBy(-10_000) },
        Hud.button(context, "+10초", 12f) to { seekBy(10_000) },
        qualityButton to ::nextQuality,
        Hud.button(context, "닫기", 12f) to { navigator.back() }
    )
    private var railCursor = 0

    private var player: ExoPlayer? = null
    private val mediaSources = YoutubeMediaSources(context)
    private var info: StreamInfo? = null
    private var maxHeight = StreamSelector.DEFAULT_MAX_HEIGHT
    private var availableHeights: List<Int> = emptyList()
    private var loadJob: Job? = null

    private val handler = Handler(Looper.getMainLooper())
    private val hideControls = Runnable { setControlsVisible(false) }
    private var ticks = 0
    private val tick = object : Runnable {
        override fun run() {
            updateProgress()
            // Save the position every ~5s so "이어보기" survives the app being killed.
            if (++ticks % 10 == 0) savePosition()
            handler.postDelayed(this, 500)
        }
    }

    init {
        view.addView(videoView, FrameLayout.LayoutParams(-1, -1))
        view.addView(status, FrameLayout.LayoutParams(-1, -1))

        controls.addView(titleView, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))
        val bottom = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(16), dp(8), 0)
            background = GradientDrawable(
                GradientDrawable.Orientation.BOTTOM_TOP, intArrayOf(Hud.SCRIM, Color.TRANSPARENT)
            )
        }
        val buttons = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        rail.forEachIndexed { i, (button, _) ->
            button.setPadding(dp(2), 0, dp(2), 0)
            button.setBackgroundColor(Color.TRANSPARENT)
            button.background = Hud.outline(context, fill = Hud.SCRIM)
            buttons.addView(button, LinearLayout.LayoutParams(0, dp(34), 1f).apply {
                if (i > 0) marginStart = dp(4)
            })
        }
        bottom.addView(buttons, LinearLayout.LayoutParams(-1, -2))
        val seekRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(position)
            addView(progress, LinearLayout.LayoutParams(0, dp(12), 1f).apply {
                setMargins(dp(8), 0, dp(8), 0)
            })
            addView(duration)
        }
        // No bottom margin: the band itself already sits on the bottom edge.
        bottom.addView(seekRow, LinearLayout.LayoutParams(-1, dp(24)))
        controls.addView(bottom, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        view.addView(controls, FrameLayout.LayoutParams(-1, -1))

        moveRail(0)
        renderTimes(0, 0)
    }

    override fun onShown() {
        if (player == null && playerFactory != null) {
            player = playerFactory.invoke().also { p ->
                videoView.player = p
                p.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        playPause.text = if (isPlaying) "일시정지" else "재생"
                        if (isPlaying) scheduleHide() else setControlsVisible(true)
                    }

                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_BUFFERING) setStatus("버퍼링…")
                        if (state == Player.STATE_READY) setStatus(null)
                        if (state == Player.STATE_ENDED) setControlsVisible(true)
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        setStatus("재생 오류\n${error.errorCodeName}")
                        setControlsVisible(true)
                    }
                })
            }
            load()
        }
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    override fun onHidden() {
        handler.removeCallbacksAndMessages(null)
        savePosition()
        player?.pause()
    }

    override fun onDestroy() {
        savePosition()
        loadJob?.cancel()
        handler.removeCallbacksAndMessages(null)
        videoView.player = null
        player?.release()
        player = null
    }

    private fun load() {
        setStatus("불러오는 중…")
        loadJob = scope.launch {
            try {
                val streamInfo = YouTubeRepository.stream(video.url)
                info = streamInfo
                titleView.text = streamInfo.name
                val resumeAt = history?.all?.firstOrNull { it.video.url == video.url }
                    ?.takeIf { it.inProgress }?.positionMs ?: 0L
                history?.recordWatch(video, YouTubeRepository.related(streamInfo))
                play(streamInfo, startAtMs = resumeAt)
            } catch (e: Exception) {
                setStatus("영상을 불러오지 못했어요\n${e.javaClass.simpleName}")
            }
        }
    }

    private fun play(streamInfo: StreamInfo, startAtMs: Long) {
        val p = player ?: return
        val selection = StreamSelector.select(streamInfo, maxHeight)
        availableHeights = selection.availableHeights
        qualityButton.text = selection.video?.let { "${StreamSelector.heightOf(it)}p" } ?: "음성"
        try {
            p.setMediaSource(mediaSources.build(streamInfo, selection), startAtMs)
            p.prepare()
            p.playWhenReady = true
        } catch (e: YoutubeMediaSources.ResolverException) {
            setStatus("재생할 수 있는 형식이 없어요")
        }
    }

    private fun togglePlay() {
        val p = player ?: return
        if (p.playbackState == Player.STATE_ENDED) p.seekTo(0)
        p.playWhenReady = !p.playWhenReady
    }

    private fun seekBy(deltaMs: Long) {
        val p = player ?: return
        val target = (p.currentPosition + deltaMs).coerceAtLeast(0)
        p.seekTo(if (p.duration > 0) target.coerceAtMost(p.duration) else target)
        updateProgress()
    }

    /** Steps down through the available heights and wraps to the highest one. */
    private fun nextQuality() {
        val streamInfo = info ?: return
        if (availableHeights.size < 2) return
        val current = availableHeights.firstOrNull { it <= maxHeight } ?: availableHeights.last()
        val index = availableHeights.indexOf(current)
        maxHeight = availableHeights[(index + 1) % availableHeights.size]
        play(streamInfo, startAtMs = player?.currentPosition ?: 0L)
    }

    private fun setStatus(text: String?) {
        status.text = text
        status.visibility = if (text == null) View.GONE else View.VISIBLE
    }

    fun setControlsVisible(visible: Boolean) {
        controls.visibility = if (visible) View.VISIBLE else View.GONE
        handler.removeCallbacks(hideControls)
        if (visible) scheduleHide()
    }

    private fun scheduleHide() {
        handler.removeCallbacks(hideControls)
        if (player?.isPlaying == true) handler.postDelayed(hideControls, HIDE_AFTER_MS)
    }

    private fun moveRail(index: Int) {
        railCursor = (index + rail.size) % rail.size
        rail.forEachIndexed { i, (button, _) -> button.isSelected = i == railCursor }
    }

    private fun savePosition() {
        val p = player ?: return
        if (info == null) return
        history?.updatePosition(video.url, p.currentPosition, p.duration.coerceAtLeast(0))
    }

    private fun updateProgress() {
        val p = player ?: return
        renderTimes(p.currentPosition, p.duration.coerceAtLeast(0))
    }

    fun renderTimes(positionMs: Long, durationMs: Long) {
        position.text = Hud.formatDuration(positionMs / 1000).ifEmpty { "0:00" }
        duration.text = Hud.formatDuration(durationMs / 1000).ifEmpty { "0:00" }
        progress.progress = if (durationMs > 0) (positionMs * 1000 / durationMs).toInt() else 0
    }

    override fun onAction(action: Action): Boolean {
        val shown = controls.visibility == View.VISIBLE
        return when (action) {
            Action.NEXT, Action.PREVIOUS -> {
                if (shown) {
                    moveRail(railCursor + if (action == Action.NEXT) 1 else -1)
                    scheduleHide()
                } else {
                    seekBy(if (action == Action.NEXT) 10_000 else -10_000)
                    setControlsVisible(true)
                }
                true
            }
            Action.SELECT -> {
                if (shown) rail[railCursor].second() else moveRail(0)
                setControlsVisible(true)
                true
            }
            Action.BACK -> if (shown && player?.isPlaying == true) {
                setControlsVisible(false)
                true
            } else {
                false
            }
            else -> false
        }
    }

    private companion object {
        const val HIDE_AFTER_MS = 4_000L
    }
}
