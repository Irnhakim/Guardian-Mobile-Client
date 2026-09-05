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
        val KEY_DEVICE_ID = stringPreferencesKey("device_id")
        val KEY_SERVER_DEVICE_ID = stringPreferencesKey("server_device_id")
        val KEY_SERVER_URL = stringPreferencesKey("server_url")
        val KEY_BLOCKED_APPS = stringPreferencesKey("blocked_apps")
        val KEY_ANTI_UNINSTALL_ENABLED = androidx.datastore.preferences.core.booleanPreferencesKey("anti_uninstall_enabled")
    }

    val deviceId: Flow<String?> = dataStore.data.map { it[KEY_DEVICE_ID] }
    val serverDeviceId: Flow<String?> = dataStore.data.map { it[KEY_SERVER_DEVICE_ID] }
    val serverUrl: Flow<String?> = dataStore.data.map { it[KEY_SERVER_URL] }
    val antiUninstallEnabled: Flow<Boolean> = dataStore.data.map { it[KEY_ANTI_UNINSTALL_ENABLED] ?: true }
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

    fun getDeviceIdSync(): String? = runBlocking { dataStore.data.first()[KEY_DEVICE_ID] }
    fun getServerDeviceIdSync(): String? = runBlocking { dataStore.data.first()[KEY_SERVER_DEVICE_ID] }
    fun getServerUrlSync(): String? = runBlocking { dataStore.data.first()[KEY_SERVER_URL] }
    fun isAntiUninstallEnabledSync(): Boolean = runBlocking { dataStore.data.first()[KEY_ANTI_UNINSTALL_ENABLED] ?: true }

    suspend fun setAntiUninstallEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_ANTI_UNINSTALL_ENABLED] = enabled }
    }

    suspend fun saveDeviceId(deviceId: String) {
        dataStore.edit { it[KEY_DEVICE_ID] = deviceId }
    }

    suspend fun saveServerDeviceId(id: String) {
        dataStore.edit { it[KEY_SERVER_DEVICE_ID] = id }
    }

    suspend fun saveServerUrl(url: String) {
        dataStore.edit { it[KEY_SERVER_URL] = url }
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }
}
