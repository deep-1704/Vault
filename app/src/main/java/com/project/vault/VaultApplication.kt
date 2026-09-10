package com.project.vault

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import dagger.hilt.android.HiltAndroidApp

/**
 * Entry point for Hilt dependency injection.
 * All Hilt components are generated from this class.
 *
 * Dark mode is forced unconditionally — Vault is a dark-first application.
 */
@HiltAndroidApp
class VaultApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Vault is always dark — ignore system day/night setting.
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
    }
}
