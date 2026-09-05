package id.irnhakim.guardian.core.services

import android.app.Notification
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import id.irnhakim.guardian.BuildConfig
import id.irnhakim.guardian.GuardianApp
import id.irnhakim.guardian.data.remote.dto.NotificationRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import id.irnhakim.guardian.data.remote.api.GuardianApi
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit

// ponytail: Retrofit built manually (no Hilt) — system-bound service; DataStore via GuardianApp singleton
class GuardianNotificationListener : NotificationListenerService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Reuse the app-level singleton DataStore — avoids "multiple DataStores active" crash
    private val dataStore get() = (applicationContext as GuardianApp).appDataStore

    private fun getServerDeviceId(): String? = runBlocking {
        dataStore.data.first()[stringPreferencesKey("server_device_id")]
    }

    private fun getServerUrl(): String? = runBlocking {
        dataStore.data.first()[stringPreferencesKey("server_url")]
    }

    private val api: GuardianApi by lazy {
        val savedUrl = getServerUrl()
        val base = if (!savedUrl.isNullOrEmpty()) {
            // server_url may or may not include /api/v1 — normalize
            val url = savedUrl.trimEnd('/')
            if (url.endsWith("/api/v1")) "$url/" else "$url/api/v1/"
        } else {
            BuildConfig.API_BASE_URL.trimEnd('/') + "/"
        }
        Log.d("GuardianNotification", "Retrofit baseUrl: $base")
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

    override fun onCreate() {
        super.onCreate()
        Log.d("GuardianNotification", "Notification listener created")
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d("GuardianNotification", "Notification listener connected")
        ensureGuardianServiceAlive()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        ensureGuardianServiceAlive()

        if (sbn == null) return
        val packageName = sbn.packageName
        if (packageName == this.packageName) return

        // Auto-dismiss system overlay warning about Guardian
        if (packageName == "android") {
            val extras = sbn.notification?.extras
            val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
            val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            if (text.contains("displaying over", ignoreCase = true) ||
                title.contains("displaying over", ignoreCase = true)) {
                cancelNotification(sbn.key)
                return
            }
        }

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        if (title.isNullOrEmpty() && text.isNullOrEmpty()) return

        val category = notification.category
        val appName = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName
        }

        serviceScope.launch {
            try {
                val deviceId = getServerDeviceId()
                Log.d("GuardianNotification", "Sending notif from $appName, deviceId=$deviceId")
                if (deviceId == null) {
                    Log.w("GuardianNotification", "deviceId null — skipping")
                    return@launch
                }
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
                    Log.d("GuardianNotification", "Notification from $appName synced")
                } else {
                    Log.e("GuardianNotification", "Sync failed: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e("GuardianNotification", "Error syncing notification", e)
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
                val deviceId = getServerDeviceId()
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

