package id.irnhakim.guardian.core.services

import android.app.Notification
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import id.irnhakim.guardian.data.local.GuardianPreferences
import id.irnhakim.guardian.data.remote.api.GuardianApi
import id.irnhakim.guardian.data.remote.dto.NotificationRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class GuardianNotificationListener : NotificationListenerService() {

    @Inject
    lateinit var api: GuardianApi

    @Inject
    lateinit var preferences: GuardianPreferences

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        Log.d("GuardianNotification", "Notification listener created")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        // Skip self/Guardian notifications to avoid loop
        val packageName = sbn.packageName
        if (packageName == this.packageName) return

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        // Read notification title and body text
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()

        // Skip if title and text are both empty
        if (title.isNullOrEmpty() && text.isNullOrEmpty()) return

        val category = notification.category

        // Read app display name
        val pm = packageManager
        val appName = try {
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName
        }

        serviceScope.launch {
            val deviceId = preferences.getServerDeviceIdSync() ?: return@launch
            try {
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
}
