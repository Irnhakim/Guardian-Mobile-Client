package id.irnhakim.guardian.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.AndroidEntryPoint
import id.irnhakim.guardian.core.services.LocationForegroundService
import id.irnhakim.guardian.core.workers.AppSyncWorker
import id.irnhakim.guardian.core.workers.BatteryWorker
import id.irnhakim.guardian.data.local.GuardianPreferences
import id.irnhakim.guardian.ui.navigation.GuardianNavGraph
import id.irnhakim.guardian.ui.theme.GuardianTheme
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var preferences: GuardianPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Automatically start service and schedule workers as soon as device is registered
        lifecycleScope.launch {
            preferences.serverDeviceId.collect { deviceId ->
                if (!deviceId.isNullOrEmpty()) {
                    LocationForegroundService.start(this@MainActivity)
                    
                    // Schedule periodic background tasks
                    BatteryWorker.schedule(this@MainActivity)
                    AppSyncWorker.schedule(this@MainActivity)

                    // Trigger immediate one-time sync for battery, installed apps, and usage stats
                    val workManager = WorkManager.getInstance(this@MainActivity)
                    workManager.enqueue(OneTimeWorkRequestBuilder<BatteryWorker>().build())
                    workManager.enqueue(OneTimeWorkRequestBuilder<AppSyncWorker>().build())
                }
            }
        }

        setContent {
            GuardianTheme {
                GuardianNavGraph()
            }
        }
    }
}
