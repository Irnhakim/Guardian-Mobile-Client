package id.irnhakim.guardian

import android.app.Application
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import id.irnhakim.guardian.di.dataStore
import javax.inject.Inject

@HiltAndroidApp
class GuardianApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    // Expose the singleton DataStore for system-bound services (NotificationListenerService, etc.)
    // that cannot use Hilt injection.
    val appDataStore: DataStore<Preferences> get() = dataStore
}
