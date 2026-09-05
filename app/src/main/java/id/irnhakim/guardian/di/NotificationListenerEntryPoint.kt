package id.irnhakim.guardian.di

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import id.irnhakim.guardian.data.local.GuardianPreferences
import id.irnhakim.guardian.data.remote.api.GuardianApi

@EntryPoint
@InstallIn(SingletonComponent::class)
interface NotificationListenerEntryPoint {
    fun api(): GuardianApi
    fun preferences(): GuardianPreferences
}
