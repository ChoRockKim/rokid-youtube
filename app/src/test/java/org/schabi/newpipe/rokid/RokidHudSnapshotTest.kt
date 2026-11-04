package org.schabi.newpipe.rokid

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import org.junit.Rule
import org.junit.Test
import org.schabi.newpipe.databinding.ListStreamItemBinding
import org.schabi.newpipe.databinding.PlayerBinding
import org.schabi.newpipe.databinding.ToolbarLayoutBinding

/**
 * Renders the Rokid HUD band (480dp x 282dp at the bottom of a 480x640 glasses display)
 * with the real layouts, so UI changes can be reviewed without the glasses.
 *
 * Run `./gradlew :app:recordPaparazziDebug`; PNGs land in `app/src/test/snapshots/images`.
 */
class RokidHudSnapshotTest {
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
        theme = "BlackTheme",
        showSystemUi = false
    )

    private val context get() = paparazzi.context
    private val inflater get() = LayoutInflater.from(context)

    @Test
    fun home() {
        val band = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
        }

        val toolbar = ToolbarLayoutBinding.inflate(inflater, band, false)
        toolbar.toolbar.title = "Live"
        toolbar.toolbar.setTitleTextColor(Color.WHITE)
        toolbar.toolbar.setNavigationIcon(org.schabi.newpipe.R.drawable.ic_menu)
        band.addView(toolbar.root)

        val main = inflater.inflate(org.schabi.newpipe.R.layout.fragment_main, band, false)
        main.findViewById<View>(org.schabi.newpipe.R.id.main_tab_layout).visibility = View.GONE
        main.findViewById<View>(org.schabi.newpipe.R.id.pager).visibility = View.GONE
        val nav = main.findViewById<View>(org.schabi.newpipe.R.id.rokid_home_nav)
        nav.visibility = View.VISIBLE
        val navItems = main.findViewById<LinearLayout>(org.schabi.newpipe.R.id.rokid_home_nav_items)
        val items = RokidKioskNavigator.findTopBarItemsForTesting(
            linkedSetOf("Live", "Gaming", "Music", "Movies")
        )
        items.forEachIndexed { index, item ->
            navItems.addView(
                RokidKioskNavBar.createButton(context, item).apply { isSelected = index == 0 }
            )
        }
        (nav.parent as ViewGroup).removeView(nav)
        band.addView(nav)

        listOf(
            Triple("CHAMPIONNAT DE FRANCE DOUBLETTE MIXTE", "FF Pétanque et Jeu", "7.2K watching"),
            Triple("로블록스 99밤생존 & 라이벌시참 쭈욱 달립니다", "잼촌", "5.8K watching"),
            Triple("[LIVE] La preview et les bets du soir", "Winamax Sport", "3.1K watching")
        ).forEachIndexed { index, (title, uploader, details) ->
            val row = ListStreamItemBinding.inflate(inflater, band, false)
            row.itemVideoTitleView.text = title
            row.itemUploaderView.text = uploader
            row.itemAdditionalDetails.text = details
            row.itemDurationView.text = "LIVE"
            row.itemThumbnailView.setImageDrawable(thumbnail(index))
            row.itemProgressView.visibility = View.GONE
            band.addView(row.root)
        }

        paparazzi.snapshot(glasses(band))
    }

    @Test
    fun playerControls() {
        val player = PlayerBinding.inflate(inflater)
        player.surfaceView.background = thumbnail(2)
        player.playbackControlRoot.visibility = View.VISIBLE
        player.titleTextView.text = "Rick Astley - Never Gonna Give You Up"
        player.channelTextView.text = "Rick Astley"
        player.qualityTextView.text = "720p"
        player.playbackSpeed.text = "1.25x"
        player.playbackCurrentTime.text = "02:03"
        player.playbackEndTime.text = "03:33"
        player.playbackSeekBar.max = 213
        player.playbackSeekBar.progress = 123
        player.rokidActionRail.visibility = View.VISIBLE
        player.rokidActionPlayPause.text = "Pause"
        player.rokidActionQuality.text = "1080p60"
        player.rokidActionSpeed.text = "1.25x"
        player.rokidActionPlayPause.isSelected = true

        paparazzi.snapshot(glasses(player.root))
    }

    /** Full 480x640 glasses display: black (transparent) above, the band at the bottom. */
    private fun glasses(band: View): View = FrameLayout(context).apply {
        setBackgroundColor(Color.BLACK)
        addView(band, RokidViewport.bandLayoutParams(context))
    }

    private fun thumbnail(index: Int) = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        when (index) {
            0 -> intArrayOf(0xFF3B5A8A.toInt(), 0xFFA8452F.toInt())
            1 -> intArrayOf(0xFF7A1C1C.toInt(), 0xFFD9A43A.toInt())
            else -> intArrayOf(0xFF2C3F63.toInt(), 0xFF5A6F99.toInt())
        }
    )
}
