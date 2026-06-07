package id.irnhakim.guardian.data.remote.api

import id.irnhakim.guardian.data.remote.dto.*
import retrofit2.Response
import retrofit2.http.*

interface GuardianApi {

    // ── Auth ────────────────────────────────────────────────
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @POST("auth/refresh")
    suspend fun refresh(@Body request: RefreshRequest): Response<AuthResponse>

    // ── Device ──────────────────────────────────────────────
    @POST("devices/register")
    suspend fun registerDevice(@Body request: RegisterDeviceRequest): Response<DeviceResponse>

    @POST("devices/{deviceId}/battery")
    suspend fun submitBattery(
        @Path("deviceId") deviceId: String,
        @Body request: BatteryRequest,
    ): Response<Unit>

    @POST("devices/{deviceId}/location")
    suspend fun submitLocation(
        @Path("deviceId") deviceId: String,
        @Body request: LocationRequest,
    ): Response<Unit>

    @POST("devices/{deviceId}/apps/sync")
    suspend fun syncApps(
        @Path("deviceId") deviceId: String,
        @Body request: SyncAppsRequest,
    ): Response<Unit>

    @POST("devices/{deviceId}/usage/sync")
    suspend fun syncUsage(
        @Path("deviceId") deviceId: String,
        @Body request: SyncUsageRequest,
    ): Response<Unit>
}
