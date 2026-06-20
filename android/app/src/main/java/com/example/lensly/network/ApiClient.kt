package com.example.lensly.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * ApiClient — singleton Retrofit client pointing at the Lensly Go backend.
 *
 * Base URL is configurable via BuildConfig (set in build.gradle.kts).
 * Default: local dev server at http://10.0.2.2:8080 (Android emulator → host machine)
 *
 * For production, set LENSLY_BACKEND_URL in your CI environment.
 */
object ApiClient {

    // Dynamic user-provided Anthropic API key, loaded from SharedPreferences
    @Volatile
    var userApiKey: String? = null

    // 10.0.2.2 = Android emulator's loopback to host machine
    // Replace with your Railway/Fly.io URL in production
    private const val BASE_URL_DEV = "http://10.0.2.2:8080/"
    private const val BASE_URL_PROD = "https://lensly-backend.fly.dev/"

    // Toggle via build variant — debug uses local, release uses prod
    private val BASE_URL = BASE_URL_DEV  // Will be swapped with BuildConfig in production

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .addInterceptor(loggingInterceptor)
        .addInterceptor { chain ->
            // Add common headers to every request
            val builder = chain.request().newBuilder()
                .addHeader("Content-Type", "application/json")
                .addHeader("X-App-Version", "1.0.0")
            
            // Add user-provided API key if available
            val key = userApiKey
            if (!key.isNullOrBlank()) {
                builder.addHeader("X-Anthropic-API-Key", key)
            }
            
            chain.proceed(builder.build())
        }
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val service: LenslyApiService = retrofit.create(LenslyApiService::class.java)
}
