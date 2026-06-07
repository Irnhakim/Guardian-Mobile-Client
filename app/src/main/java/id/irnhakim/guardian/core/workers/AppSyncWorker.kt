package id.irnhakim.guardian.core.workers

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.hilt.work.HiltWorker
import androidx.work.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import id.irnhakim.guardian.data.local.GuardianPreferences
import id.irnhakim.guardian.data.remote.api.GuardianApi
import id.irnhakim.guardian.data.remote.dto.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

@HiltWorker
class AppSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val api: GuardianApi,
    private val preferences: GuardianPreferences,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val deviceId = preferences.getServerDeviceIdSync() ?: return Result.retry()
        val pm = applicationContext.packageManager

        // 1. Sync installed apps
        try {
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
        } catch (e: Exception) {
            // Continue to usage sync even if app list fails
        }

        // 2. Sync usage stats (requires PACKAGE_USAGE_STATS permission)
        try {
            val usm = applicationContext.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val cal = Calendar.getInstance()
            val endTime = cal.timeInMillis
            cal.add(Calendar.DAY_OF_YEAR, -7)
            val startTime = cal.timeInMillis

            val stats = usm.queryAndAggregateUsageStats(startTime, endTime)
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val today = dateFormat.format(Date())

            val usages = stats
                .filter { (_, stat) -> stat.totalTimeInForeground > 0 }
                .map { (pkg, stat) ->
                    val label = try {
                        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                    } catch (e: Exception) { pkg }

                    AppUsageDto(
                        packageName = pkg,
                        appName = label,
                        usageMs = stat.totalTimeInForeground,
                        date = today,
                    )
                }

            if (usages.isNotEmpty()) {
                api.syncUsage(deviceId, SyncUsageRequest(usages))
            }
        } catch (e: Exception) {
            // Usage stats might not be available (permission not granted)
        }

        return Result.success()
    }

    companion object {
        const val WORK_NAME = "guardian_app_sync_worker"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<AppSyncWorker>(
                30, TimeUnit.MINUTES
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
