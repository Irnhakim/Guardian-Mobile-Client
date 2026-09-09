package id.irnhakim.guardian.core.services

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import id.irnhakim.guardian.BuildConfig
import id.irnhakim.guardian.GuardianApp
import id.irnhakim.guardian.ui.AppBlockActivity
import id.irnhakim.guardian.data.remote.api.GuardianApi
import id.irnhakim.guardian.data.remote.dto.BrowsingHistoryRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class GuardianAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private var lastCapturedUrl: String? = null
    private var lastCapturedTime: Long = 0

    private val dataStore get() = (applicationContext as GuardianApp).appDataStore

    private fun getServerDeviceId(): String? = runBlocking {
        dataStore.data.first()[stringPreferencesKey("server_device_id")]
    }

    private fun isAntiUninstallEnabled(): Boolean = runBlocking {
        dataStore.data.first()[booleanPreferencesKey("anti_uninstall_enabled")] ?: true
    }

    private fun getBlockedApps(): Set<String> = runBlocking {
        dataStore.data.first()[stringSetPreferencesKey("blocked_apps")] ?: emptySet()
    }

    private val api: GuardianApi by lazy {
        val savedUrl = runBlocking {
            dataStore.data.first()[stringPreferencesKey("server_url")]
        }
        val base = if (!savedUrl.isNullOrEmpty()) {
            val url = savedUrl.trimEnd('/')
            if (url.endsWith("/api/v1")) "$url/" else "$url/api/v1/"
        } else {
            BuildConfig.API_BASE_URL.trimEnd('/') + "/"
        }
        Retrofit.Builder()
            .baseUrl(base)
            .client(OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GuardianApi::class.java)
    }

    companion object {
        // Whitelist aplikasi perbankan & e-wallet Indonesia / global
        // Jangan pernah sentuh window content saat app ini aktif agar tidak dicap keylogger/overlay malware
        private val BANKING_PACKAGES = setOf(
            "com.bca", "id.co.bca.mybca", "com.bca.bcamobile",
            "id.bmri.livin", "id.co.bankmandiri.livin",
            "id.co.bri.brimo", "com.bankbni.mobile",
            "com.cimbniaga.octomobile", "com.btpn.jenius",
            "id.dana", "com.telkom.indihome.ui", "com.ovo",
            "com.shopee.id", "com.tokopedia.tkpd", "com.lazada.android",
            "com.bankbsi.mobile", "com.permata.mobilex", "com.seabank.id"
        )

        private val BROWSER_PACKAGES = setOf(
            "com.android.chrome",
            "org.mozilla.firefox",
            "com.microsoft.emmx",
            "com.sec.android.app.sbrowser",
            "com.brave.browser",
            "com.opera.browser"
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // Watchdog: keep LocationForegroundService alive whenever system fires accessibility events
        ensureGuardianServiceAlive()

        val packageName = event.packageName?.toString() ?: ""
        if (packageName == "id.irnhakim.guardian") return

        // Bypass total jika aplikasi yang aktif adalah perbankan / e-wallet
        if (BANKING_PACKAGES.contains(packageName) || packageName.contains("bank", ignoreCase = true)) {
            return
        }

        try {
            if (!isAntiUninstallEnabled()) return
        } catch (e: Exception) {
            // If preferences check fails, continue default protection
        }

        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return

        // App blocking check (event-driven, replaces 1s polling in LocationForegroundService)
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            checkAndBlockApp(packageName)
        }

        // Browser URL capture
        if (BROWSER_PACKAGES.contains(packageName)) {
            captureBrowserUrl(packageName)
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
            val blockedApps = getBlockedApps()
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

    private fun captureBrowserUrl(packageName: String) {
        val rootNode = rootInActiveWindow ?: return
        try {
            val url = findUrlInNode(rootNode, packageName) ?: return
            if (url.isBlank() || url == lastCapturedUrl) return

            val now = System.currentTimeMillis()
            // Throttle duplicate/rapid events minimal 3 detik
            if (now - lastCapturedTime < 3000 && url == lastCapturedUrl) return

            lastCapturedUrl = url
            lastCapturedTime = now

            val deviceId = getServerDeviceId() ?: return
            Log.d("AccessibilityService", "Extracted browser URL: $url for device: $deviceId")
            serviceScope.launch {
                try {
                    api.submitBrowsingHistory(
                        deviceId,
                        BrowsingHistoryRequest(
                            url = url,
                            browser = packageName
                        )
                    )
                    Log.d("AccessibilityService", "Successfully submitted browsing URL: $url")
                } catch (e: Exception) {
                    Log.e("AccessibilityService", "Failed to submit browsing URL: $url", e)
                }
            }
        } finally {
            try { rootNode.recycle() } catch (e: Exception) {}
        }
    }

    private fun findUrlInNode(node: AccessibilityNodeInfo?, packageName: String): String? {
        if (node == null) return null

        // Coba cari langsung dengan viewId umum
        val directIds = listOf(
            "$packageName:id/url_bar",
            "$packageName:id/location_bar",
            "$packageName:id/search_box",
            "$packageName:id/toolbar_url"
        )

        for (id in directIds) {
            val matches = node.findAccessibilityNodeInfosByViewId(id)
            if (!matches.isNullOrEmpty()) {
                for (target in matches) {
                    val text = target.text?.toString()
                    if (!text.isNullOrBlank() && (text.contains(".") || text.startsWith("http"))) {
                        return text
                    }
                }
            }
        }

        // Fallback: traversal recursive
        return recursiveScanUrl(node)
    }

    private fun recursiveScanUrl(node: AccessibilityNodeInfo?): String? {
        if (node == null) return null

        val viewId = node.viewIdResourceName ?: ""
        if (viewId.contains("url_bar", ignoreCase = true) ||
            viewId.contains("toolbar_url", ignoreCase = true) ||
            viewId.contains("location_bar", ignoreCase = true) ||
            viewId.contains("search_box", ignoreCase = true)
        ) {
            val text = node.text?.toString()
            if (!text.isNullOrBlank() && (text.contains(".") || text.startsWith("http"))) {
                return text
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val result = recursiveScanUrl(child)
            try { child?.recycle() } catch (e: Exception) {}
            if (result != null) return result
        }

        return null
    }

    private fun ensureGuardianServiceAlive() {
        if (LocationForegroundService.getInstance() == null) {
            val deviceId = getServerDeviceId()
            if (!deviceId.isNullOrEmpty()) {
                Log.d("AccessibilityService", "Watchdog: restarting LocationForegroundService...")
                LocationForegroundService.start(applicationContext)
            }
        }
    }
}
