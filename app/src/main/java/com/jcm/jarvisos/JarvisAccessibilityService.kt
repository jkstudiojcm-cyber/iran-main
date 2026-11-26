package com.jcm.jarvisos

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class JarvisAccessibilityService : AccessibilityService() {

    companion object {
        var instance: JarvisAccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    fun performBack() {
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    fun performHome() {
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    fun performScroll(down: Boolean) {
        val displayMetrics = resources.displayMetrics
        val midX = displayMetrics.widthPixels / 2f
        val startY = if (down) displayMetrics.heightPixels * 0.75f else displayMetrics.heightPixels * 0.25f
        val endY = if (down) displayMetrics.heightPixels * 0.25f else displayMetrics.heightPixels * 0.75f

        val path = Path().apply {
            moveTo(midX, startY)
            lineTo(midX, endY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 300))
            .build()
        dispatchGesture(gesture, null, null)
    }

    fun clickNodeByText(targetText: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val nodes = rootNode.findAccessibilityNodeInfosByText(targetText)
        for (node in nodes) {
            if (clickNodeOrParent(node)) return true
        }
        return false
    }

    private fun clickNodeOrParent(node: AccessibilityNodeInfo?): Boolean {
        var current = node
        while (current != null) {
            if (current.isClickable) {
                return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            current = current.parent
        }
        return false
    }
}
