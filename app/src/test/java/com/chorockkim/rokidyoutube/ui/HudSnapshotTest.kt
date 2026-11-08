package com.chorockkim.rokidyoutube.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import com.chorockkim.rokidyoutube.data.VideoItem
import com.chorockkim.rokidyoutube.ui.Hud.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test

/**
 * Renders every screen on a 480x640 Rokid display with the app inside the 480dp x 282dp band at
 * the bottom, exactly like MainActivity lays it out. Record with
 * `./gradlew :app:recordPaparazziDebug`; PNGs land in app/src/test/snapshots/images.
 */
class HudSnapshotTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(
            screenWidth = 480,
            screenHeight = 640,
            xdpi = 160,
            ydpi = 160,
            density = Density.MEDIUM,
            orientation = ScreenOrientation.PORTRAIT,
            softButtons = false
        ),
        theme = "android:Theme.Material.NoActionBar",
        showSystemUi = false
    )

    private val context get() = paparazzi.context
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    private val navigator = object : Navigator {
        override fun openSearch() {}
        override fun openPlayer(video: VideoItem) {}
        override fun openAccount() {}
        override fun openUpdate() {}
        override fun exitApp() {}
        override fun setKeepScreenOn(on: Boolean) {}
        override fun back() {}
        override fun startVoiceSearch(onResult: (String) -> Unit) {}
    }

    private val sampleVideos = listOf(
        VideoItem("u1", "[LCK] T1 vs Gen.G 하이라이트 | 2026 LCK 서머 결승", "LCK", 1_234, 2_310_000, null, false),
        VideoItem("u2", "로블록스 99밤생존 & 라이벌시참 쭈욱 달립니다", "잼촌", 0, 5_800, null, true),
        VideoItem("u3", "Rick Astley - Never Gonna Give You Up (Official Video)", "Rick Astley", 213, 1_700_000_000, null, false),
        VideoItem("u4", "백종원의 15분 김치찌개", "백종원 PAIK JONG WON", 905, 3_400_000, null, false),
        VideoItem("u5", "NewJeans (뉴진스) 'Supernatural' Official MV", "HYBE LABELS", 191, 98_000_000, null, false),
        VideoItem("u6", "서울 야경 드라이브 4K | 한강 다리 투어", "Walk Seoul", 3_725, 410_000, null, false),
        VideoItem("u7", "침착맨 삼국지 정주행 3부", "침착맨", 7_260, 1_200_000, null, false)
    )

    private fun tabs(items: List<VideoItem>) = listOf(
        HomeScreen.Tab("추천", "") { items },
        HomeScreen.Tab("이어보기", "") { emptyList() },
        HomeScreen.Tab("구독", "") { emptyList() },
        HomeScreen.Tab("좋아요", "") { emptyList() }
    )

    @Test
    fun home_chipSelected() {
        val home = HomeScreen(context, navigator, scope, tabs(sampleVideos)) { "로그인" }
        home.list.thumbnailBinder = ::fakeThumbnail
        snapshot(home.view)
    }

    @Test
    fun home_rowSelected() {
        val home = HomeScreen(context, navigator, scope, tabs(sampleVideos)) { "계정" }
        home.list.thumbnailBinder = ::fakeThumbnail
        repeat(10) { home.onAction(GlassesInput.Action.NEXT) } // 6 chips -> 5th card (2nd row)
        snapshot(home.view)
    }

    @Test
    fun home_offline() {
        val home = HomeScreen(context, navigator, scope, tabs(sampleVideos)) { "계정" }
        home.list.thumbnailBinder = ::fakeThumbnail
        repeat(7) { home.onAction(GlassesInput.Action.NEXT) }
        snapshot(home.view, offline = true)
    }

    @Test
    fun search_keyboard() {
        val search = SearchScreen(context, navigator, scope) { sampleVideos }
        "rick ast".forEach { c -> search.keyboard.setText(search.keyboard.text + c) }
        search.keyboard.selected = 11 // "l"
        snapshot(search.view)
    }

    @Test
    fun search_results() {
        val search = SearchScreen(context, navigator, scope) { sampleVideos }
        search.results.thumbnailBinder = ::fakeThumbnail
        search.keyboard.setText("rick astley")
        search.search("rick astley")
        snapshot(search.view)
    }

    @Test
    fun player_controls() {
        val player = PlayerScreen(context, navigator, scope, sampleVideos[2], playerFactory = null)
        // layoutlib cannot draw a SurfaceView; stand in a gradient for the video frame.
        player.videoView.visibility = View.INVISIBLE
        player.view.background = gradient(2)
        player.renderTimes(123_000, 213_000)
        snapshot(player.view)
    }

    @Test
    fun player_controlsHidden() {
        val player = PlayerScreen(context, navigator, scope, sampleVideos[2], playerFactory = null)
        // layoutlib cannot draw a SurfaceView; stand in a gradient for the video frame.
        player.videoView.visibility = View.INVISIBLE
        player.view.background = gradient(2)
        player.setControlsVisible(false)
        snapshot(player.view)
    }

    @Test
    fun player_shrunkTo50Percent() {
        val player = PlayerScreen(context, navigator, scope, sampleVideos[2], playerFactory = null)
        // The gradient sits on the video view itself here so the scaling shows up.
        player.videoView.foreground = gradient(2)
        player.setControlsVisible(false)
        repeat(10) { player.onAction(GlassesInput.Action.UP) } // start from 100%
        repeat(5) { player.onAction(GlassesInput.Action.DOWN) }
        snapshot(player.view)
        repeat(10) { player.onAction(GlassesInput.Action.UP) } // leave the saved size at 100%
    }

    @Test
    fun player_swipeSeek() {
        val player = PlayerScreen(context, navigator, scope, sampleVideos[2], playerFactory = null)
        player.videoView.visibility = View.INVISIBLE
        player.view.background = gradient(2)
        player.setControlsVisible(false)
        player.showSeekToastForPreview("+10초 · 2:13 / 3:33")
        snapshot(player.view)
    }

    @Test
    fun player_sizeAdjust() {
        val player = PlayerScreen(context, navigator, scope, sampleVideos[2], playerFactory = null)
        player.videoView.visibility = View.INVISIBLE
        player.view.background = gradient(2)
        player.showSizeAdjustForPreview()
        snapshot(player.view)
    }

    @Test
    fun player_privacyAdjust() {
        val player = PlayerScreen(context, navigator, scope, sampleVideos[2], playerFactory = null)
        player.videoView.visibility = View.INVISIBLE
        player.view.background = gradient(2)
        player.showPrivacyAdjustForPreview(3)
        snapshot(player.view)
    }

    /** What each "가림" level does to a picture (layoutlib cannot draw the TextureView itself). */
    @Test
    fun privacyFilter_levels() {
        val grid = android.widget.GridLayout(context).apply { columnCount = 3 }
        for (level in 0..com.chorockkim.rokidyoutube.player.PrivacyFilter.MAX_LEVEL) {
            val cell = android.widget.LinearLayout(context).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                setPadding(context.dp(4), context.dp(2), context.dp(4), context.dp(2))
            }
            cell.addView(ImageView(context).apply {
                setImageDrawable(GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    intArrayOf(Color.BLACK, Color.DKGRAY, Color.GRAY, 0xFF6F86B5.toInt(), Color.WHITE)
                ))
                colorFilter = com.chorockkim.rokidyoutube.player.PrivacyFilter.colorFilter(level)
            }, android.widget.LinearLayout.LayoutParams(context.dp(148), context.dp(100)))
            cell.addView(Hud.text(context, 12f).apply { text = if (level == 0) "끔" else "가림 $level" })
            grid.addView(cell)
        }
        snapshot(grid)
    }

    @Test
    fun update_available() {
        val screen = UpdateScreen(context, navigator, scope, updater = null)
        screen.showAvailable(
            com.chorockkim.rokidyoutube.update.Release("0.2.1", 201, 7_481_342, "ab".repeat(32))
        )
        snapshot(screen.view, update = "0.2.1")
    }

    @Test
    fun home_updateAvailable() {
        val home = HomeScreen(context, navigator, scope, tabs(sampleVideos)) { "계정" }
        home.list.thumbnailBinder = ::fakeThumbnail
        home.setUpdateAvailable("0.2.1")
        repeat(6) { home.onAction(GlassesInput.Action.NEXT) } // last chip
        snapshot(home.view, update = "0.2.1")
    }

    @Test
    fun account_deviceCode() {
        val auth = com.chorockkim.rokidyoutube.auth.GoogleAuth(
            "id", "secret",
            object : com.chorockkim.rokidyoutube.auth.GoogleAuth.TokenStore {
                override fun load() = null
                override fun save(tokens: com.chorockkim.rokidyoutube.auth.GoogleAuth.Tokens?) {}
            },
            { error("no network in snapshots") }
        )
        val account = AccountScreen(context, navigator, scope, auth, {}, {})
        account.showCodeForPreview("KQW-RZTD")
        snapshot(account.view)
    }

    /** Full 480x640 glasses display; black above the band is transparent on the glasses. */
    private fun snapshot(band: View, offline: Boolean = false, update: String? = null) {
        val root = FrameLayout(context).apply { setBackgroundColor(Color.BLACK) }
        root.addView(
            band,
            FrameLayout.LayoutParams(
                context.dp(Hud.BAND_WIDTH_DP), context.dp(Hud.BAND_HEIGHT_DP),
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            )
        )
        if (offline) root.addView(Hud.offlineNotice(context), Hud.offlineNoticeParams(context))
        if (update != null) {
            root.addView(Hud.updateNotice(context).apply { text = "새 버전 v$update" }, Hud.updateNoticeParams(context))
        }
        root.addView(
            StatusIndicator(context).apply { show(82, charging = false, time = "14:32") },
            StatusIndicator.params(context)
        )
        paparazzi.snapshot(root)
    }

    private fun fakeThumbnail(view: ImageView, item: VideoItem) {
        view.setImageDrawable(gradient(sampleVideos.indexOf(item)))
    }

    private fun gradient(index: Int) = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        when (index % 4) {
            0 -> intArrayOf(0xFF1B3A6B.toInt(), 0xFFB8862F.toInt())
            1 -> intArrayOf(0xFF7A1C1C.toInt(), 0xFFD9A43A.toInt())
            2 -> intArrayOf(0xFF2C3F63.toInt(), 0xFF6F86B5.toInt())
            else -> intArrayOf(0xFF2E5A2E.toInt(), 0xFFC2C26A.toInt())
        }
    )
}
