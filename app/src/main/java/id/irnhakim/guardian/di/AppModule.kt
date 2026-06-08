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
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
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

                var response = chain.proceed(finalRequest)

                if (response.code == 401 && !token.isNullOrEmpty()) {
                    synchronized(this) {
                        val currentToken = preferences.getAccessTokenSync()
                        if (currentToken != token) {
                            response.close()
                            val retryRequest = newRequest.newBuilder()
                                .addHeader("Authorization", "Bearer $currentToken")
                                .build()
                            return@addInterceptor chain.proceed(retryRequest)
                        }

                        val refreshToken = preferences.getRefreshTokenSync()
                        if (!refreshToken.isNullOrEmpty() && !serverUrl.isNullOrEmpty()) {
                            val refreshClient = OkHttpClient.Builder()
                                .connectTimeout(10, TimeUnit.SECONDS)
                                .build()

                            val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
                            val refreshBody = "{\"refreshToken\":\"$refreshToken\"}".toRequestBody(mediaType)
                            val refreshRequest = okhttp3.Request.Builder()
                                .url(serverUrl.replace("/api/v1", "") + "/api/v1/auth/refresh")
                                .post(refreshBody)
                                .build()

                            var refreshSuccess = false
                            try {
                                val refreshResponse = refreshClient.newCall(refreshRequest).execute()
                                if (refreshResponse.isSuccessful) {
                                    val bodyString = refreshResponse.body?.string()
                                    val json = com.google.gson.JsonParser.parseString(bodyString).asJsonObject
                                    val newAccess = json.get("accessToken").asString
                                    val newRefresh = json.get("refreshToken").asString

                                    runBlocking {
                                        preferences.saveTokens(newAccess, newRefresh)
                                    }

                                    response.close()
                                    val retryRequest = newRequest.newBuilder()
                                        .addHeader("Authorization", "Bearer $newAccess")
                                        .build()
                                    response = chain.proceed(retryRequest)
                                    refreshSuccess = true
                                }
                            } catch (e: Exception) {
                                // Ignore
                            }

                            if (!refreshSuccess) {
                                // Fallback: try logging in using stored parent credentials
                                val credentials = runBlocking { preferences.getCredentials() }
                                val email = credentials.first
                                val password = credentials.second
                                if (!email.isNullOrEmpty() && !password.isNullOrEmpty()) {
                                    val loginBody = "{\"email\":\"$email\",\"password\":\"$password\"}".toRequestBody(mediaType)
                                    val loginRequest = okhttp3.Request.Builder()
                                        .url(serverUrl.replace("/api/v1", "") + "/api/v1/auth/login")
                                        .post(loginBody)
                                        .build()
                                    try {
                                        val loginResponse = refreshClient.newCall(loginRequest).execute()
                                        if (loginResponse.isSuccessful) {
                                            val bodyString = loginResponse.body?.string()
                                            val json = com.google.gson.JsonParser.parseString(bodyString).asJsonObject
                                            val newAccess = json.get("accessToken").asString
                                            val newRefresh = json.get("refreshToken").asString

                                            runBlocking {
                                                preferences.saveTokens(newAccess, newRefresh)
                                            }

                                            response.close()
                                            val retryRequest = newRequest.newBuilder()
                                                .addHeader("Authorization", "Bearer $newAccess")
                                                .build()
                                            response = chain.proceed(retryRequest)
                                        }
                                    } catch (e: Exception) {
                                        // Ignore
                                    }
                                }
                            }
                        }
                    }
                }
                if (response.code == 404 && finalRequest.url.encodedPath.contains("/devices/")) {
                    runBlocking {
                        preferences.clear()
                    }
                }
                response
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
