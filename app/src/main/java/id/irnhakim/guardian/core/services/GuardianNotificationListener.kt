package id.irnhakim.guardian.core.services

import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import dagger.hilt.android.EntryPointAccessors
import id.irnhakim.guardian.di.NotificationListenerEntryPoint
import id.irnhakim.guardian.data.remote.dto.NotificationRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class GuardianNotificationListener : NotificationListenerService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Retrieve Hilt dependencies via EntryPoint instead of @Inject
    // (NotificationListenerService is system-bound; @AndroidEntryPoint injection is unreliable)
    private val entryPoint by lazy {
        EntryPointAccessors.fromApplication(
            applicationContext,
            NotificationListenerEntryPoint::class.java
        )
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("GuardianNotification", "Notification listener created")
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d("GuardianNotification", "Notification listener connected — starting watchdog")
        ensureGuardianServiceAlive()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        // Watchdog: revive location & socket if stopped
        ensureGuardianServiceAlive()

        if (sbn == null) return

        // Skip self/Guardian notifications to avoid loop
        val packageName = sbn.packageName
        if (packageName == this.packageName) return

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()

        if (title.isNullOrEmpty() && text.isNullOrEmpty()) return

        val category = notification.category

        val pm = packageManager
        val appName = try {
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName
        }

        serviceScope.launch {
            try {
                val api = entryPoint.api()
                val preferences = entryPoint.preferences()
                val deviceId = preferences.getServerDeviceIdSync() ?: return@launch
                val response = api.submitNotification(
                    deviceId,
                    NotificationRequest(
                        packageName = packageName,
                        appName = appName,
                        title = title,
                        text = text,
                        category = category
                    )
                )
                if (response.isSuccessful) {
                    Log.d("GuardianNotification", "Notification from $appName synced successfully")
                } else {
                    Log.e("GuardianNotification", "Failed to sync notification from $appName: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e("GuardianNotification", "Error syncing notification from $appName", e)
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun ensureGuardianServiceAlive() {
        if (LocationForegroundService.getInstance() == null) {
            try {
                val preferences = entryPoint.preferences()
                val deviceId = preferences.getServerDeviceIdSync()
                if (!deviceId.isNullOrEmpty()) {
                    Log.d("GuardianNotification", "Watchdog: restarting LocationForegroundService...")
                    LocationForegroundService.start(applicationContext)
                }
            } catch (e: Exception) {
                Log.e("GuardianNotification", "Watchdog failed", e)
            }
        }
    }
}
