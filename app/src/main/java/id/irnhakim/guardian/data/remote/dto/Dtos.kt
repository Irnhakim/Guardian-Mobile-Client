package id.irnhakim.guardian.data.remote.dto

import com.google.gson.annotations.SerializedName

// ── Auth ──────────────────────────────────────────────────────

data class LoginRequest(
    val email: String,
    val password: String,
)

data class RefreshRequest(
    val refreshToken: String,
)

data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val user: UserDto,
)

data class UserDto(
    val id: String,
    val email: String,
    val name: String,
    val role: String,
)

// ── Device ────────────────────────────────────────────────────

data class RegisterDeviceRequest(
    val deviceId: String,
    val deviceName: String,
    val brand: String,
    val model: String,
    val androidVersion: String,
    val securityPatch: String? = null,
    val fcmToken: String? = null,
)

data class DeviceResponse(
    val id: String,
    val deviceId: String,
    val deviceName: String,
    val brand: String,
    val model: String,
    val androidVersion: String,
    val parentId: String,
    val status: String,
)

// ── Battery ───────────────────────────────────────────────────

data class BatteryRequest(
    val level: Int,
    val isCharging: Boolean,
    val temperature: Float? = null,
    val voltage: Int? = null,
)

// ── Location ──────────────────────────────────────────────────

data class LocationRequest(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float? = null,
    val altitude: Double? = null,
    val speed: Float? = null,
    val bearing: Float? = null,
    val provider: String? = null,
)

// ── Apps ──────────────────────────────────────────────────────

data class AppInfoDto(
    val packageName: String,
    val appName: String,
    val versionName: String? = null,
    val versionCode: Int? = null,
    val isSystemApp: Boolean = false,
)

data class SyncAppsRequest(
    val apps: List<AppInfoDto>,
)

data class AppUsageDto(
    val packageName: String,
    val appName: String,
    val usageMs: Long,
    val date: String, // YYYY-MM-DD
)

data class SyncUsageRequest(
    val usages: List<AppUsageDto>,
)

// ── Notifications ──────────────────────────────────────────────

data class NotificationRequest(
    val packageName: String,
    val appName: String,
    val title: String? = null,
    val text: String? = null,
    val category: String? = null,
)
