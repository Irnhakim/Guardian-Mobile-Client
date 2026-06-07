package id.irnhakim.guardian.core.services

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.google.android.gms.location.LocationRequest as GmsLocationRequest
import dagger.hilt.android.AndroidEntryPoint
import id.irnhakim.guardian.data.local.GuardianPreferences
import id.irnhakim.guardian.data.remote.api.GuardianApi
import id.irnhakim.guardian.data.remote.dto.LocationRequest as LocationDto
import kotlinx.coroutines.*
import javax.inject.Inject

@AndroidEntryPoint
class LocationForegroundService : Service() {

    @Inject lateinit var api: GuardianApi
    @Inject lateinit var preferences: GuardianPreferences

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { location ->
                serviceScope.launch {
                    sendLocation(location)
                }
            }
        }
    }

    private var socketManager: GuardianSocketManager? = null

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        startForeground(NOTIFICATION_ID, buildNotification())
        startLocationUpdates()

        // Initialize Socket connection
        serviceScope.launch {
            val serverUrl = preferences.getServerUrlSync() ?: return@launch
            val deviceId = preferences.getServerDeviceIdSync() ?: return@launch
            socketManager = GuardianSocketManager(this@LocationForegroundService, serverUrl, deviceId)
            socketManager?.connect()
        }
    }

    private fun startLocationUpdates() {
        val request = GmsLocationRequest.Builder(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            15 * 60 * 1000L // 15 minutes
        )
            .setMinUpdateIntervalMillis(60 * 1000L)
            .setWaitForAccurateLocation(false)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(
                request, locationCallback, Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            stopSelf()
        }
    }

    private suspend fun sendLocation(location: android.location.Location) {
        val deviceId = preferences.getServerDeviceIdSync() ?: return
        try {
            api.submitLocation(
                deviceId,
                LocationDto(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracy = location.accuracy,
                    altitude = location.altitude.takeIf { it != 0.0 },
                    speed = location.speed.takeIf { it > 0 },
                    bearing = location.bearing.takeIf { it > 0 },
                    provider = location.provider,
                )
            )
        } catch (e: Exception) {
            // Retry on next location update
        }
    }

    private fun buildNotification(): Notification {
        val channelId = "guardian_location"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(channelId) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    "Guardian Location",
                    NotificationManager.IMPORTANCE_LOW
                ).apply { description = "Keeps Guardian location tracking active" }
            )
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Guardian Active")
            .setContentText("Monitoring device location")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        socketManager?.disconnect()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            context.startForegroundService(Intent(context, LocationForegroundService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LocationForegroundService::class.java))
        }
    }
}
