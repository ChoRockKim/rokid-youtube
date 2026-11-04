package com.chorockkim.rokidyoutube.ui

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction

/**
 * Bridge target for R08 Access Bridge (github.com/Anezium/R08-Access-Bridge).
 *
 * The ring sends media keys that the bridge's accessibility service consumes. For apps without a
 * dedicated integration, the bridge walks the window's clickable/focusable accessibility nodes:
 * it input-focuses one, clicks it on a single tap, and when there is no next node it performs
 * ACTION_SCROLL_FORWARD / BACKWARD on the focused node; double tap is the global Back action.
 *
 * So the app exposes exactly one node: this transparent view over the band (the screens below it
 * are hidden from accessibility). Ring next/previous arrive as scroll forward/backward and ring
 * tap as a click, and all of them are fed into the same one-axis navigation as the touchpad.
 */
class RingTarget(context: Context, private val onAction: (GlassesInput.Action) -> Unit) : View(context) {
    init {
        isFocusable = true
        isFocusableInTouchMode = true
        isClickable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = "Rokid YouTube"
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = View::class.java.name
        info.isScrollable = true
        info.isClickable = true
        info.isFocusable = true
        info.addAction(AccessibilityAction.ACTION_CLICK)
        info.addAction(AccessibilityAction.ACTION_SCROLL_FORWARD)
        info.addAction(AccessibilityAction.ACTION_SCROLL_BACKWARD)
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        val mapped = when (action) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> GlassesInput.Action.NEXT
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> GlassesInput.Action.PREVIOUS
            AccessibilityNodeInfo.ACTION_CLICK -> GlassesInput.Action.SELECT
            else -> null
        }
        if (mapped != null) {
            onAction(mapped)
            return true
        }
        return super.performAccessibilityAction(action, arguments)
    }

    /** A plain click (e.g. a tap gesture the bridge falls back to) is a select too. */
    override fun performClick(): Boolean {
        super.performClick()
        onAction(GlassesInput.Action.SELECT)
        return true
    }
}
