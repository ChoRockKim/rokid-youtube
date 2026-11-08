package com.chorockkim.rokidyoutube.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.chorockkim.rokidyoutube.ui.GlassesInput.Action
import com.chorockkim.rokidyoutube.ui.Hud.dp
import com.chorockkim.rokidyoutube.update.InstallResultReceiver
import com.chorockkim.rokidyoutube.update.Release
import com.chorockkim.rokidyoutube.update.Updater
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * App update: check GitHub releases, download + verify, then the system installer.
 * Same layout as the account screen: title, big value, hint, a row of buttons.
 */
class UpdateScreen(
    context: Context,
    private val navigator: Navigator,
    private val scope: CoroutineScope,
    /** null in snapshot tests. */
    private val updater: Updater?,
    private val onChecked: (Release?) -> Unit = {}
) : Screen {
    override val view = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(context.dp(16), context.dp(10), context.dp(16), context.dp(8))
    }

    private val title = Hud.text(context, 15f, bold = true).apply { gravity = Gravity.CENTER }
    private val value = Hud.text(context, 30f, Hud.GREEN, bold = true).apply { gravity = Gravity.CENTER }
    private val hint = Hud.text(context, 12f, Hud.GREY).apply {
        gravity = Gravity.CENTER
        setLineSpacing(0f, 1.15f)
        maxLines = 4
    }
    private val buttonRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
    }
    private val buttons = mutableListOf<Pair<TextView, () -> Unit>>()
    private var cursor = 0
    private var job: Job? = null
    private var downloaded: File? = null
    /** Waiting for the user to allow installs in system settings. */
    private var awaitingPermission = false
    /** The system installer is showing / ran: its result comes through the receiver. */
    private var installStarted = false
    private var started = false

    init {
        view.addView(title, LinearLayout.LayoutParams(-1, -2))
        view.addView(value, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        view.addView(hint, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        view.addView(View(context), LinearLayout.LayoutParams(-1, 0, 1f))
        view.addView(buttonRow, LinearLayout.LayoutParams(-1, dp(36)))
    }

    override fun onShown() {
        InstallResultReceiver.listener = { result -> view.post { onInstallResult(result) } }
        val apk = downloaded
        when {
            // Back from the system "allow installs" screen.
            awaitingPermission && apk != null -> {
                awaitingPermission = false
                install(apk)
            }
            // Back from the system install screen, or a check / download still running.
            installStarted || job?.isActive == true -> Unit
            // Snapshots set a state before showing; don't overwrite it.
            !started -> {
                started = true
                check()
            }
            else -> Unit
        }
    }

    override fun onHidden() {
        InstallResultReceiver.listener = null
    }

    override fun onDestroy() {
        job?.cancel()
        InstallResultReceiver.listener = null
    }

    private fun check() {
        val u = updater ?: return
        show("업데이트 확인 중…", "", "지금 버전 v${u.currentVersionName}")
        setButtons("닫기" to { navigator.back() })
        job = scope.launch {
            try {
                when (val result = u.check(force = true)) {
                    is Updater.Check.Available -> {
                        onChecked(result.release)
                        showAvailable(result.release)
                    }
                    Updater.Check.UpToDate -> {
                        onChecked(null)
                        show("최신 버전이에요", "v${u.currentVersionName}", "")
                        setButtons("닫기" to { navigator.back() }, "다시 확인" to { check() })
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(e.message ?: "업데이트를 확인하지 못했어요")
            }
        }
    }

    internal fun showAvailable(release: Release) {
        val notes = release.notes.lines().map { it.trim().removePrefix("-").removePrefix("*").trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("**Full Changelog") }
            .take(3).joinToString("\n")
        val current = updater?.currentVersionName ?: "0.1.0"
        show("새 버전이 있어요", "v${release.versionName}", notes.ifEmpty { "지금 버전 v$current" })
        setButtons("받아서 설치" to { download(release) }, "나중에" to { navigator.back() })
    }

    private fun download(release: Release) {
        val u = updater ?: return
        show("받는 중", "0%", "v${release.versionName} · ${release.apkSize / 1_000_000}MB")
        setButtons("취소" to {
            job?.cancel()
            showAvailable(release)
        })
        job = scope.launch {
            try {
                val apk = u.download(release) { percent ->
                    value.text = if (percent >= 0) "$percent%" else "…"
                }
                downloaded = apk
                install(apk)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(e.message ?: "받지 못했어요")
            }
        }
    }

    private fun install(apk: File) {
        val u = updater ?: return
        if (!u.canInstall()) {
            awaitingPermission = true
            show(
                "설치 허용이 필요해요",
                "",
                "다음 화면에서 이 앱의 '출처 허용'을 켜고\n뒤로 돌아오면 바로 설치해요"
            )
            setButtons("설정 열기" to { u.openInstallPermissionSettings() }, "취소" to { navigator.back() })
            return
        }
        installStarted = true
        show("설치하는 중", "", "시스템 설치 화면이 뜨면 '업데이트'를 눌러 주세요\n설치가 끝나면 앱이 닫혀요. 다시 열어 주세요")
        setButtons("닫기" to { navigator.back() })
        job = scope.launch {
            try {
                u.install(apk)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail("설치를 시작하지 못했어요 (${e.message ?: e.javaClass.simpleName})")
            }
        }
    }

    private fun onInstallResult(result: InstallResultReceiver.Result) {
        when (result) {
            InstallResultReceiver.Result.WaitingForUser -> Unit
            InstallResultReceiver.Result.Installed -> show("설치 완료", "", "앱을 다시 열어 주세요")
            is InstallResultReceiver.Result.Failed -> fail("설치 실패: ${result.message}")
        }
    }

    private fun fail(message: String) {
        installStarted = false
        show("업데이트 실패", "", message)
        setButtons("다시 시도" to { check() }, "닫기" to { navigator.back() })
    }

    private fun show(titleText: String, valueText: String, hintText: String) {
        title.text = titleText
        value.text = valueText
        value.visibility = if (valueText.isEmpty()) View.GONE else View.VISIBLE
        hint.text = hintText
    }

    private fun setButtons(vararg items: Pair<String, () -> Unit>) {
        buttonRow.removeAllViews()
        buttons.clear()
        items.forEachIndexed { i, (label, action) ->
            val button = Hud.button(view.context, label)
            buttonRow.addView(button, LinearLayout.LayoutParams(dp(120), dp(34)).apply {
                if (i > 0) marginStart = dp(8)
            })
            buttons += button to action
        }
        moveTo(0)
    }

    private fun moveTo(index: Int) {
        if (buttons.isEmpty()) return
        cursor = (index + buttons.size) % buttons.size
        buttons.forEachIndexed { i, (b, _) -> b.isSelected = i == cursor }
    }

    override fun onAction(action: Action): Boolean = when (action) {
        Action.NEXT -> { moveTo(cursor + 1); true }
        Action.PREVIOUS -> { moveTo(cursor - 1); true }
        Action.SELECT -> { buttons.getOrNull(cursor)?.second?.invoke(); true }
        else -> false
    }
}
