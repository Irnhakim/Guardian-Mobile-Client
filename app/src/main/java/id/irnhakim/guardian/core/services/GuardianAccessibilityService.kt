package id.irnhakim.guardian.core.services

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import dagger.hilt.android.AndroidEntryPoint
import id.irnhakim.guardian.ui.AppBlockActivity
import id.irnhakim.guardian.data.local.GuardianPreferences
import javax.inject.Inject

@AndroidEntryPoint
class GuardianAccessibilityService : AccessibilityService() {

    @Inject lateinit var preferences: GuardianPreferences

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // Watchdog: keep LocationForegroundService alive whenever system fires accessibility events
        ensureGuardianServiceAlive()

        val packageName = event.packageName?.toString() ?: ""
        if (packageName == "id.irnhakim.guardian") return

        try {
            if (!preferences.isAntiUninstallEnabledSync()) return
        } catch (e: Exception) {
            // If preferences check fails, continue default protection
        }

        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return

        // App blocking check (event-driven, replaces 1s polling in LocationForegroundService)
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            checkAndBlockApp(packageName)
        }

        // Target Settings applications, Package Installers, and App Uninstaller dialogs
        val isTargetApp = packageName.contains("settings", ignoreCase = true) ||
                packageName == "com.android.settings" ||
                packageName.contains("packageinstaller", ignoreCase = true) ||
                packageName.contains("permissioncontroller", ignoreCase = true)

        if (isTargetApp) {
            val rootNode = rootInActiveWindow ?: return
            try {
                val targets = listOf("id.irnhakim.guardian", "Guardian", "guardian")
                if (scanNodesForText(rootNode, targets)) {
                    Log.d("AccessibilityService", "Detected attempt to access/uninstall Guardian! Redirecting home...")
                    performGlobalAction(GLOBAL_ACTION_HOME)
                }
            } catch (e: Exception) {
                // Ignore
            } finally {
                try { rootNode.recycle() } catch (e: Exception) {}
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
        ensureGuardianServiceAlive()
    }

    private fun checkAndBlockApp(packageName: String) {
        try {
            val blockedApps = preferences.getBlockedAppsSync()
            if (blockedApps.contains(packageName)) {
                val pm = packageManager
                val appLabel = try {
                    val appInfo = pm.getApplicationInfo(packageName, 0)
                    pm.getApplicationLabel(appInfo).toString()
                } catch (e: Exception) {
                    packageName
                }
                AppBlockActivity.start(this, packageName, appLabel)
            }
        } catch (e: Exception) {
            // Ignore
        }
    }

    private fun ensureGuardianServiceAlive() {
        if (LocationForegroundService.getInstance() == null) {
            val deviceId = preferences.getServerDeviceIdSync()
            if (!deviceId.isNullOrEmpty()) {
                Log.d("AccessibilityService", "Watchdog: restarting LocationForegroundService...")
                LocationForegroundService.start(applicationContext)
            }
        }
    }
}
