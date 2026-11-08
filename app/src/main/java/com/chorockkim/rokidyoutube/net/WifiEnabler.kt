package com.chorockkim.rokidyoutube.net

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.provider.Settings

/**
 * Keeps Wi-Fi on while the app is open, the same way as rokid-conversate's WifiEnabler.
 *
 * The glasses keep Wi-Fi off to save battery, and apps targeting API 29+ (this one) cannot turn
 * it on. While it is off, every [RETRY_MS] this tries, in order:
 *   1. the "Wi-Fi 켜기 도우미" app from rokid-conversate (targets API 28, so it still may):
 *      its no-UI activity, and on the next try its broadcast receiver (on the glasses the
 *      activity worked where the broadcast to a never-started app did not);
 *   2. Settings.Global.WIFI_ON = 1 (only with WRITE_SECURE_SETTINGS granted over adb);
 *   3. WifiManager.setWifiEnabled(true) (does nothing at targetSdk 29+, harmless).
 * Joining a network is left to the system (saved networks, e.g. the phone hotspot). The system
 * Wi-Fi screen is never opened by itself: it is bright and large on the glasses.
 * The helper reports its result only to rokid-conversate, so this app judges by the Wi-Fi state.
 */
class WifiEnabler(private val context: Context, private val onStatus: (Status) -> Unit) {
    enum class Status {
        /** Off, trying to turn it on. */
        ENABLING,
        /** Still off after [GIVE_UP_MS]; keeps trying. */
        CANNOT_ENABLE,
        /** No helper app on the glasses and Wi-Fi is off. */
        NO_HELPER,
        /** On, not joined to a network yet. */
        WAITING_FOR_NETWORK,
        CONNECTED
    }

    private val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val connectivity =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    @Volatile private var running = false
    private var attempts = 0

    private val isOn: Boolean get() = runCatching { wifi.isWifiEnabled }.getOrDefault(true)

    private val isConnected: Boolean
        get() = runCatching {
            connectivity.getNetworkCapabilities(connectivity.activeNetwork)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }.getOrDefault(true)

    val hasHelper: Boolean
        get() = runCatching { context.packageManager.getPackageInfo(HELPER, 0); true }.getOrDefault(false)

    /** Watches until [stop]; [onStatus] is called from a background thread on each change. */
    fun start() {
        if (running) return
        running = true
        Thread({
            var last: Status? = null
            var offSince = 0L
            var lastTry = 0L
            while (running) {
                val now = System.currentTimeMillis()
                val status = when {
                    isConnected -> Status.CONNECTED.also { offSince = 0L }
                    isOn -> Status.WAITING_FOR_NETWORK.also { offSince = 0L }
                    else -> {
                        if (offSince == 0L) offSince = now
                        if (now - lastTry >= RETRY_MS) {
                            lastTry = now
                            tryEnable()
                        }
                        when {
                            now - offSince < GIVE_UP_MS -> Status.ENABLING
                            hasHelper -> Status.CANNOT_ENABLE
                            else -> Status.NO_HELPER
                        }
                    }
                }
                if (status != last) {
                    last = status
                    onStatus(status)
                }
                Thread.sleep(if (status == Status.CONNECTED) CONNECTED_POLL_MS else POLL_MS)
            }
        }, "wifi-watch").apply { isDaemon = true }.start()
    }

    fun stop() {
        running = false
    }

    @Suppress("DEPRECATION")
    private fun tryEnable() {
        if (hasHelper) runCatching {
            if (attempts++ % 2 == 0) {
                context.startActivity(
                    Intent().setClassName(HELPER, "$HELPER.WifiOnActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } else {
                context.sendBroadcast(
                    Intent("$HELPER.WIFI_ON").setClassName(HELPER, "$HELPER.WifiOnReceiver")
                        .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                )
            }
        }
        runCatching { Settings.Global.putInt(context.contentResolver, Settings.Global.WIFI_ON, 1) }
        runCatching { wifi.setWifiEnabled(true) }
    }

    companion object {
        const val HELPER = "com.chorockkim.conversate.wifihelper"
        const val POLL_MS = 1_000L
        const val CONNECTED_POLL_MS = 5_000L
        /** The helper activity briefly pauses this app, so not more often than this. */
        const val RETRY_MS = 10_000L
        const val GIVE_UP_MS = 8_000L

        /** Text for the pill above the band; null = nothing to show. */
        fun label(status: Status?, online: Boolean): String? = when (status) {
            Status.ENABLING -> "Wi-Fi 켜는 중…"
            Status.CANNOT_ENABLE -> "Wi-Fi 꺼짐 · 다시 켜는 중"
            Status.NO_HELPER -> "Wi-Fi 꺼짐 · 도우미 앱 없음"
            Status.WAITING_FOR_NETWORK -> "Wi-Fi 연결 기다리는 중"
            Status.CONNECTED, null -> if (online) null else "인터넷 연결 끊김"
        }
    }
}
