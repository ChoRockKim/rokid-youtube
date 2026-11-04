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
import com.chorockkim.rokidyoutube.auth.GoogleAuth
import com.chorockkim.rokidyoutube.data.VideoItem
import com.chorockkim.rokidyoutube.ui.Hud.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test

/** The glasses side of signing in, step by step (used for the README's login walkthrough). */
class LoginFlowSnapshotTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(
            screenWidth = 480, screenHeight = 640, xdpi = 160, ydpi = 160,
            density = Density.MEDIUM, orientation = ScreenOrientation.PORTRAIT, softButtons = false
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
        override fun back() {}
        override fun startVoiceSearch(onResult: (String) -> Unit) {}
    }

    private val popular = listOf(
        VideoItem("p1", "[LCK] T1 vs Gen.G 하이라이트 | 2026 LCK 서머 결승", "LCK", 1_234, 2_310_000, null, false),
        VideoItem("p2", "로블록스 99밤생존 & 라이벌시참 쭈욱 달립니다", "잼촌", 0, 5_800, null, true),
        VideoItem("p3", "Rick Astley - Never Gonna Give You Up", "Rick Astley", 213, 1_700_000_000, null, false),
        VideoItem("p4", "백종원의 15분 김치찌개", "백종원 PAIK JONG WON", 905, 3_400_000, null, false),
        VideoItem("p5", "NewJeans (뉴진스) 'Supernatural' Official MV", "HYBE LABELS", 191, 98_000_000, null, false),
        VideoItem("p6", "서울 야경 드라이브 4K | 한강 다리 투어", "Walk Seoul", 3_725, 410_000, null, false)
    )
    private val subscriptions = listOf(
        VideoItem("s1", "침착맨 삼국지 정주행 3부", "침착맨", 7_260, 1_200_000, null, false),
        VideoItem("s2", "아이폰 18 프로 한 달 사용기", "ITSub잇섭", 842, 640_000, null, false),
        VideoItem("s3", "주간 코딩 라이브 #42", "노마드 코더", 5_400, 88_000, null, false),
        VideoItem("s4", "Kotlin 2.4 새 기능 정리", "Android Developers", 1_030, 120_000, null, false),
        VideoItem("s5", "유럽 기차 여행 브이로그 🇨🇭", "빠니보틀", 1_980, 2_100_000, null, false),
        VideoItem("s6", "1분 요리: 계란볶음밥", "1분요리 뚝딱이형", 64, 3_300_000, null, false)
    )

    private fun tabs(subs: List<VideoItem>) = listOf(
        HomeScreen.Tab("추천", "") { popular },
        HomeScreen.Tab("이어보기", "") { emptyList() },
        HomeScreen.Tab("구독", "구글 계정을 연결하면 볼 수 있어요") { subs },
        HomeScreen.Tab("좋아요", "") { emptyList() }
    )

    @Test
    fun step1_homeLoginChip() {
        val home = HomeScreen(context, navigator, scope, tabs(emptyList())) { "로그인" }
        home.list.thumbnailBinder = ::fakeThumbnail
        repeat(5) { home.onAction(GlassesInput.Action.NEXT) } // 검색 추천 이어보기 구독 좋아요 -> 로그인
        snapshot(home.view)
    }

    @Test
    fun step2_codeOnGlasses() {
        val auth = GoogleAuth("id", "secret", object : GoogleAuth.TokenStore {
            override fun load() = null
            override fun save(tokens: GoogleAuth.Tokens?) {}
        }, { error("no network in snapshots") })
        val account = AccountScreen(context, navigator, scope, auth, {}, {})
        account.showCodeForPreview("KQW-RZTD")
        snapshot(account.view)
    }

    @Test
    fun step3_homeSignedInSubscriptions() {
        val home = HomeScreen(context, navigator, scope, tabs(subscriptions)) { "계정" }
        home.list.thumbnailBinder = ::fakeThumbnail
        repeat(3) { home.onAction(GlassesInput.Action.NEXT) } // -> 구독
        home.onAction(GlassesInput.Action.SELECT)
        snapshot(home.view)
    }

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
        val colors = listOf(
            intArrayOf(0xFF1B3A6B.toInt(), 0xFFB8862F.toInt()),
            intArrayOf(0xFF7A1C1C.toInt(), 0xFFD9A43A.toInt()),
            intArrayOf(0xFF2C3F63.toInt(), 0xFF6F86B5.toInt()),
            intArrayOf(0xFF2E5A2E.toInt(), 0xFFC2C26A.toInt())
        )
        view.setImageDrawable(
            GradientDrawable(GradientDrawable.Orientation.TL_BR, colors[Math.floorMod(item.url.hashCode(), 4)])
        )
    }
}
