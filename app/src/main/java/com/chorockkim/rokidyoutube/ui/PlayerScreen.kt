package com.chorockkim.rokidyoutube.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.TextureView
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.chorockkim.rokidyoutube.data.VideoItem
import com.chorockkim.rokidyoutube.data.WatchHistory
import com.chorockkim.rokidyoutube.data.YouTubeRepository
import com.chorockkim.rokidyoutube.player.FineVolume
import com.chorockkim.rokidyoutube.player.PrivacyFilter
import com.chorockkim.rokidyoutube.player.StreamSelector
import com.chorockkim.rokidyoutube.player.YoutubeMediaSources
import com.chorockkim.rokidyoutube.ui.GlassesInput.Action
import com.chorockkim.rokidyoutube.ui.Hud.dp
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.video.VideoSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.stream.StreamInfo

/**
 * Player filling the HUD band. Controls (one row of buttons + progress) appear only on a tap and
 * hide after a few seconds. Hidden: swipe = seek ±10s (small time pill, no controls), tap = show
 * controls, back = close.
 * Shown: swipe walks the buttons, tap presses, back hides the controls.
 * [크기] / [가림] / [볼륨] open an adjust panel: front/back swipes change the video size (10%
 * steps, 20-100%), the privacy filter (see [PrivacyFilter]) or the volume (5% steps, see
 * [FineVolume]); tap or back closes it. The volume keys
 * also change the volume in 5% steps, and DPAD up/down (if the device sends them) the size.
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
    }

    /**
     * The video picture: a TextureView (not a SurfaceView) so the "가림" color filter can be
     * applied to it, inside a frame that keeps the video's aspect ratio.
     */
    internal val videoView = AspectRatioFrameLayout(context).apply {
        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
    }
    private val textureView = TextureView(context)
    private val status = Hud.text(context, 15f, Hud.GREY).apply { gravity = Gravity.CENTER }

    /** Small "+10초 · 2:13 / 3:33" pill after a swipe-seek, instead of opening the controls. */
    private val seekToast = Hud.text(context, 12f, bold = true).apply {
        setPadding(dp(10), dp(3), dp(10), dp(3))
        background = GradientDrawable().apply {
            setColor(Hud.SCRIM)
            cornerRadius = dp(10).toFloat()
        }
        visibility = View.GONE
    }
    private val hideSeekToast = Runnable { seekToast.visibility = View.GONE }

    private val prefs = context.getSharedPreferences("player", Context.MODE_PRIVATE)
    /** Video size in percent of the band, kept across videos and restarts. */
    private var sizePercent = prefs.getInt(KEY_SIZE, MAX_SIZE).coerceIn(MIN_SIZE, MAX_SIZE)
    /** "가림" level 0 (off) .. [PrivacyFilter.MAX_LEVEL], kept across videos and restarts. */
    private var privacyLevel = prefs.getInt(KEY_PRIVACY, 0).coerceIn(0, PrivacyFilter.MAX_LEVEL)
    private val volume: FineVolume? = if (playerFactory != null) FineVolume(context) else null

    /** What front/back swipes change while an adjust panel is open. */
    private enum class Adjust { NONE, SIZE, PRIVACY, VOLUME }
    private var adjust = Adjust.NONE

    // Centered adjust panel: "화면 크기" / "80%" / hint.
    private val adjustTitle = Hud.text(context, 12f, Hud.GREY).apply { gravity = Gravity.CENTER }
    private val adjustValue = Hud.text(context, 26f, Hud.GREEN, bold = true).apply { gravity = Gravity.CENTER }
    private val adjustHint = Hud.text(context, 11f, Hud.GREY).apply {
        gravity = Gravity.CENTER
        setLineSpacing(context.dp(2).toFloat(), 1f)
    }
    private val adjustPanel = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(18), dp(8), dp(18), dp(10))
        minimumWidth = dp(150)
        background = GradientDrawable().apply {
            setColor(Hud.SCRIM)
            setStroke(dp(1), Hud.DIM_GREEN)
            cornerRadius = dp(8).toFloat()
        }
        addView(adjustTitle, LinearLayout.LayoutParams(-2, -2))
        addView(adjustValue, LinearLayout.LayoutParams(-2, -2).apply { setMargins(0, dp(4), 0, dp(4)) })
        addView(adjustHint, LinearLayout.LayoutParams(-2, -2))
        visibility = View.GONE
    }
    private val hideAdjustPanel = Runnable { if (adjust == Adjust.NONE) adjustPanel.visibility = View.GONE }

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

    private val playPause = Hud.button(context, "멈춤", 12f)
    private val qualityButton = Hud.button(context, "480p", 12f)
    private val rail: List<Pair<TextView, () -> Unit>> = listOf(
        playPause to ::togglePlay,
        Hud.button(context, "크기", 12f) to { startAdjust(Adjust.SIZE) },
        Hud.button(context, "가림", 12f) to { startAdjust(Adjust.PRIVACY) },
        Hud.button(context, "볼륨", 12f) to { startAdjust(Adjust.VOLUME) },
        qualityButton to ::nextQuality,
        Hud.button(context, "닫기", 12f) to { navigator.back() },
        // Red so it is not mistaken for 닫기 (which only closes the player).
        Hud.button(context, "종료", 12f).apply {
            setTextColor(
                android.content.res.ColorStateList(
                    arrayOf(intArrayOf(android.R.attr.state_selected), intArrayOf()),
                    intArrayOf(Hud.GREEN, Hud.RED)
                )
            )
        } to { navigator.exitApp() }
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
        videoView.addView(textureView, FrameLayout.LayoutParams(-1, -1))
        view.addView(videoView, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
        applyPrivacy()
        view.addView(status, FrameLayout.LayoutParams(-1, -1))
        applySize()

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
                if (i > 0) marginStart = dp(3)
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
        view.addView(adjustPanel, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        view.addView(seekToast, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = dp(10)
        })

        moveRail(0)
        renderTimes(0, 0)
    }

    override fun onShown() {
        // Coming back to a player that was paused (by the user, or when the app went to the
        // background): it stays paused, so the display may time out.
        player?.let { userPaused = !it.playWhenReady }
        if (player == null && playerFactory != null) {
            player = playerFactory.invoke().also { p ->
                p.setVideoTextureView(textureView)
                p.volume = volume?.gain ?: 1f
                p.addListener(object : Player.Listener {
                    override fun onVideoSizeChanged(size: VideoSize) {
                        if (size.width > 0 && size.height > 0) {
                            videoView.setAspectRatio(size.width * size.pixelWidthHeightRatio / size.height)
                        }
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        playPause.text = if (isPlaying) "멈춤" else "재생"
                        // Controls only ever open on a tap; a pause (incl. buffering after a seek)
                        // just keeps them open if they already are.
                        if (isPlaying) scheduleHide() else handler.removeCallbacks(hideControls)
                    }

                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_BUFFERING) setStatus("버퍼링…")
                        if (state == Player.STATE_READY) setStatus(null)
                        // No controls pop up at the end either; just say so.
                        if (state == Player.STATE_ENDED) setStatus("끝났어요\n탭하면 메뉴")
                        updateScreenOn()
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        setStatus("재생 오류\n${error.errorCodeName}")
                    }
                })
            }
            load()
        }
        handler.removeCallbacks(tick)
        handler.post(tick)
        updateScreenOn()
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
        player?.clearVideoTextureView(textureView)
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
        userPaused = !p.playWhenReady
        updateScreenOn()
    }

    /** Paused with [멈춤] (or ended): let the display time out. Loading/buffering keeps it on. */
    private var userPaused = false

    private fun updateScreenOn() {
        val ended = player?.playbackState == Player.STATE_ENDED
        navigator.setKeepScreenOn(!userPaused && !ended)
    }

    private fun seekBy(deltaMs: Long) {
        val p = player ?: return
        val target = (p.currentPosition + deltaMs).coerceAtLeast(0)
        p.seekTo(if (p.duration > 0) target.coerceAtMost(p.duration) else target)
        updateProgress()
    }

    private fun showSeekToast(label: String) {
        val p = player
        seekToast.text = if (p == null) label else {
            val pos = Hud.formatDuration(p.currentPosition / 1000).ifEmpty { "0:00" }
            val dur = Hud.formatDuration(p.duration.coerceAtLeast(0) / 1000)
            if (dur.isEmpty()) "$label · $pos" else "$label · $pos / $dur"
        }
        seekToast.visibility = View.VISIBLE
        handler.removeCallbacks(hideSeekToast)
        handler.postDelayed(hideSeekToast, 1_000)
    }

    /** For snapshots. */
    internal fun showSeekToastForPreview(text: String) {
        seekToast.text = text
        seekToast.visibility = View.VISIBLE
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

    private fun resizeBy(deltaPercent: Int) {
        val next = (sizePercent + deltaPercent).coerceIn(MIN_SIZE, MAX_SIZE)
        if (next != sizePercent) {
            sizePercent = next
            prefs.edit().apply {
                putInt(KEY_SIZE, next)
                apply()
            }
            applySize()
        }
    }

    private fun changePrivacy(delta: Int) {
        val next = (privacyLevel + delta).coerceIn(0, PrivacyFilter.MAX_LEVEL)
        if (next == privacyLevel) return
        privacyLevel = next
        prefs.edit().apply {
            putInt(KEY_PRIVACY, next)
            apply()
        }
        applyPrivacy()
    }

    /** Color filter on the video layer only; controls and text stay as they are. */
    private fun applyPrivacy() {
        val filter = PrivacyFilter.colorFilter(privacyLevel)
        textureView.setLayerPaint(filter?.let { android.graphics.Paint().apply { colorFilter = it } })
    }

    private fun changeVolume(steps: Int) {
        val v = volume ?: return
        v.change(steps)
        player?.volume = v.gain
    }

    /** Opens the adjust panel; front/back swipes then change the value, tap or back closes it. */
    private fun startAdjust(mode: Adjust) {
        adjust = mode
        setControlsVisible(false)
        renderAdjust(mode, hint = true)
    }

    private fun endAdjust() {
        adjust = Adjust.NONE
        adjustPanel.visibility = View.GONE
    }

    private fun renderAdjust(mode: Adjust, hint: Boolean) {
        adjustTitle.text = when (mode) {
            Adjust.SIZE -> "화면 크기"
            Adjust.PRIVACY -> "가림 (앞사람에게 덜 보이게)"
            else -> "볼륨"
        }
        adjustValue.text = when (mode) {
            Adjust.SIZE -> "$sizePercent%"
            Adjust.PRIVACY -> if (privacyLevel == 0) "끔" else "$privacyLevel / ${PrivacyFilter.MAX_LEVEL}"
            else -> "${volume?.percent ?: 100}%"
        }
        adjustHint.text = "앞뒤로 쓸어서 조절\n탭하면 완료"
        adjustHint.visibility = if (hint) View.VISIBLE else View.GONE
        adjustPanel.visibility = View.VISIBLE
        handler.removeCallbacks(hideAdjustPanel)
        // A quick change from the volume keys (no adjust mode) shows the value briefly.
        if (adjust == Adjust.NONE) handler.postDelayed(hideAdjustPanel, 1_200)
    }

    /** For snapshots: show the adjust panel as it looks after tapping [크기]. */
    internal fun showSizeAdjustForPreview() = startAdjust(Adjust.SIZE)

    /** For snapshots: the "가림" panel at [level]. */
    internal fun showPrivacyAdjustForPreview(level: Int) {
        privacyLevel = level
        startAdjust(Adjust.PRIVACY)
    }

    /** Scales only the video picture around the band's center; controls keep their size. */
    private fun applySize() {
        val scale = sizePercent / 100f
        videoView.scaleX = scale
        videoView.scaleY = scale
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
        if (action == Action.VOLUME_UP || action == Action.VOLUME_DOWN) {
            changeVolume(if (action == Action.VOLUME_UP) 1 else -1)
            renderAdjust(Adjust.VOLUME, hint = adjust == Adjust.VOLUME)
            return true
        }
        if (adjust != Adjust.NONE) {
            when (action) {
                Action.NEXT, Action.PREVIOUS, Action.UP, Action.DOWN -> {
                    val up = action == Action.NEXT || action == Action.UP
                    when (adjust) {
                        Adjust.SIZE -> resizeBy(if (up) SIZE_STEP else -SIZE_STEP)
                        Adjust.PRIVACY -> changePrivacy(if (up) 1 else -1)
                        else -> changeVolume(if (up) 1 else -1)
                    }
                    renderAdjust(adjust, hint = true)
                }
                Action.SELECT, Action.BACK -> endAdjust()
                else -> return false
            }
            return true
        }
        val shown = controls.visibility == View.VISIBLE
        return when (action) {
            Action.NEXT, Action.PREVIOUS -> {
                if (shown) {
                    moveRail(railCursor + if (action == Action.NEXT) 1 else -1)
                    scheduleHide()
                } else {
                    seekBy(if (action == Action.NEXT) 10_000 else -10_000)
                    showSeekToast(if (action == Action.NEXT) "+10초" else "-10초")
                }
                true
            }
            Action.UP, Action.DOWN -> {
                resizeBy(if (action == Action.UP) SIZE_STEP else -SIZE_STEP)
                renderAdjust(Adjust.SIZE, hint = false)
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
        const val KEY_SIZE = "videoSizePercent"
        const val KEY_PRIVACY = "privacyLevel"
        const val MIN_SIZE = 20
        const val MAX_SIZE = 100
        const val SIZE_STEP = 10
    }
}
