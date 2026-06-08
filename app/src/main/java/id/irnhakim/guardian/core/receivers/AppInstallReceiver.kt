package id.irnhakim.guardian.core.receivers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import id.irnhakim.guardian.data.local.GuardianPreferences
import id.irnhakim.guardian.data.remote.api.GuardianApi
import id.irnhakim.guardian.data.remote.dto.CreateApprovalRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AppInstallReceiver(
    private val api: GuardianApi,
    private val preferences: GuardianPreferences
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_PACKAGE_ADDED) return

        val packageName = intent.data?.schemeSpecificPart ?: return
        val isReplacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
        if (isReplacing) return

        val pm = context.packageManager
        val appName = try {
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName
        }

        // Avoid blocking pre-installed system packages/updates
        if (isSystemApp(context, packageName)) return

        val installer = getInstallerPackageName(context, packageName)
        Log.d("AppInstallReceiver", "Package added: $packageName ($appName), Installer: $installer")

        // Block if not installed via Google Play Store (com.android.vending)
        if (installer != "com.android.vending") {
            CoroutineScope(Dispatchers.IO).launch {
                val deviceId = preferences.getServerDeviceIdSync() ?: return@launch
                
                // 1. Persist in local blocked list
                preferences.addBlockedApp(packageName)

                // 2. Notify the backend server
                try {
                    api.submitApproval(
                        deviceId = deviceId,
                        request = CreateApprovalRequest(
                            packageName = packageName,
                            appName = appName,
                            installer = installer ?: "Unknown (sideloaded/ADB)"
                        )
                    )
                    Log.d("AppInstallReceiver", "Successfully submitted approval request for $packageName")
                } catch (e: Exception) {
                    Log.e("AppInstallReceiver", "Error sending approval request to backend", e)
                }

                // 3. Post a block alert notification
                showBlockedNotification(context, packageName, appName)
            }
        }
    }

    private fun isSystemApp(context: Context, packageName: String): Boolean {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        } catch (e: Exception) {
            false
        }
    }

    private fun getInstallerPackageName(context: Context, packageName: String): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.packageManager.getInstallSourceInfo(packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getInstallerPackageName(packageName)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun showBlockedNotification(context: Context, packageName: String, appName: String) {
        val channelId = "guardian_app_block"
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (manager.getNotificationChannel(channelId) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        channelId,
                        "Pencegahan Instalasi Aplikasi",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Notifikasi untuk aplikasi yang diblokir karena dipasang dari luar Play Store"
                    }
                )
            }
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle("Aplikasi Diblokir")
            .setContentText("Aplikasi '$appName' memerlukan persetujuan orang tua.")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        manager.notify(packageName.hashCode(), notification)
    }
}
