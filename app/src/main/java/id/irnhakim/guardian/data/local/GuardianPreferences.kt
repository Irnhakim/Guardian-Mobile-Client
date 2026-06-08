package id.irnhakim.guardian.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GuardianPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        val KEY_ACCESS_TOKEN = stringPreferencesKey("access_token")
        val KEY_REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val KEY_DEVICE_ID = stringPreferencesKey("device_id")
        val KEY_SERVER_DEVICE_ID = stringPreferencesKey("server_device_id")
        val KEY_PARENT_EMAIL = stringPreferencesKey("parent_email")
        val KEY_PARENT_PASSWORD = stringPreferencesKey("parent_password") // encrypted in production
        val KEY_SERVER_URL = stringPreferencesKey("server_url")
        val KEY_BLOCKED_APPS = stringPreferencesKey("blocked_apps")
    }

    val accessToken: Flow<String?> = dataStore.data.map { it[KEY_ACCESS_TOKEN] }
    val refreshToken: Flow<String?> = dataStore.data.map { it[KEY_REFRESH_TOKEN] }
    val deviceId: Flow<String?> = dataStore.data.map { it[KEY_DEVICE_ID] }
    val serverDeviceId: Flow<String?> = dataStore.data.map { it[KEY_SERVER_DEVICE_ID] }
    val serverUrl: Flow<String?> = dataStore.data.map { it[KEY_SERVER_URL] }
    val blockedApps: Flow<Set<String>> = dataStore.data.map { prefs ->
        val csv = prefs[KEY_BLOCKED_APPS] ?: ""
        if (csv.isEmpty()) emptySet() else csv.split(",").toSet()
    }

    fun getBlockedAppsSync(): Set<String> = runBlocking {
        val csv = dataStore.data.map { it[KEY_BLOCKED_APPS] }.first() ?: ""
        if (csv.isEmpty()) emptySet() else csv.split(",").toSet()
    }

    suspend fun addBlockedApp(packageName: String) {
        dataStore.edit { prefs ->
            val current = prefs[KEY_BLOCKED_APPS] ?: ""
            val set = if (current.isEmpty()) mutableSetOf() else current.split(",").toMutableSet()
            set.add(packageName)
            prefs[KEY_BLOCKED_APPS] = set.joinToString(",")
        }
    }

    suspend fun removeBlockedApp(packageName: String) {
        dataStore.edit { prefs ->
            val current = prefs[KEY_BLOCKED_APPS] ?: ""
            val set = if (current.isEmpty()) mutableSetOf() else current.split(",").toMutableSet()
            set.remove(packageName)
            prefs[KEY_BLOCKED_APPS] = set.joinToString(",")
        }
    }

    fun getAccessTokenSync(): String? = runBlocking { dataStore.data.first()[KEY_ACCESS_TOKEN] }
    fun getRefreshTokenSync(): String? = runBlocking { dataStore.data.first()[KEY_REFRESH_TOKEN] }
    fun getDeviceIdSync(): String? = runBlocking { dataStore.data.first()[KEY_DEVICE_ID] }
    fun getServerDeviceIdSync(): String? = runBlocking { dataStore.data.first()[KEY_SERVER_DEVICE_ID] }
    fun getServerUrlSync(): String? = runBlocking { dataStore.data.first()[KEY_SERVER_URL] }

    suspend fun saveTokens(access: String, refresh: String) {
        dataStore.edit { prefs ->
            prefs[KEY_ACCESS_TOKEN] = access
            prefs[KEY_REFRESH_TOKEN] = refresh
        }
    }

    suspend fun saveDeviceId(deviceId: String) {
        dataStore.edit { it[KEY_DEVICE_ID] = deviceId }
    }

    suspend fun saveServerDeviceId(id: String) {
        dataStore.edit { it[KEY_SERVER_DEVICE_ID] = id }
    }

    suspend fun saveCredentials(email: String, password: String) {
        dataStore.edit { prefs ->
            prefs[KEY_PARENT_EMAIL] = email
            prefs[KEY_PARENT_PASSWORD] = password
        }
    }

    suspend fun saveServerUrl(url: String) {
        dataStore.edit { it[KEY_SERVER_URL] = url }
    }

    suspend fun getCredentials(): Pair<String?, String?> {
        val prefs = dataStore.data.first()
        return prefs[KEY_PARENT_EMAIL] to prefs[KEY_PARENT_PASSWORD]
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }
}

