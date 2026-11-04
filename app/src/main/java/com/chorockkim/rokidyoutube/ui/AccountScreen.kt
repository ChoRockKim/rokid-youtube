package com.chorockkim.rokidyoutube.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.chorockkim.rokidyoutube.auth.GoogleAuth
import com.chorockkim.rokidyoutube.ui.GlassesInput.Action
import com.chorockkim.rokidyoutube.ui.Hud.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Google account link. Signed out: shows google.com/device and a code to type on the phone, then
 * waits for approval. Signed in: sign out or clear the watch history kept on the glasses.
 */
class AccountScreen(
    context: Context,
    private val navigator: Navigator,
    private val scope: CoroutineScope,
    private val auth: GoogleAuth,
    private val clearHistory: () -> Unit,
    private val onAccountChanged: () -> Unit
) : Screen {
    override val view = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        // `view` is not set yet here, so use the Context extension, not Screen.dp().
        setPadding(context.dp(16), context.dp(10), context.dp(16), context.dp(8))
    }

    private val title = Hud.text(context, 15f, bold = true).apply { gravity = Gravity.CENTER }
    private val code = Hud.text(context, 34f, Hud.GREEN, bold = true).apply {
        gravity = Gravity.CENTER
        letterSpacing = 0.12f
    }
    private val hint = Hud.text(context, 13f, Hud.GREY).apply {
        gravity = Gravity.CENTER
        setLineSpacing(0f, 1.15f)
    }
    private val buttonRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
    }
    private val buttons = mutableListOf<Pair<TextView, () -> Unit>>()
    private var cursor = 0
    private var job: Job? = null

    init {
        view.addView(title, LinearLayout.LayoutParams(-1, -2))
        view.addView(code, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        view.addView(hint, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        view.addView(View(context), LinearLayout.LayoutParams(-1, 0, 1f))
        view.addView(buttonRow, LinearLayout.LayoutParams(-1, dp(36)))
    }

    override fun onShown() = render()

    private fun render() {
        job?.cancel()
        when {
            !auth.isConfigured -> showNotConfigured()
            auth.isSignedIn -> showSignedIn()
            else -> startDeviceLogin()
        }
    }

    private fun showNotConfigured() {
        title.text = "구글 로그인 키가 없어요"
        code.visibility = View.GONE
        hint.text = "local.properties에 googleClientId / googleClientSecret를\n넣고 다시 빌드해 주세요 (README 참고)"
        setButtons("닫기" to { navigator.back() })
    }

    private fun showSignedIn() {
        title.text = "구글 계정 연결됨"
        code.visibility = View.GONE
        hint.text = "구독 · 좋아요 탭이 내 계정 기준으로 보여요\n추천 · 이어보기는 이 안경의 시청 기록으로 만들어요"
        setButtons(
            "닫기" to { navigator.back() },
            "로그아웃" to {
                scope.launch {
                    auth.signOut()
                    onAccountChanged()
                    render()
                }
            },
            "기록 지우기" to {
                clearHistory()
                hint.text = "이 안경의 시청 기록을 지웠어요"
            }
        )
    }

    private fun startDeviceLogin() {
        title.text = "폰에서 google.com/device 열고 코드 입력"
        code.visibility = View.VISIBLE
        code.text = "······"
        hint.text = "코드를 받는 중…"
        setButtons("취소" to { navigator.back() })
        job = scope.launch {
            try {
                val device = auth.requestDeviceCode()
                code.text = device.userCode
                title.text = "폰에서 ${device.verificationUrl.removePrefix("https://").removePrefix("www.")} 열고 코드 입력"
                hint.text = "구글 계정으로 로그인하고 'YouTube 보기' 권한을 허용하면\n자동으로 연결돼요"
                when (auth.waitForApproval(device)) {
                    GoogleAuth.PollResult.SignedIn -> {
                        onAccountChanged()
                        navigator.back()
                    }
                    GoogleAuth.PollResult.Denied -> showRetry("권한을 거부했어요")
                    else -> showRetry("코드가 만료됐어요")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showRetry("연결하지 못했어요 (${e.message ?: e.javaClass.simpleName})")
            }
        }
    }

    private fun showRetry(message: String) {
        code.visibility = View.GONE
        title.text = message
        hint.text = ""
        setButtons("다시 시도" to { render() }, "닫기" to { navigator.back() })
    }

    private fun setButtons(vararg items: Pair<String, () -> Unit>) {
        buttonRow.removeAllViews()
        buttons.clear()
        items.forEachIndexed { i, (label, action) ->
            val button = Hud.button(view.context, label)
            buttonRow.addView(button, LinearLayout.LayoutParams(dp(110), dp(34)).apply {
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

    override fun onHidden() {
        job?.cancel()
    }

    override fun onDestroy() {
        job?.cancel()
    }

    /** For snapshots: show a device code without talking to Google. */
    internal fun showCodeForPreview(userCode: String) {
        title.text = "폰에서 google.com/device 열고 코드 입력"
        code.visibility = View.VISIBLE
        code.text = userCode
        hint.text = "구글 계정으로 로그인하고 'YouTube 보기' 권한을 허용하면\n자동으로 연결돼요"
        setButtons("취소" to {})
    }
}
