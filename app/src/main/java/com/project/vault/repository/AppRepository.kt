package com.project.vault.repository

import com.project.vault.AppDatabase
import com.project.vault.api.ApiService
import javax.inject.Inject

/**
 * Central repository for the Vault app.
 *
 * Mediates between the Room [AppDatabase] (local data source) and the
 * [ApiService] (remote data source). All data access for the ViewModels
 * flows through this class.
 *
 * Add feature-specific suspend functions as the app grows:
 *
 * Example:
 *   suspend fun getItems(): List<Item> = db.itemDao().getAll()
 *
 * Errors are propagated as exceptions and caught at the ViewModel layer
 * via [ui.base.BaseViewModel.launchSafe].
 */
class AppRepository @Inject constructor(
    private val db: AppDatabase,
    private val api: ApiService
) {
    // Repository methods will be added here per feature.
}
