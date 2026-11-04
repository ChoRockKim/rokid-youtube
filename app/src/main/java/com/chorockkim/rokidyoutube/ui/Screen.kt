package com.chorockkim.rokidyoutube.ui

import android.view.View
import com.chorockkim.rokidyoutube.ui.Hud.dp

/** One page shown inside the HUD band. The activity keeps a back stack of these. */
interface Screen {
    val view: View

    /** @return true if the action was consumed. BACK not consumed pops the screen. */
    fun onAction(action: GlassesInput.Action): Boolean

    fun onShown() {}
    fun onHidden() {}
    fun onDestroy() {}

    /** dp -> px for code running in the screen itself (outside a View receiver). */
    fun dp(value: Number): Int = view.context.dp(value)
}

/** What screens can ask the host activity to do. */
interface Navigator {
    fun openSearch()
    fun openPlayer(url: String, title: String)
    fun back()
    fun startVoiceSearch(onResult: (String) -> Unit)
}
