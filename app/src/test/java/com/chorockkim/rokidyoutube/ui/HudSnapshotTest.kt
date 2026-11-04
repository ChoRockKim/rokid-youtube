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
import com.chorockkim.rokidyoutube.data.Category
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
        override fun openPlayer(url: String, title: String) {}
        override fun back() {}
        override fun startVoiceSearch(onResult: (String) -> Unit) {}
    }

    private val sampleVideos = listOf(
        VideoItem("u1", "[LCK] T1 vs Gen.G 하이라이트 | 2026 LCK 서머 결승", "LCK", 1_234, 2_310_000, null, false),
        VideoItem("u2", "로블록스 99밤생존 & 라이벌시참 쭈욱 달립니다", "잼촌", 0, 5_800, null, true),
        VideoItem("u3", "Rick Astley - Never Gonna Give You Up (Official Video)", "Rick Astley", 213, 1_700_000_000, null, false),
        VideoItem("u4", "백종원의 15분 김치찌개", "백종원 PAIK JONG WON", 905, 3_400_000, null, false)
    )

    private val categories = listOf(
        Category("trending_gaming", "게임"),
        Category("trending_music", "음악"),
        Category("trending_movies_and_shows", "영화"),
        Category("trending_podcasts_episodes", "팟캐스트"),
        Category("live", "라이브")
    )

    @Test
    fun home_chipSelected() {
        val home = HomeScreen(context, navigator, scope, categories) { sampleVideos }
        home.list.thumbnailBinder = ::fakeThumbnail
        snapshot(home.view)
    }

    @Test
    fun home_rowSelected() {
        val home = HomeScreen(context, navigator, scope, categories) { sampleVideos }
        home.list.thumbnailBinder = ::fakeThumbnail
        repeat(7) { home.onAction(GlassesInput.Action.NEXT) } // 6 chips -> 2nd row
        snapshot(home.view)
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
        val player = PlayerScreen(
            context, navigator, scope, "u3",
            "Rick Astley - Never Gonna Give You Up (Official Video)", playerFactory = null
        )
        // layoutlib cannot draw a SurfaceView; stand in a gradient for the video frame.
        player.videoView.visibility = View.INVISIBLE
        player.view.background = gradient(2)
        player.renderTimes(123_000, 213_000)
        snapshot(player.view)
    }

    @Test
    fun player_controlsHidden() {
        val player = PlayerScreen(context, navigator, scope, "u3", "Rick Astley", playerFactory = null)
        // layoutlib cannot draw a SurfaceView; stand in a gradient for the video frame.
        player.videoView.visibility = View.INVISIBLE
        player.view.background = gradient(2)
        player.setControlsVisible(false)
        snapshot(player.view)
    }

    /** Full 480x640 glasses display; black above the band is transparent on the glasses. */
    private fun snapshot(band: View) {
        val root = FrameLayout(context).apply { setBackgroundColor(Color.BLACK) }
        root.addView(
            band,
            FrameLayout.LayoutParams(
                context.dp(Hud.BAND_WIDTH_DP), context.dp(Hud.BAND_HEIGHT_DP),
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            )
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
