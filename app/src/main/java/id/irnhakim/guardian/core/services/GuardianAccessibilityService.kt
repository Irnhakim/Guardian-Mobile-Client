package id.irnhakim.guardian.core.services

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

class GuardianAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return

        val packageName = event.packageName?.toString() ?: ""

        // Target Settings applications across Android configurations (Oppo, Samsung, Pixel, etc.)
        if (packageName.contains("settings", ignoreCase = true) || packageName == "com.android.settings") {
            val rootNode = rootInActiveWindow ?: return
            try {
                if (scanNodesForText(rootNode, listOf("id.irnhakim.guardian", "Guardian"))) {
                    Log.d("AccessibilityService", "Detected attempt to access Guardian app settings! Redirecting home...")
                    // Redirect to home screen to close the settings app details
                    performGlobalAction(GLOBAL_ACTION_HOME)
                }
            } finally {
                rootNode.recycle()
            }
        }
    }

    private fun scanNodesForText(node: AccessibilityNodeInfo?, targetTexts: List<String>): Boolean {
        if (node == null) return false
        
        val text = node.text?.toString() ?: ""
        val contentDescription = node.contentDescription?.toString() ?: ""
        val viewId = node.viewIdResourceName ?: ""

        for (target in targetTexts) {
            if (text.contains(target, ignoreCase = true) ||
                contentDescription.contains(target, ignoreCase = true) ||
                viewId.contains(target, ignoreCase = true)) {
                return true
            }
        }

        val childCount = node.childCount
        for (i in 0 until childCount) {
            val child = node.getChild(i)
            if (scanNodesForText(child, targetTexts)) {
                child?.recycle()
                return true
            }
            child?.recycle()
        }
        return false
    }

    override fun onInterrupt() {
        Log.d("AccessibilityService", "Accessibility Service Interrupted")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("AccessibilityService", "Accessibility Service Connected")
    }
}
