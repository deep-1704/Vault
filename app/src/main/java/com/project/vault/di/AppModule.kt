package com.project.vault.di

import android.content.Context
import androidx.room.Room
import com.project.vault.AppDatabase
import com.project.vault.api.ApiClient
import com.project.vault.api.ApiService
import com.project.vault.api.BasicAuthInterceptor
import com.project.vault.entity.dao.CredentialDao
import com.project.vault.security.CryptoManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Single Hilt module that provides all app-wide singleton dependencies:
 * Room database, Retrofit API service, CryptoManager, CredentialDao,
 * and CredentialRepository (the last two are auto-bound by Hilt via
 * their @Inject constructors, so no explicit @Provides is needed for them).
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        )
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5
            )
            .build()
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(basicAuthInterceptor: BasicAuthInterceptor): okhttp3.OkHttpClient {
        return ApiClient.createOkHttpClient(basicAuthInterceptor)
    }

    @Provides
    @Singleton
    fun provideApiService(okHttpClient: okhttp3.OkHttpClient): ApiService {
        return ApiClient.createRetrofit(okHttpClient).create(ApiService::class.java)
    }

    /**
     * CryptoManager has no dependencies of its own; we call [ensureKeyPair] here
     * so the RSA key pair is ready before any save/load operation can run.
     */
    @Provides
    @Singleton
    fun provideCryptoManager(): CryptoManager {
        return CryptoManager().also { it.ensureKeyPair() }
    }

    @Provides
    @Singleton
    fun provideCredentialDao(db: AppDatabase): CredentialDao = db.credentialDao()
}
