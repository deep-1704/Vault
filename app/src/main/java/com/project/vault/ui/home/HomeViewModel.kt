package com.project.vault.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.project.vault.ui.base.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the home screen.
 *
 * Holds a mock list of [Credential] items. When the DB layer is added,
 * replace [_credentials] with a repository call.
 *
 * Loading states for Sync/Share buttons are tracked per item id in
 * [syncingIds] and [sharingIds] and exposed through [buttonLoadingEvent]
 * so the adapter can update individual button states without rebinding
 * the entire list.
 */
@HiltViewModel
class HomeViewModel @Inject constructor() : BaseViewModel() {

    // ── Credential list ──────────────────────────────────────────────────

    private val _credentials = MutableLiveData<List<Credential>>(MOCK_CREDENTIALS)
    val credentials: LiveData<List<Credential>> = _credentials

    // ── Button loading state ─────────────────────────────────────────────

    /**
     * Emits a [ButtonLoadingEvent] when a button's loading state changes.
     * The adapter observes this to update a single item without a full rebind.
     */
    private val _buttonLoadingEvent = MutableLiveData<ButtonLoadingEvent>()
    val buttonLoadingEvent: LiveData<ButtonLoadingEvent> = _buttonLoadingEvent

    // Active loading sets — prevent double-taps
    private val syncingIds = mutableSetOf<Int>()
    private val sharingIds = mutableSetOf<Int>()

    // ── Actions ──────────────────────────────────────────────────────────

    fun onSyncClicked(credentialId: Int) {
        if (syncingIds.contains(credentialId)) return
        syncingIds.add(credentialId)
        _buttonLoadingEvent.value = ButtonLoadingEvent(
            credentialId, ButtonType.SYNC, isLoading = true
        )
        viewModelScope.launch {
            delay(1_000L) // Mock 1-second operation
            syncingIds.remove(credentialId)
            _buttonLoadingEvent.value = ButtonLoadingEvent(
                credentialId, ButtonType.SYNC, isLoading = false
            )
        }
    }

    fun onShareClicked(credentialId: Int) {
        if (sharingIds.contains(credentialId)) return
        sharingIds.add(credentialId)
        _buttonLoadingEvent.value = ButtonLoadingEvent(
            credentialId, ButtonType.SHARE, isLoading = true
        )
        viewModelScope.launch {
            delay(1_000L) // Mock 1-second operation
            sharingIds.remove(credentialId)
            _buttonLoadingEvent.value = ButtonLoadingEvent(
                credentialId, ButtonType.SHARE, isLoading = false
            )
        }
    }

    // ── Models ───────────────────────────────────────────────────────────

    enum class ButtonType { SYNC, SHARE }

    data class ButtonLoadingEvent(
        val credentialId: Int,
        val buttonType: ButtonType,
        val isLoading: Boolean
    )

    // ── Mock data ────────────────────────────────────────────────────────

    companion object {
        private val MOCK_CREDENTIALS = mutableListOf<Credential>()
    }
}
