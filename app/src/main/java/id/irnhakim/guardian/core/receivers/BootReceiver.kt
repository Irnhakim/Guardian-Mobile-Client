package id.irnhakim.guardian.core.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import id.irnhakim.guardian.core.services.LocationForegroundService
import id.irnhakim.guardian.core.workers.AppSyncWorker
import id.irnhakim.guardian.core.workers.BatteryWorker
import id.irnhakim.guardian.data.local.GuardianPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var preferences: GuardianPreferences

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        CoroutineScope(Dispatchers.IO).launch {
            val deviceId = preferences.getServerDeviceIdSync()
            if (deviceId != null) {
                // Restart all background services/workers on boot
                LocationForegroundService.start(context)
                BatteryWorker.schedule(context)
                AppSyncWorker.schedule(context)
            }
        }
    }
}
