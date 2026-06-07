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
    }

    val accessToken: Flow<String?> = dataStore.data.map { it[KEY_ACCESS_TOKEN] }
    val refreshToken: Flow<String?> = dataStore.data.map { it[KEY_REFRESH_TOKEN] }
    val deviceId: Flow<String?> = dataStore.data.map { it[KEY_DEVICE_ID] }
    val serverDeviceId: Flow<String?> = dataStore.data.map { it[KEY_SERVER_DEVICE_ID] }
    val serverUrl: Flow<String?> = dataStore.data.map { it[KEY_SERVER_URL] }

    fun getAccessTokenSync(): String? = runBlocking { dataStore.data.first()[KEY_ACCESS_TOKEN] }
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

