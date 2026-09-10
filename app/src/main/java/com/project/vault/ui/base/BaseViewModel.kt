package com.project.vault.ui.base

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Base ViewModel providing shared error-handling infrastructure.
 *
 * All ViewModels in this app should extend [BaseViewModel]. Use [launchSafe]
 * to run coroutines — it automatically catches exceptions and posts the
 * error message to [errorLiveData], which [BaseFragment] observes to show
 * a Toast without boilerplate in every ViewModel.
 *
 * Example usage in a subclass:
 *
 *   fun loadData() = launchSafe {
 *       val result = repository.getData()
 *       _dataLiveData.value = result
 *   }
 */
abstract class BaseViewModel : ViewModel() {

    private val _errorLiveData = MutableLiveData<String>()

    /** Emits error messages caught inside [launchSafe]. Observed by [BaseFragment]. */
    val errorLiveData: LiveData<String> = _errorLiveData

    /**
     * Launches a coroutine in [viewModelScope] wrapped in a try/catch.
     * Any [Exception] thrown inside [block] will post its message to [errorLiveData].
     *
     * @param block The suspending work to perform.
     */
    protected fun launchSafe(block: suspend CoroutineScope.() -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                _errorLiveData.value = e.message ?: "An unexpected error occurred."
            }
        }
    }
}
