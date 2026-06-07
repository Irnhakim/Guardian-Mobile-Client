package id.irnhakim.guardian.ui.viewmodel

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fasterxml.uuid.Generators
import dagger.hilt.android.lifecycle.HiltViewModel
import id.irnhakim.guardian.data.local.GuardianPreferences
import id.irnhakim.guardian.data.remote.api.GuardianApi
import id.irnhakim.guardian.data.remote.dto.LoginRequest
import id.irnhakim.guardian.data.remote.dto.RegisterDeviceRequest
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SetupState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val step: SetupStep = SetupStep.CREDENTIALS,
)

enum class SetupStep { CREDENTIALS, REGISTERING, DONE }

@HiltViewModel
class MainViewModel @Inject constructor(
    application: Application,
    private val api: GuardianApi,
    private val preferences: GuardianPreferences,
) : AndroidViewModel(application) {

    val isRegistered: StateFlow<Boolean> = preferences.serverDeviceId
        .map { !it.isNullOrEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _setupState = MutableStateFlow(SetupState())
    val setupState: StateFlow<SetupState> = _setupState.asStateFlow()

    fun setup(email: String, password: String, serverUrl: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            _setupState.update { it.copy(isLoading = true, error = null) }

            // Save the server URL first, so the OkHttp Interceptor will dynamically redirect the Retrofit instance immediately.
            preferences.saveServerUrl(serverUrl)

            // 1. Login as parent
            val loginResult = runCatching {
                api.login(LoginRequest(email, password))
            }.getOrElse {
                _setupState.update { s -> s.copy(isLoading = false, error = "Cannot reach server: ${it.message}") }
                return@launch
            }

            if (!loginResult.isSuccessful) {
                _setupState.update { it.copy(isLoading = false, error = "Invalid credentials") }
                return@launch
            }

            val tokens = loginResult.body()!!
            preferences.saveTokens(tokens.accessToken, tokens.refreshToken)
            preferences.saveCredentials(email, password)

            // 2. Generate or reuse device ID
            val deviceId = preferences.getDeviceIdSync()
                ?: Generators.randomBasedGenerator().generate().toString()
                    .also { preferences.saveDeviceId(it) }

            // 3. Register device
            _setupState.update { it.copy(step = SetupStep.REGISTERING) }
            val registerResult = runCatching {
                api.registerDevice(
                    RegisterDeviceRequest(
                        deviceId = deviceId,
                        deviceName = "${Build.MANUFACTURER} ${Build.MODEL}",
                        brand = Build.MANUFACTURER,
                        model = Build.MODEL,
                        androidVersion = Build.VERSION.RELEASE,
                        securityPatch = Build.VERSION.SECURITY_PATCH,
                    )
                )
            }.getOrElse {
                _setupState.update { s -> s.copy(isLoading = false, error = "Registration failed: ${it.message}") }
                return@launch
            }

            if (!registerResult.isSuccessful) {
                _setupState.update { it.copy(isLoading = false, error = "Device registration failed") }
                return@launch
            }

            preferences.saveServerDeviceId(registerResult.body()!!.deviceId)
            _setupState.update { it.copy(isLoading = false, step = SetupStep.DONE) }
            onComplete()
        }
    }
}
