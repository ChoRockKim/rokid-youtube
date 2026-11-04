package com.chorockkim.rokidyoutube

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.KeyEvent
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.chorockkim.rokidyoutube.data.VideoItem
import com.chorockkim.rokidyoutube.data.YouTubeRepository
import com.chorockkim.rokidyoutube.ui.AccountScreen
import com.chorockkim.rokidyoutube.ui.GlassesInput
import com.chorockkim.rokidyoutube.ui.HomeScreen
import com.chorockkim.rokidyoutube.ui.Hud
import com.chorockkim.rokidyoutube.ui.Hud.dp
import com.chorockkim.rokidyoutube.ui.Navigator
import com.chorockkim.rokidyoutube.ui.PlayerScreen
import com.chorockkim.rokidyoutube.ui.RingTarget
import com.chorockkim.rokidyoutube.ui.Screen
import com.chorockkim.rokidyoutube.ui.SearchScreen

/**
 * Single activity. Everything is drawn inside [band], a 480dp x 282dp area glued to the bottom
 * edge of the glasses display; the rest stays black, i.e. transparent on the waveguide.
 */
class MainActivity : ComponentActivity(), Navigator {
    private lateinit var band: FrameLayout
    private lateinit var home: HomeScreen
    private val app get() = application as RokidYouTubeApp
    /** Holds the current screen; hidden from accessibility so [ringTarget] is the only node. */
    private lateinit var screenHost: FrameLayout
    private lateinit var ringTarget: RingTarget
    private val stack = ArrayDeque<Screen>()
    private val input = GlassesInput()
    private var recognizer: SpeechRecognizer? = null
    private var pendingVoice: ((String) -> Unit)? = null
    private lateinit var offlineBanner: android.widget.TextView
    private var networkCallback: android.net.ConnectivityManager.NetworkCallback? = null
    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) listen() else toast("마이크 권한이 필요해요")
    }

    /**
     * Fits the 480dp x 282dp band to the glasses display whatever its real density is: the
     * density is overridden so the band is exactly as wide as the screen (or as tall, if the
     * screen is very wide). System font scaling is ignored so text cannot overflow the band.
     */
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(newBase)
        val metrics = newBase.resources.displayMetrics
        val (w, h) = realScreenSize(newBase, metrics.widthPixels, metrics.heightPixels)
        val scale = minOf(w / Hud.BAND_WIDTH_DP.toFloat(), h / Hud.BAND_HEIGHT_DP.toFloat())
        applyOverrideConfiguration(android.content.res.Configuration().apply {
            densityDpi = (scale * android.util.DisplayMetrics.DENSITY_DEFAULT).toInt().coerceAtLeast(1)
            fontScale = 1f
        })
    }

    private fun realScreenSize(context: android.content.Context, fallbackW: Int, fallbackH: Int): Pair<Int, Int> {
        val wm = context.getSystemService(android.view.WindowManager::class.java) ?: return fallbackW to fallbackH
        return if (android.os.Build.VERSION.SDK_INT >= 30) {
            val b = wm.maximumWindowMetrics.bounds
            b.width() to b.height()
        } else {
            val m = android.util.DisplayMetrics()
            @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(m)
            m.widthPixels to m.heightPixels
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keep the glasses display on while the app is open, not only during playback.
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        band = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        root.addView(
            band,
            FrameLayout.LayoutParams(
                dp(Hud.BAND_WIDTH_DP), dp(Hud.BAND_HEIGHT_DP),
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            )
        )
        screenHost = FrameLayout(this).apply {
            importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        band.addView(screenHost, FrameLayout.LayoutParams(-1, -1))
        // R08 ring via R08 Access Bridge: see RingTarget.
        ringTarget = RingTarget(this, ::dispatchAction)
        band.addView(ringTarget, FrameLayout.LayoutParams(-1, -1))
        // Sits just above the band, in the empty (transparent) area, so it never covers the app.
        offlineBanner = Hud.offlineNotice(this).apply { visibility = android.view.View.GONE }
        root.addView(offlineBanner, Hud.offlineNoticeParams(this))
        setContentView(root)
        watchNetwork()

        home = HomeScreen(this, this, lifecycleScope, homeTabs()) { if (app.auth.isSignedIn) "계정" else "로그인" }
        push(home)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = dispatchAction(GlassesInput.Action.BACK)
        })
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) return super.dispatchKeyEvent(event)
        return when (val action = input.map(event)) {
            GlassesInput.Action.NONE -> super.dispatchKeyEvent(event)
            GlassesInput.Action.DUPLICATE -> true
            else -> { dispatchAction(action); true }
        }
    }

    fun dispatchAction(action: GlassesInput.Action) {
        val top = stack.lastOrNull()
        var consumed = top?.onAction(action) ?: false
        // Vertical swipes fall back to previous / next on screens that don't use them.
        if (!consumed && action == GlassesInput.Action.UP) consumed = top?.onAction(GlassesInput.Action.PREVIOUS) ?: false
        if (!consumed && action == GlassesInput.Action.DOWN) consumed = top?.onAction(GlassesInput.Action.NEXT) ?: false
        if (!consumed && action == GlassesInput.Action.BACK) back()
    }

    private fun push(screen: Screen) {
        stack.lastOrNull()?.let {
            it.onHidden()
            screenHost.removeView(it.view)
        }
        stack.addLast(screen)
        screenHost.addView(screen.view, FrameLayout.LayoutParams(-1, -1))
        screen.onShown()
        ringTarget.requestFocus()
    }

    override fun back() {
        if (stack.size <= 1) {
            finish()
            return
        }
        val top = stack.removeLast()
        top.onHidden()
        top.onDestroy()
        screenHost.removeView(top.view)
        stack.last().let {
            screenHost.addView(it.view, FrameLayout.LayoutParams(-1, -1))
            it.onShown()
        }
        ringTarget.requestFocus()
    }

    override fun openSearch() {
        push(SearchScreen(this, this, lifecycleScope) { YouTubeRepository.search(it) })
    }

    override fun openPlayer(video: VideoItem) {
        push(PlayerScreen(this, this, lifecycleScope, video, app.history))
    }

    override fun openAccount() {
        push(
            AccountScreen(
                this, this, lifecycleScope, app.auth,
                clearHistory = { app.history.clear() },
                onAccountChanged = {
                    app.recommendations.invalidate()
                    home.reload()
                }
            )
        )
    }

    /** Personal feeds instead of YouTube categories (see README for what each one uses). */
    private fun homeTabs() = listOf(
        HomeScreen.Tab("추천", "아직 추천할 게 없어요", reloadOnReturn = true) {
            // Watch history + (signed in) liked videos and subscriptions; popular until there is any.
            app.recommendations.feed().ifEmpty { YouTubeRepository.popular() }
        },
        HomeScreen.Tab("이어보기", "이어 볼 영상이 없어요", reloadOnReturn = true) {
            app.history.continueWatching()
        },
        HomeScreen.Tab("구독", "구독 채널의 새 영상이 없어요") { app.accountApi.subscriptionFeed() },
        HomeScreen.Tab("좋아요", "좋아요한 영상이 없어요") { app.accountApi.likedVideos() }
    )

    override fun onStop() {
        super.onStop()
        stack.lastOrNull()?.onHidden()
    }

    override fun onStart() {
        super.onStart()
        stack.lastOrNull()?.onShown()
    }

    // region Offline notice

    private fun watchNetwork() {
        val cm = getSystemService(android.net.ConnectivityManager::class.java) ?: return
        fun show(online: Boolean) = runOnUiThread {
            offlineBanner.visibility = if (online) android.view.View.GONE else android.view.View.VISIBLE
        }
        fun hasInternet(caps: android.net.NetworkCapabilities?) =
            caps?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        fun refresh() = show(hasInternet(cm.getNetworkCapabilities(cm.activeNetwork)))
        val callback = object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) = refresh()
            // Default-network callback: losing it means there is no network at all.
            override fun onLost(network: android.net.Network) = show(false)
            override fun onCapabilitiesChanged(
                network: android.net.Network, caps: android.net.NetworkCapabilities
            ) = show(hasInternet(caps))
        }
        cm.registerDefaultNetworkCallback(callback)
        networkCallback = callback
        refresh()
    }

    // endregion

    override fun onDestroy() {
        networkCallback?.let {
            getSystemService(android.net.ConnectivityManager::class.java)?.unregisterNetworkCallback(it)
        }
        stack.forEach { it.onDestroy() }
        recognizer?.destroy()
        super.onDestroy()
    }

    // region Voice search (Korean), same SpeechRecognizer flow as RokidPipe's SearchFragment

    override fun startVoiceSearch(onResult: (String) -> Unit) {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            toast("음성 인식을 쓸 수 없어요")
            return
        }
        pendingVoice = onResult
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        listen()
    }

    private fun listen() {
        val r = recognizer ?: SpeechRecognizer.createSpeechRecognizer(this).also { recognizer = it }
        r.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle) {
                val text = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.trim()
                if (text.isNullOrEmpty()) toast("못 알아들었어요") else pendingVoice?.invoke(text)
            }

            override fun onError(error: Int) = toast("음성 인식 실패 ($error)")
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        r.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        )
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    // endregion
}
