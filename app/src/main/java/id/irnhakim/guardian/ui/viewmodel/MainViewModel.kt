package id.irnhakim.guardian.ui.viewmodel

import android.app.Application
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fasterxml.uuid.Generators
import dagger.hilt.android.lifecycle.HiltViewModel
import id.irnhakim.guardian.data.local.GuardianPreferences
import id.irnhakim.guardian.data.remote.api.GuardianApi
import id.irnhakim.guardian.data.remote.dto.RegisterDeviceRequest
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    application: Application,
    private val api: GuardianApi,
    private val preferences: GuardianPreferences,
) : AndroidViewModel(application) {

    val isRegistered: StateFlow<Boolean> = preferences.serverDeviceId
        .map { !it.isNullOrEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun autoRegister() {
        Log.d("MainViewModel", "autoRegister called")
        viewModelScope.launch {
            try {
                Log.d("MainViewModel", "autoRegister coroutine started")
                val serverId = preferences.getServerDeviceIdSync()
                Log.d("MainViewModel", "current serverId=$serverId")
                if (!serverId.isNullOrEmpty()) return@launch

                val deviceId = preferences.getDeviceIdSync()
                    ?: Generators.randomBasedGenerator().generate().toString()
                        .also { preferences.saveDeviceId(it) }

                Log.d("MainViewModel", "Registering device: deviceId=$deviceId")
                val response = api.registerDevice(
                    RegisterDeviceRequest(
                        deviceId = deviceId,
                        deviceName = "${Build.MANUFACTURER} ${Build.MODEL}",
                        brand = Build.MANUFACTURER,
                        model = Build.MODEL,
                        androidVersion = Build.VERSION.RELEASE,
                        securityPatch = Build.VERSION.SECURITY_PATCH,
                    )
                )

                if (response.isSuccessful && response.body() != null) {
                    val registeredDeviceId = response.body()!!.deviceId
                    preferences.saveServerDeviceId(registeredDeviceId)
                    Log.d("MainViewModel", "Device auto-registered successfully: $registeredDeviceId")
                } else {
                    Log.e("MainViewModel", "Auto-registration failed: code=${response.code()} err=${response.errorBody()?.string()}")
                }
            } catch (e: Throwable) {
                Log.e("MainViewModel", "Auto-registration error: ${e.message}", e)
            }
        }
    }
}
