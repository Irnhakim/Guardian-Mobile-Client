package id.irnhakim.guardian.di

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import id.irnhakim.guardian.BuildConfig
import id.irnhakim.guardian.data.local.GuardianPreferences
import id.irnhakim.guardian.data.remote.api.GuardianApi
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

val Context.dataStore by preferencesDataStore(name = "guardian_prefs")

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideOkHttp(preferences: GuardianPreferences): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request()
                val serverUrl = preferences.getServerUrlSync()
                val newRequest = if (!serverUrl.isNullOrEmpty()) {
                    val parsedUrl = serverUrl.toHttpUrlOrNull()
                    if (parsedUrl != null) {
                        val newUrl = request.url.newBuilder()
                            .scheme(parsedUrl.scheme)
                            .host(parsedUrl.host)
                            .port(parsedUrl.port)
                            .build()
                        request.newBuilder().url(newUrl).build()
                    } else request
                } else request

                val token = preferences.getAccessTokenSync()
                val finalRequest = if (!token.isNullOrEmpty()) {
                    newRequest.newBuilder()
                        .addHeader("Authorization", "Bearer $token")
                        .build()
                } else newRequest
                chain.proceed(finalRequest)
            }
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
                    else HttpLoggingInterceptor.Level.NONE
                }
            )
            .build()

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL + "/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides
    @Singleton
    fun provideGuardianApi(retrofit: Retrofit): GuardianApi =
        retrofit.create(GuardianApi::class.java)

    @Provides
    @Singleton
    fun providePreferences(@ApplicationContext ctx: Context): GuardianPreferences =
        GuardianPreferences(ctx.dataStore)
}
