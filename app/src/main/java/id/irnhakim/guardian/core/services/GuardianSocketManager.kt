package id.irnhakim.guardian.core.services

import android.content.Context
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import id.irnhakim.guardian.core.workers.AppSyncWorker
import id.irnhakim.guardian.core.workers.BatteryWorker
import io.socket.client.IO
import io.socket.client.Socket
import id.irnhakim.guardian.data.local.GuardianPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class GuardianSocketManager(
    private val context: Context,
    private val serverUrl: String,
    private val deviceId: String,
    private val preferences: GuardianPreferences
) {

    private var socket: Socket? = null

    fun connect() {
        if (socket?.connected() == true) return

        try {
            val options = IO.Options().apply {
                forceNew = true
                reconnection = true
                path = "/socket.io"
                query = "deviceId=$deviceId&role=DEVICE"
            }
            // Use the WS_URL or the serverUrl from preferences
            val wsUrl = serverUrl.replace("http://", "ws://").replace("https://", "wss://").replace("/api/v1", "")
            
            socket = IO.socket(serverUrl.replace("/api/v1", "") + "/guardian", options)

            socket?.on(Socket.EVENT_CONNECT) {
                Log.d("GuardianSocket", "Connected to Guardian WebSocket")
                // Join the device room
                socket?.emit("subscribe:device", mapOf("deviceId" to deviceId))
            }

            socket?.on("force_sync") {
                Log.d("GuardianSocket", "Received force_sync command from dashboard!")
                triggerSync()
            }

            socket?.on("device:deleted") {
                Log.d("GuardianSocket", "Device was deleted from parent dashboard! Resetting app preferences...")
                resetApp()
            }

            socket?.on(Socket.EVENT_DISCONNECT) {
                Log.d("GuardianSocket", "Disconnected from Guardian WebSocket")
            }

            socket?.connect()
        } catch (e: Exception) {
            Log.e("GuardianSocket", "Failed to connect to socket", e)
        }
    }

    private fun triggerSync() {
        val workManager = WorkManager.getInstance(context)
        workManager.enqueue(OneTimeWorkRequestBuilder<BatteryWorker>().build())
        workManager.enqueue(OneTimeWorkRequestBuilder<AppSyncWorker>().build())
        
        // Also force a location update if location service is running
        LocationForegroundService.start(context)
    }

    private fun resetApp() {
        CoroutineScope(Dispatchers.IO).launch {
            preferences.clear()
        }
    }

    fun disconnect() {
        socket?.disconnect()
        socket = null
    }
}
