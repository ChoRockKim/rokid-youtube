package org.schabi.newpipe

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.rokid.RokidMode

class DebugRokidStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_DUMP_STATE -> dumpState(context)
            ACTION_RESET_STATE -> resetState(context)
        }
    }

    private fun dumpState(context: Context) {
        val activity = DebugApp.getCurrentActivity()
        val root = activity?.window?.decorView
        val focused = activity?.currentFocus
        val state = JSONObject()
            .put("packageName", context.packageName)
            .put("buildType", BuildConfig.BUILD_TYPE)
            .put("versionName", BuildConfig.VERSION_NAME)
            .put("versionCode", BuildConfig.VERSION_CODE)
            .put("rokidMode", RokidMode.enabled())
            .put("activity", activity?.javaClass?.name ?: JSONObject.NULL)
            .put("focused", viewToJson(activity, focused))
            .put("keyboardVisible", hasShownViewWithId(root, R.id.rokidKeyboardOverlay))
            .put("playerRailVisible", hasShownViewWithId(root, R.id.rokidActionRail))
            .put("playerMenuVisible", hasShownViewWithId(root, R.id.rokidPlayerMenuOverlay))
            .put("focusableActionCount", countFocusableActions(root))
            .put("focusableActions", focusableActions(activity, root))
            .put("unnamedFocusableActions", unnamedFocusableActions(activity, root))

        stateFile(context).writeText(state.toString(), Charsets.UTF_8)
        Log.i(TAG, state.toString())
    }

    private fun resetState(context: Context) {
        stateFile(context).delete()
        Log.i(TAG, "{\"reset\":true}")
    }

    private fun viewToJson(activity: Activity?, view: View?): JSONObject {
        if (activity == null || view == null) {
            return JSONObject().put("present", false)
        }
        return JSONObject()
            .put("present", true)
            .put("className", view.javaClass.name)
            .put("idName", resourceName(activity, view.id))
            .put("label", labelOf(view))
            .put("text", if (view is TextView) view.text?.toString() else JSONObject.NULL)
            .put("contentDescription", view.contentDescription?.toString() ?: JSONObject.NULL)
            .put("clickable", view.isClickable)
            .put("focusable", view.isFocusable)
            .put("focused", view.isFocused)
            .put("selected", view.isSelected)
            .put("shown", view.isShown)
    }

    private fun focusableActions(activity: Activity?, root: View?): JSONArray {
        val actions = JSONArray()
        walk(root) { view ->
            if (view.isShown &&
                view.isEnabled &&
                view.isFocusable &&
                (view.isClickable || view.isLongClickable)
            ) {
                actions.put(actionToJson(activity, view))
            }
        }
        return actions
    }

    private fun unnamedFocusableActions(activity: Activity?, root: View?): JSONArray {
        val actions = JSONArray()
        walk(root) { view ->
            if (view.isShown &&
                view.isEnabled &&
                view.isFocusable &&
                (view.isClickable || view.isLongClickable) &&
                labelOf(view).isBlank()
            ) {
                actions.put(actionToJson(activity, view))
            }
        }
        return actions
    }

    private fun countFocusableActions(root: View?): Int {
        var count = 0
        walk(root) { view ->
            if (view.isShown &&
                view.isEnabled &&
                view.isFocusable &&
                (view.isClickable || view.isLongClickable)
            ) {
                count += 1
            }
        }
        return count
    }

    private fun hasShownViewWithId(root: View?, id: Int): Boolean {
        var found = false
        walk(root) { view ->
            if (view.id == id && view.isShown) {
                found = true
            }
        }
        return found
    }

    private fun walk(root: View?, visit: (View) -> Unit) {
        if (root == null) {
            return
        }
        visit(root)
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) {
                walk(root.getChildAt(index), visit)
            }
        }
    }

    private fun labelOf(view: View): String {
        val text = if (view is TextView) view.text?.toString().orEmpty() else ""
        val hint = if (view is TextView) view.hint?.toString().orEmpty() else ""
        val description = view.contentDescription?.toString().orEmpty()
        return description.ifBlank { text }.ifBlank { hint }.trim()
    }

    private fun actionToJson(activity: Activity?, view: View): JSONObject = JSONObject()
        .put("className", view.javaClass.name)
        .put("idName", resourceName(activity, view.id))
        .put("label", labelOf(view))
        .put("clickable", view.isClickable)
        .put("longClickable", view.isLongClickable)
        .put("focused", view.isFocused)
        .put("selected", view.isSelected)
        .put("bounds", boundsOf(view))
        .put("accessibilityActions", accessibilityActionsToJson(activity, view))

    private fun accessibilityActionsToJson(activity: Activity?, view: View): JSONArray {
        val actions = JSONArray()
        val nodeInfo = view.createAccessibilityNodeInfo() ?: return actions
        for (action in nodeInfo.actionList) {
            actions.put(accessibilityActionToJson(activity, action))
        }
        return actions
    }

    private fun accessibilityActionToJson(
        activity: Activity?,
        action: AccessibilityNodeInfo.AccessibilityAction
    ): JSONObject = JSONObject()
        .put("id", action.id)
        .put("idName", resourceName(activity, action.id))
        .put("label", action.label?.toString() ?: JSONObject.NULL)

    private fun boundsOf(view: View): String {
        val rect = Rect()
        view.getGlobalVisibleRect(rect)
        return "[${rect.left},${rect.top}][${rect.right},${rect.bottom}]"
    }

    private fun resourceName(activity: Activity?, id: Int): Any {
        if (activity == null || id == View.NO_ID) {
            return JSONObject.NULL
        }
        return try {
            activity.resources.getResourceName(id)
        } catch (ignored: RuntimeException) {
            JSONObject.NULL
        }
    }

    private fun stateFile(context: Context): File = File(context.filesDir, "rokid-debug-state.json")

    companion object {
        const val ACTION_DUMP_STATE =
            "com.anezium.rokid.newpipe.debug.DUMP_ROKID_STATE"
        const val ACTION_RESET_STATE =
            "com.anezium.rokid.newpipe.debug.RESET_ROKID_STATE"
        private const val TAG = "RokidDebugState"
    }
}
