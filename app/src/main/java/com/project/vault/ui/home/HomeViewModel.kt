package com.project.vault.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import com.project.vault.entity.CredentialEntity
import com.project.vault.repository.CredentialRepository
import com.project.vault.ui.base.BaseViewModel
import com.project.vault.ui.home.add.CredentialFormData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the home screen.
 *
 * - [credentials] is a reactive [LiveData] derived from a Room [Flow]. It updates
 *   automatically whenever the `credentials` table changes — no manual refresh needed.
 * - [saveState] tracks the lifecycle of an in-flight save operation so the
 *   [AddCredentialBottomSheet] can show a loading state and auto-dismiss on success.
 * - [buttonLoadingEvent] drives per-item Sync/Share button spinners without a full
 *   list rebind (behaviour retained from the original mock implementation).
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: CredentialRepository,
    private val authRepository: com.project.vault.repository.AuthRepository
) : BaseViewModel() {

    val isLoggedIn: LiveData<Boolean> = authRepository.isLoggedIn.asLiveData()
    val currentUsername: LiveData<String?> = authRepository.currentUsername.asLiveData()

    fun logout() {
        authRepository.logout()
    }

    // ── Credential list (reactive, Room-backed) ───────────────────────────────

    /**
     * Maps [CredentialEntity] rows from the DB to the lightweight UI [Credential] model.
     * [title] is a plaintext column — no decryption needed for list display.
     */
    val credentials: LiveData<List<Credential>> =
        repository.getAllFlow()
            .asLiveData()
            .map { entities -> entities.map { it.toUiModel() } }

    // ── Credential detail state ───────────────────────────────────────────────

    private val _selectedCredentialDetail = MutableLiveData<DetailState>(DetailState.Idle)
    val selectedCredentialDetail: LiveData<DetailState> = _selectedCredentialDetail

    fun loadCredentialDetail(credentialId: Int) {
        _selectedCredentialDetail.value = DetailState.Loading
        viewModelScope.launch {
            runCatching { repository.getCredentialDetail(credentialId) }
                .onSuccess { detail ->
                    if (detail != null) {
                        _selectedCredentialDetail.postValue(DetailState.Success(credentialId, detail))
                    } else {
                        _selectedCredentialDetail.postValue(DetailState.Error("Credential not found"))
                    }
                }
                .onFailure { e ->
                    _selectedCredentialDetail.postValue(DetailState.Error(e.message ?: "Failed to load credential"))
                }
        }
    }

    fun resetDetailState() {
        _selectedCredentialDetail.value = DetailState.Idle
    }

    // ── Save state ────────────────────────────────────────────────────────────

    private val _saveState = MutableLiveData<SaveState>(SaveState.Idle)

    /**
     * Observed by [AddCredentialBottomSheet] to drive the Save button's loading
     * state and dismiss the sheet on success.
     */
    val saveState: LiveData<SaveState> = _saveState

    /** Resets [saveState] to [SaveState.Idle]. Call when the sheet is opened/dismissed. */
    fun resetSaveState() {
        _saveState.value = SaveState.Idle
    }

    // ── Button loading state ──────────────────────────────────────────────────

    private val _buttonLoadingEvent = MutableLiveData<ButtonLoadingEvent>()
    val buttonLoadingEvent: LiveData<ButtonLoadingEvent> = _buttonLoadingEvent

    private val syncingIds  = mutableSetOf<Int>()
    private val sharingIds  = mutableSetOf<Int>()

    // ── Actions ───────────────────────────────────────────────────────────────

    /**
     * Encrypts [data] and inserts it into Room via [CredentialRepository].
     * Posts [SaveState.Saving] immediately, then [SaveState.Success] or [SaveState.Error].
     */
    fun addCredential(data: CredentialFormData) {
        if (_saveState.value is SaveState.Saving) return   // guard against double-tap
        _saveState.value = SaveState.Saving
        viewModelScope.launch {
            runCatching { repository.saveCredential(data) }
                .onSuccess  { _saveState.postValue(SaveState.Success) }
                .onFailure  { e -> _saveState.postValue(SaveState.Error(e.message ?: "Save failed")) }
        }
    }

    /**
     * Updates an existing credential record in Room via [CredentialRepository].
     */
    fun updateCredential(id: Int, data: CredentialFormData) {
        if (_saveState.value is SaveState.Saving) return
        _saveState.value = SaveState.Saving
        viewModelScope.launch {
            runCatching { repository.updateCredential(id, data) }
                .onSuccess  { _saveState.postValue(SaveState.Success) }
                .onFailure  { e -> _saveState.postValue(SaveState.Error(e.message ?: "Update failed")) }
        }
    }

    /**
     * Deletes a credential from Room by [id].
     */
    fun deleteCredential(id: Int) {
        launchSafe {
            repository.deleteCredential(id)
        }
    }

    fun onSyncClicked(credentialId: Int) {
        if (syncingIds.contains(credentialId)) return
        syncingIds.add(credentialId)
        _buttonLoadingEvent.value = ButtonLoadingEvent(credentialId, ButtonType.SYNC, true)
        launchSafe {
            kotlinx.coroutines.delay(1_000L) // Mock — replace with real sync call
            syncingIds.remove(credentialId)
            _buttonLoadingEvent.postValue(ButtonLoadingEvent(credentialId, ButtonType.SYNC, false))
        }
    }

    fun onShareClicked(credentialId: Int) {
        if (sharingIds.contains(credentialId)) return
        sharingIds.add(credentialId)
        _buttonLoadingEvent.value = ButtonLoadingEvent(credentialId, ButtonType.SHARE, true)
        launchSafe {
            kotlinx.coroutines.delay(1_000L) // Mock — replace with real share call
            sharingIds.remove(credentialId)
            _buttonLoadingEvent.postValue(ButtonLoadingEvent(credentialId, ButtonType.SHARE, false))
        }
    }

    // ── Models ────────────────────────────────────────────────────────────────

    enum class ButtonType { SYNC, SHARE }

    data class ButtonLoadingEvent(
        val credentialId: Int,
        val buttonType: ButtonType,
        val isLoading: Boolean
    )

    sealed class SaveState {
        object Idle    : SaveState()
        object Saving  : SaveState()
        object Success : SaveState()
        data class Error(val message: String) : SaveState()
    }

    sealed class DetailState {
        object Idle    : DetailState()
        object Loading : DetailState()
        data class Success(val id: Int, val data: CredentialFormData) : DetailState()
        data class Error(val message: String) : DetailState()
    }

    // ── Mapping helpers ───────────────────────────────────────────────────────

    private fun CredentialEntity.toUiModel() = Credential(
        id     = id,
        title  = title,
        status = when {
            isShared -> CredentialStatus.SHARED
            isSynced -> CredentialStatus.SYNCED
            else     -> CredentialStatus.OFFLINE
        }
    )
}

