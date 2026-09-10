package com.project.vault.api

import com.project.vault.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Retrofit + OkHttp client factory and configuration.
 *
 * - Logging is BODY level in debug builds, NONE in release.
 * - Timeouts: 30 s connect / read / write.
 */
object ApiClient {

    const val BASE_URL = BuildConfig.BASE_URL
    private const val TIMEOUT_SECONDS = 30L

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }

    fun createOkHttpClient(basicAuthInterceptor: BasicAuthInterceptor? = null): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)

        if (basicAuthInterceptor != null) {
            builder.addInterceptor(basicAuthInterceptor)
        }

        builder.addInterceptor(loggingInterceptor)
        return builder.build()
    }

    fun createRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val retrofit: Retrofit by lazy {
        createRetrofit(createOkHttpClient())
    }
}

