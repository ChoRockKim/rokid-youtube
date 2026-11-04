/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app

import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import kotlinx.serialization.json.Json
import net.newpipe.Constants
import net.newpipe.app.navigation.Destination
import net.newpipe.app.theme.currentService

/**
 * Entry point for compose-related UI components on Android
 */
class ComposeActivity : ComponentActivity() {
    private var lastDirectionTimestamp = 0L

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN
            && event.repeatCount > 0
            && isDirectionalKey(event.keyCode)
        ) {
            return true
        }

        if (event.keyCode == KEYCODE_ROKID_DOUBLE_TAP) {
            return super.dispatchKeyEvent(event.withKeyCode(KeyEvent.KEYCODE_DPAD_CENTER))
        }

        if (event.keyCode == KEYCODE_ROKID_SWIPE_FORWARD) {
            return super.dispatchKeyEvent(event.withKeyCode(KeyEvent.KEYCODE_DPAD_RIGHT))
        }

        if (event.keyCode == KEYCODE_ROKID_SWIPE_BACK) {
            return super.dispatchKeyEvent(event.withKeyCode(KeyEvent.KEYCODE_DPAD_LEFT))
        }

        return super.dispatchKeyEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val focusManager = LocalFocusManager.current
            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(focusRequester)
                    .focusable()
                    .onKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) {
                            return@onKeyEvent false
                        }

                        when (mapRokidAction(event.key)) {
                            RokidAction.NEXT -> {
                                focusManager.moveFocus(FocusDirection.Next)
                                true
                            }
                            RokidAction.PREVIOUS -> {
                                focusManager.moveFocus(FocusDirection.Previous)
                                true
                            }
                            RokidAction.DUPLICATE -> true
                            RokidAction.NONE -> false
                        }
                    }
            ) {
                App(
                    // TODO: Change when everything is in compose and this is the primary activity
                    startDestination = Json.decodeFromString<Destination>(
                        intent.getStringExtra(Constants.INTENT_SCREEN_KEY)!!
                    ),
                    onCloseRequest = ::finish
                ) {
                    val view = LocalView.current
                    val service = currentService()

                    DisposableEffect(service) {
                        val windowController = WindowCompat.getInsetsController(window, view)
                        windowController.isAppearanceLightStatusBars =
                            service.isSchemeColorDensityLight
                        onDispose {
                            windowController.isAppearanceLightStatusBars = false
                        }
                    }
                }
            }
        }
    }

    private fun mapRokidAction(key: Key): RokidAction {
        val action = when (key) {
            Key.DirectionRight,
            Key.DirectionDown -> RokidAction.NEXT
            Key.DirectionLeft,
            Key.DirectionUp -> RokidAction.PREVIOUS
            else -> RokidAction.NONE
        }

        if (action == RokidAction.NONE) {
            return RokidAction.NONE
        }

        val now = SystemClock.uptimeMillis()
        if (lastDirectionTimestamp > 0L
            && now - lastDirectionTimestamp <= DIRECTION_DEBOUNCE_MS
        ) {
            return RokidAction.DUPLICATE
        }

        lastDirectionTimestamp = now
        return action
    }

    private fun isDirectionalKey(keyCode: Int) = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_DPAD_UP,
        KEYCODE_ROKID_SWIPE_FORWARD,
        KEYCODE_ROKID_SWIPE_BACK -> true
        else -> false
    }

    private fun KeyEvent.withKeyCode(keyCode: Int) = KeyEvent(
        downTime,
        eventTime,
        action,
        keyCode,
        repeatCount,
        metaState,
        deviceId,
        scanCode,
        flags,
        source
    )

    private enum class RokidAction {
        NONE,
        NEXT,
        PREVIOUS,
        DUPLICATE
    }

    private companion object {
        private const val KEYCODE_ROKID_SWIPE_FORWARD = 183
        private const val KEYCODE_ROKID_SWIPE_BACK = 184
        private const val KEYCODE_ROKID_DOUBLE_TAP = 202
        private const val DIRECTION_DEBOUNCE_MS = 275L
    }
}
