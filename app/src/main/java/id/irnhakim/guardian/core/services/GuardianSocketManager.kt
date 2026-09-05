package id.irnhakim.guardian.core.services

import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import id.irnhakim.guardian.core.workers.AppSyncWorker
import io.socket.client.IO
import io.socket.client.Socket
import id.irnhakim.guardian.data.local.GuardianPreferences
import id.irnhakim.guardian.data.remote.api.GuardianApi
import id.irnhakim.guardian.data.remote.dto.*
import id.irnhakim.guardian.core.utils.PermissionUtils
import android.app.usage.UsageStatsManager
import android.content.pm.ApplicationInfo
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import android.provider.Settings

class GuardianSocketManager(
    private val context: Context,
    private val serverUrl: String,
    private val deviceId: String,
    private val preferences: GuardianPreferences,
    private val api: GuardianApi,
) {

    private var socket: Socket? = null
    private val overlayManager = GuardianOverlayManager(context)

    fun connect() {
        if (socket?.connected() == true) return

        try {
            val options = IO.Options().apply {
                forceNew = true
                reconnection = true
                path = "/socket.io"
                query = "deviceId=$deviceId&role=DEVICE"
            }
            // Use the WS_URL or the serverUrl from preferences
            val wsUrl = serverUrl.replace("http://", "ws://").replace("https://", "wss://").replace("/api/v1", "")
            
            socket = IO.socket(serverUrl.replace("/api/v1", "") + "/guardian", options)

            socket?.on(Socket.EVENT_CONNECT) {
                Log.d("GuardianSocket", "Connected to Guardian WebSocket")
                // Join the device room
                socket?.emit("subscribe:device", mapOf("deviceId" to deviceId))
                syncPermissions()
            }

            socket?.on("force_sync") { args ->
                val data = args?.firstOrNull() as? JSONObject
                val target = data?.optString("target", "all") ?: "all"
                Log.d("GuardianSocket", "Received force_sync command from dashboard (target: $target)!")
                triggerSync(target)
            }

            socket?.on("device:deleted") {
                Log.d("GuardianSocket", "Device was deleted from parent dashboard! Resetting app preferences...")
                resetApp()
            }

            socket?.on("approval:resolved") { args ->
                try {
                    val data = args?.firstOrNull() as? JSONObject
                    if (data != null) {
                        val packageName = data.optString("packageName")
                        val status = data.optString("status")
                        Log.d("GuardianSocket", "Approval resolved: $packageName -> $status")
                        if (status == "APPROVED" && !packageName.isNullOrEmpty()) {
                            CoroutineScope(Dispatchers.IO).launch {
                                preferences.removeBlockedApp(packageName)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("GuardianSocket", "Error parsing approval:resolved payload", e)
                }
            }

            socket?.on("device:message") { args ->
                try {
                    val data = args?.firstOrNull() as? JSONObject
                    if (data != null) {
                        val type = data.optString("type") ?: "MESSAGE"
                        val message = data.optString("message") ?: ""
                        val password = data.optString("password")
                        Log.d("GuardianSocket", "Received device message command: $type, message: $message, password: $password")
                        
                        if (Settings.canDrawOverlays(context)) {
                            overlayManager.showMessage(type, message, password)
                        } else {
                            id.irnhakim.guardian.ui.DeviceMessageActivity.start(context, type, message, password)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("GuardianSocket", "Error parsing device:message payload", e)
                }
            }

            socket?.on("app:hide") {
                Log.d("GuardianSocket", "Received app:hide — hiding Guardian from launcher")
                setAppVisibility(false)
            }

            socket?.on("app:show") {
                Log.d("GuardianSocket", "Received app:show — showing Guardian in launcher")
                setAppVisibility(true)
            }

            socket?.on("protection:set") { args ->
                try {
                    val data = args?.firstOrNull() as? JSONObject
                    val enabled = data?.optBoolean("enabled", true) ?: true
                    Log.d("GuardianSocket", "Received protection:set — enabled: $enabled")
                    CoroutineScope(Dispatchers.IO).launch {
                        preferences.setAntiUninstallEnabled(enabled)
                        if (!enabled) {
                            // Automatically remove Device Admin so uninstall is unblocked
                            removeDeviceAdmin()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("GuardianSocket", "Error handling protection:set", e)
                }
            }

            socket?.on(Socket.EVENT_DISCONNECT) {
                Log.d("GuardianSocket", "Disconnected from Guardian WebSocket")
            }

            socket?.connect()
        } catch (e: Exception) {
            Log.e("GuardianSocket", "Failed to connect to socket", e)
        }
    }

    private fun triggerSync(target: String = "all") {
        when (target) {
            "all" -> {
                syncBattery()
                syncLocation()
                syncPermissions()
                syncApps()
                syncUsage()
            }
            "battery" -> syncBattery()
            "location" -> syncLocation()
            "apps" -> syncApps()
            "usage" -> syncUsage()
            "permissions" -> syncPermissions()
        }
    }

    private fun syncLocation() {
        if (LocationForegroundService.getInstance() != null) {
            LocationForegroundService.requestImmediateLocationNow()
        } else {
            LocationForegroundService.start(context)
        }
    }

    private fun syncApps() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val pm = context.packageManager
                val packages = pm.getInstalledPackages(PackageManager.GET_META_DATA)
                val apps = packages.map { pkg ->
                    val isSystem = pkg.applicationInfo?.flags?.and(ApplicationInfo.FLAG_SYSTEM) != 0
                    AppInfoDto(
                        packageName = pkg.packageName,
                        appName = pkg.applicationInfo?.loadLabel(pm)?.toString() ?: pkg.packageName,
                        versionName = pkg.versionName,
                        versionCode = pkg.longVersionCode.toInt(),
                        isSystemApp = isSystem,
                    )
                }
                api.syncApps(deviceId, SyncAppsRequest(apps))
                Log.d("GuardianSocket", "Direct realtime sync apps: ${apps.size} apps sent")
            } catch (e: Exception) {
                Log.e("GuardianSocket", "Direct sync apps failed", e)
            }
        }
    }

    private fun syncUsage() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return@launch
                val cal = Calendar.getInstance()
                val endTime = cal.timeInMillis
                cal.add(Calendar.DAY_OF_YEAR, -7)
                val startTime = cal.timeInMillis

                val stats = usm.queryAndAggregateUsageStats(startTime, endTime)
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val today = dateFormat.format(Date())

                val usages = stats.entries
                    .filter { it.value.totalTimeInForeground > 0 }
                    .map { entry ->
                        val pkgName = entry.key
                        val stat = entry.value
                        val appLabel = try {
                            val appInfo = context.packageManager.getApplicationInfo(pkgName, 0)
                            context.packageManager.getApplicationLabel(appInfo).toString()
                        } catch (e: Exception) {
                            pkgName
                        }
                        AppUsageDto(
                            packageName = pkgName,
                            appName = appLabel,
                            usageMs = stat.totalTimeInForeground,
                            date = today,
                        )
                    }

                if (usages.isNotEmpty()) {
                    api.syncUsage(deviceId, SyncUsageRequest(usages))
                    Log.d("GuardianSocket", "Direct realtime sync usage: ${usages.size} items sent")
                }
            } catch (e: Exception) {
                Log.e("GuardianSocket", "Direct sync usage failed", e)
            }
        }
    }

    private fun syncBattery() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                val batteryStatus = context.registerReceiver(null, intentFilter) ?: return@launch

                val level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale) else return@launch

                val status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL
                val rawTemp = batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
                val temperature = if (rawTemp > 0) rawTemp / 10f else null
                val voltage = batteryStatus.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
                    .takeIf { it > 0 }

                api.submitBattery(
                    deviceId,
                    BatteryRequest(
                        level = batteryPct,
                        isCharging = isCharging,
                        temperature = temperature,
                        voltage = voltage,
                    )
                )
                Log.d("GuardianSocket", "Force sync battery: $batteryPct% charging=$isCharging")
            } catch (e: Exception) {
                Log.e("GuardianSocket", "Force sync battery failed", e)
            }
        }
    }

    fun syncPermissions() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val checklist = PermissionUtils.getPermissionChecklist(context)
                api.updateDevice(deviceId, UpdateDeviceRequest(permissions = checklist))
                Log.d("GuardianSocket", "Permissions synced to server: $checklist")
            } catch (e: Exception) {
                Log.e("GuardianSocket", "Failed to sync permissions", e)
            }
        }
    }

    private fun resetApp() {
        CoroutineScope(Dispatchers.IO).launch {
            preferences.clear()
        }
    }

    /**
     * Hide or show the Guardian app icon in the device launcher.
     * When hidden (visible=false), the app icon disappears from the app drawer.
     * The background service (location tracking, socket) keeps running.
     * The admin can show it again remotely via app:show socket event.
     */
    private fun setAppVisibility(visible: Boolean) {
        try {
            val pm = context.packageManager
            val mainActivity = ComponentName(context, "id.irnhakim.guardian.ui.MainActivity")
            val newState = if (visible)
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            pm.setComponentEnabledSetting(
                mainActivity,
                newState,
                PackageManager.DONT_KILL_APP
            )
            Log.d("GuardianSocket", "App visibility set to: ${if (visible) "VISIBLE" else "HIDDEN"}")
        } catch (e: Exception) {
            Log.e("GuardianSocket", "Failed to set app visibility", e)
        }
    }

    private fun removeDeviceAdmin() {
        try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
            val adminComponent = ComponentName(context, id.irnhakim.guardian.core.receivers.GuardianDeviceAdminReceiver::class.java)
            if (dpm.isAdminActive(adminComponent)) {
                dpm.removeActiveAdmin(adminComponent)
                Log.d("GuardianSocket", "Device Admin successfully removed via server command")
            }
        } catch (e: Exception) {
            Log.e("GuardianSocket", "Failed to remove device admin", e)
        }
    }

    fun disconnect() {
        socket?.disconnect()
        socket = null
    }
}
