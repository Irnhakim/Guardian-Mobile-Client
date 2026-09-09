package id.irnhakim.guardian.core.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import id.irnhakim.guardian.core.services.LocationForegroundService
import id.irnhakim.guardian.data.local.GuardianPreferences
import java.util.concurrent.TimeUnit

@HiltWorker
class WatchdogWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val preferences: GuardianPreferences,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val deviceId = preferences.getServerDeviceIdSync() ?: return Result.success()
        if (deviceId.isBlank()) return Result.success()

        if (LocationForegroundService.getInstance() == null) {
            android.util.Log.d("WatchdogWorker", "LocationForegroundService not running — restarting")
            LocationForegroundService.start(applicationContext)
        }

        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "guardian_watchdog"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<WatchdogWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
