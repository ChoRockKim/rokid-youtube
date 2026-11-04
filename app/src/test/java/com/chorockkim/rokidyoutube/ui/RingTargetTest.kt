package com.chorockkim.rokidyoutube.ui

import android.view.accessibility.AccessibilityNodeInfo
import app.cash.paparazzi.Paparazzi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * R08 Access Bridge drives non-integrated apps through accessibility: focus the node, scroll
 * forward/backward when there is no next node, click on tap. Check RingTarget answers those.
 */
class RingTargetTest {
    @get:Rule
    val paparazzi = Paparazzi()

    @Test
    fun bridgeActionsBecomeGlassesActions() {
        val received = mutableListOf<GlassesInput.Action>()
        val target = RingTarget(paparazzi.context) { received += it }

        assertTrue(target.performAccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD, null))
        assertTrue(target.performAccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD, null))
        assertTrue(target.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null))

        assertEquals(
            listOf(GlassesInput.Action.NEXT, GlassesInput.Action.PREVIOUS, GlassesInput.Action.SELECT),
            received
        )
    }

    @Test
    fun nodeLooksLikeAScrollableClickableCandidate() {
        val target = RingTarget(paparazzi.context) {}
        assertTrue(target.isFocusable)
        assertTrue(target.isClickable)
        assertEquals(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_YES, target.importantForAccessibility)
    }
}
