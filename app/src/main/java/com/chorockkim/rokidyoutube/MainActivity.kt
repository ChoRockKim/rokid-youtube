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
import kotlinx.coroutines.launch
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
    private var pendingVoice: com.chorockkim.rokidyoutube.ui.VoiceListener? = null
    private lateinit var offlineBanner: android.widget.TextView
    private lateinit var updateBanner: android.widget.TextView
    private var networkCallback: android.net.ConnectivityManager.NetworkCallback? = null
    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) listen() else pendingVoice?.onError("마이크 권한이 필요해요")
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
        // Keep the glasses display on while the app is open (the player lets it time out while
        // the user has paused, see setKeepScreenOn).
        setKeepScreenOn(true)
        // Volume keys always change the media volume, also while nothing is playing (otherwise
        // they change the ring / notification volume there).
        volumeControlStream = android.media.AudioManager.STREAM_MUSIC
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
        root.addView(com.chorockkim.rokidyoutube.ui.StatusIndicator(this), com.chorockkim.rokidyoutube.ui.StatusIndicator.params(this))
        updateBanner = Hud.updateNotice(this).apply { visibility = android.view.View.GONE }
        root.addView(updateBanner, Hud.updateNoticeParams(this))
        setContentView(root)
        watchNetwork()

        home = HomeScreen(this, this, lifecycleScope, homeTabs()) { if (app.auth.isSignedIn) "계정" else "로그인" }
        push(home)
        checkForUpdate()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { dispatchAction(GlassesInput.Action.BACK) }
        })
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) return super.dispatchKeyEvent(event)
        return when (val action = input.map(event)) {
            GlassesInput.Action.NONE -> super.dispatchKeyEvent(event)
            GlassesInput.Action.DUPLICATE -> true
            // Volume keys: fine steps in the player, the normal system volume elsewhere.
            GlassesInput.Action.VOLUME_UP, GlassesInput.Action.VOLUME_DOWN ->
                dispatchAction(action) || super.dispatchKeyEvent(event)
            else -> { dispatchAction(action); true }
        }
    }

    /** Returns whether a screen (or going back) handled [action]. */
    fun dispatchAction(action: GlassesInput.Action): Boolean {
        val top = stack.lastOrNull()
        var consumed = top?.onAction(action) ?: false
        // Vertical swipes fall back to previous / next on screens that don't use them.
        if (!consumed && action == GlassesInput.Action.UP) consumed = top?.onAction(GlassesInput.Action.PREVIOUS) ?: false
        if (!consumed && action == GlassesInput.Action.DOWN) consumed = top?.onAction(GlassesInput.Action.NEXT) ?: false
        if (!consumed && action == GlassesInput.Action.BACK) {
            back()
            consumed = true
        }
        return consumed
    }

    private fun push(screen: Screen) {
        stack.lastOrNull()?.let {
            it.onHidden()
            screenHost.removeView(it.view)
        }
        stack.addLast(screen)
        screenHost.addView(screen.view, FrameLayout.LayoutParams(-1, -1))
        setKeepScreenOn(true)
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
            setKeepScreenOn(true)
            it.onShown()
        }
        ringTarget.requestFocus()
    }

    override fun setKeepScreenOn(on: Boolean) {
        val flag = android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        if (on) window.addFlags(flag) else window.clearFlags(flag)
    }

    override fun exitApp() {
        // onDestroy releases the player and the screens.
        finishAndRemoveTask()
    }

    override fun openSearch() {
        push(SearchScreen(this, this, lifecycleScope) { YouTubeRepository.search(it) })
    }

    override fun openPlayer(video: VideoItem) {
        push(PlayerScreen(this, this, lifecycleScope, video, app.history))
    }

    /**
     * At every app start: ask the server for a newer version and, if there is one, download,
     * verify and install it right away (like rokid-conversate). Installed once with
     * `adb install -i`, Android asks nothing and the app starts again by itself; otherwise the
     * system shows its install confirmation. Progress shows in the green pill above the band.
     */
    private fun checkForUpdate() {
        val updater = app.updater
        lifecycleScope.launch {
            val release = (runCatching { updater.check(force = true) }.getOrNull()
                as? com.chorockkim.rokidyoutube.update.Updater.Check.Available)?.release
            showUpdate(release)
            if (release == null) {
                installCompanions()
                return@launch
            }
            if (!updater.canInstall()) {
                // Needs "install unknown apps" once (adb: appops set <pkg> REQUEST_INSTALL_PACKAGES allow).
                updateBanner.text = "새 버전 v${release.versionName} · 설치 허용 필요"
                return@launch
            }
            updateBanner.text = "업데이트 v${release.versionName} 받는 중"
            val apk = runCatching {
                updater.download(release) { percent ->
                    if (percent >= 0) updateBanner.text = "업데이트 v${release.versionName} 받는 중 $percent%"
                }
            }.getOrElse {
                updateBanner.text = "업데이트 실패 · ${it.message ?: "다시 켜면 다시 해요"}"
                return@launch
            }
            com.chorockkim.rokidyoutube.update.InstallResultReceiver.listener = { result ->
                runOnUiThread {
                    updateBanner.text = when (result) {
                        is com.chorockkim.rokidyoutube.update.InstallResultReceiver.Result.Failed ->
                            "업데이트 실패 · ${result.message}"
                        com.chorockkim.rokidyoutube.update.InstallResultReceiver.Result.WaitingForUser ->
                            "업데이트 v${release.versionName} · 설치 확인을 눌러 주세요"
                        else -> "업데이트 v${release.versionName} 설치 완료 · 다시 켜는 중"
                    }
                }
            }
            updateBanner.text = "업데이트 v${release.versionName} 설치 중 · 곧 다시 켜져요"
            runCatching { updater.install(apk) }.onFailure {
                updateBanner.text = "업데이트 실패 · ${it.message ?: it.javaClass.simpleName}"
            }
        }
    }

    /**
     * Installs the glasses apps in [Updater.COMPANIONS] that are not on the glasses yet (e.g.
     * rokid-lens), from the same server, signed with the same key. Android shows its install
     * confirmation once for a new app; afterwards the app updates itself.
     */
    private suspend fun installCompanions() {
        val updater = app.updater
        for ((pkg, label) in com.chorockkim.rokidyoutube.update.Updater.COMPANIONS) {
            if (updater.installedVersion(pkg) != null) continue
            val target = com.chorockkim.rokidyoutube.update.Updater.AppTarget(
                pkg, com.chorockkim.rokidyoutube.update.Updater.companionUrl(updater.self.baseUrl, label), label
            )
            val release = runCatching { updater.latest(target) }.getOrNull() ?: continue
            val name = label.removePrefix("rokid-").replaceFirstChar { it.uppercase() }
            updateBanner.visibility = android.view.View.VISIBLE
            if (!updater.canInstall()) {
                updateBanner.text = "$name 설치하려면 설치 허용 필요"
                return
            }
            updateBanner.text = "$name 받는 중"
            val apk = runCatching {
                updater.download(release, target) { percent ->
                    if (percent >= 0) updateBanner.text = "$name 받는 중 $percent%"
                }
            }.getOrElse {
                updateBanner.text = "$name 설치 실패 · ${it.message ?: "다시 켜면 다시 해요"}"
                return
            }
            com.chorockkim.rokidyoutube.update.InstallResultReceiver.listener = { result ->
                runOnUiThread {
                    updateBanner.text = when (result) {
                        is com.chorockkim.rokidyoutube.update.InstallResultReceiver.Result.Failed ->
                            "$name 설치 실패 · ${result.message}"
                        com.chorockkim.rokidyoutube.update.InstallResultReceiver.Result.WaitingForUser ->
                            "$name 설치 확인 창에서 '설치'를 눌러 주세요"
                        else -> "$name 설치 완료"
                    }
                }
            }
            updateBanner.text = "$name 설치 중"
            runCatching { updater.install(apk, pkg) }.onFailure {
                updateBanner.text = "$name 설치 실패 · ${it.message ?: it.javaClass.simpleName}"
            }
            return // one at a time: the next one on the next start
        }
    }

    private fun showUpdate(release: com.chorockkim.rokidyoutube.update.Release?) {
        updateBanner.text = release?.let { "새 버전 v${it.versionName}" }
        updateBanner.visibility = if (release == null) android.view.View.GONE else android.view.View.VISIBLE
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
        wifiEnabler.stop()
        stack.lastOrNull()?.onHidden()
    }

    override fun onStart() {
        super.onStart()
        wifiEnabler.start()
        stack.lastOrNull()?.onShown()
    }

    // region Offline notice

    private var online = true
    private var wifiStatus: com.chorockkim.rokidyoutube.net.WifiEnabler.Status? = null
    /** Turns Wi-Fi back on through the rokid-conversate helper app while the app is open. */
    private val wifiEnabler by lazy {
        com.chorockkim.rokidyoutube.net.WifiEnabler(this) { status ->
            runOnUiThread {
                wifiStatus = status
                renderNetwork()
            }
        }
    }

    /** The red pill above the band: Wi-Fi state first, then "no internet". */
    private fun renderNetwork() {
        val text = com.chorockkim.rokidyoutube.net.WifiEnabler.label(wifiStatus, online)
        offlineBanner.text = text
        offlineBanner.visibility = if (text == null) android.view.View.GONE else android.view.View.VISIBLE
    }

    private fun watchNetwork() {
        val cm = getSystemService(android.net.ConnectivityManager::class.java) ?: return
        fun show(isOnline: Boolean) = runOnUiThread {
            online = isOnline
            renderNetwork()
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

    override fun startVoiceSearch(listener: com.chorockkim.rokidyoutube.ui.VoiceListener) {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            listener.onError("이 기기엔 음성 인식이 없어요 · 한글 키보드를 써 주세요")
            return
        }
        pendingVoice = listener
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        listen()
    }

    private fun listen() {
        val listener = pendingVoice ?: return
        val r = recognizer ?: runCatching { SpeechRecognizer.createSpeechRecognizer(this) }.getOrNull()
            ?.also { recognizer = it }
        if (r == null) {
            listener.onError("음성 인식을 시작하지 못했어요")
            return
        }
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = listener.onListening("")

            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.trim()
                if (!text.isNullOrEmpty()) listener.onListening(text)
            }

            override fun onResults(results: Bundle) {
                val text = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.trim()
                if (text.isNullOrEmpty()) listener.onError("못 알아들었어요 · 다시 해 주세요")
                else listener.onResult(text)
            }

            override fun onError(error: Int) {
                // A recognizer stuck in "busy" / a client error does not recover: make a new one next time.
                if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || error == SpeechRecognizer.ERROR_CLIENT) {
                    recognizer?.destroy()
                    recognizer = null
                }
                listener.onError(voiceErrorText(error))
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        r.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ko-KR")
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        )
    }

    private fun voiceErrorText(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "못 알아들었어요 · 다시 해 주세요"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "마이크 권한이 필요해요"
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
        SpeechRecognizer.ERROR_SERVER -> "음성 인식 서버에 못 붙었어요 · 인터넷 확인"
        SpeechRecognizer.ERROR_AUDIO -> "마이크를 쓸 수 없어요"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "음성 인식이 바빠요 · 다시 해 주세요"
        12, 13 -> "한국어 음성 인식이 없어요 · 한글 키보드를 써 주세요" // LANGUAGE_NOT_SUPPORTED / UNAVAILABLE
        else -> "음성 인식 실패 ($error) · 한글 키보드를 써 주세요"
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    // endregion
}
