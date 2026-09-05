package id.irnhakim.guardian.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
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

    private val viewModel: id.irnhakim.guardian.ui.viewmodel.MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Explicitly touch viewModel so lazy by viewModels() initializes immediately
        viewModel.autoRegister()

        // Automatically start service and schedule workers as soon as device is registered
        lifecycleScope.launch {
            preferences.serverDeviceId.collect { deviceId ->
                if (!deviceId.isNullOrEmpty()) {
                    val hasLocation = androidx.core.content.ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        android.Manifest.permission.ACCESS_FINE_LOCATION
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
                    androidx.core.content.ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        android.Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                    if (hasLocation) {
                        LocationForegroundService.start(this@MainActivity)
                    }

                    // Schedule periodic background tasks
                    BatteryWorker.schedule(this@MainActivity)
                    AppSyncWorker.schedule(this@MainActivity)

                    // Trigger immediate one-time sync for battery, installed apps, and usage stats
                    val workManager = WorkManager.getInstance(this@MainActivity)
                    workManager.enqueue(OneTimeWorkRequestBuilder<BatteryWorker>().build())
                    workManager.enqueue(OneTimeWorkRequestBuilder<AppSyncWorker>().build())
                } else {
                    // Stop foreground services and cancel periodic tasks if device deleted
                    LocationForegroundService.stop(this@MainActivity)
                    val workManager = WorkManager.getInstance(this@MainActivity)
                    workManager.cancelUniqueWork(BatteryWorker.WORK_NAME)
                    workManager.cancelUniqueWork(AppSyncWorker.WORK_NAME)
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
