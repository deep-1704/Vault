package com.project.vault.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import com.project.vault.entity.CredentialEntity
import com.project.vault.repository.CredentialRepository
import com.project.vault.repository.SyncRepository
import com.project.vault.ui.base.BaseViewModel
import com.project.vault.ui.home.add.CredentialFormData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the home screen.
 *
 * - [credentials] is a reactive [LiveData] derived from a Room [Flow]. It updates
 *   automatically whenever the `credentials` table changes or a search query is typed.
 * - [saveState] tracks the lifecycle of an in-flight save operation so the
 *   [AddCredentialBottomSheet] can show a loading state and auto-dismiss on success.
 * - [buttonLoadingEvent] drives per-item Sync/Share button spinners without a full
 *   list rebind (behaviour retained from the original mock implementation).
 * - [syncState] carries one-shot error events from the sync pipeline so
 *   [HomeFragment] can show a Snackbar; successful syncs are reflected automatically
 *   through Room's reactive Flow updating the credential chip.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: CredentialRepository,
    private val syncRepository: SyncRepository,
    private val shareRepository: com.project.vault.repository.ShareRepository,
    private val authRepository: com.project.vault.repository.AuthRepository
) : BaseViewModel() {

    val isLoggedIn: LiveData<Boolean> = authRepository.isLoggedIn.asLiveData()
    val currentUsername: LiveData<String?> = authRepository.currentUsername.asLiveData()

    fun logout() {
        authRepository.logout()
    }

    // ── Search query ──────────────────────────────────────────────────────────

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    val hasActiveSearch: LiveData<Boolean> = _searchQuery
        .map { it.isNotBlank() }
        .asLiveData()

    // ── Credential list (reactive, Room-backed) ───────────────────────────────

    private val allCredentialsFlow = repository.getAllFlow()
        .map { entities -> entities.map { it.toUiModel() } }

    /**
     * Total count of all credentials in the database (unaffected by active search query).
     */
    val totalCredentialsCount: LiveData<Int> = allCredentialsFlow
        .map { it.size }
        .asLiveData()

    /**
     * Filtered credential list according to the debounced search query (case-insensitive title match).
     * Debounces 100ms while user types, and immediately when cleared or initialised.
     */
    @OptIn(FlowPreview::class)
    val credentials: LiveData<List<Credential>> =
        combine(
            allCredentialsFlow,
            _searchQuery.debounce { query -> if (query.isEmpty()) 0L else 100L }
        ) { list, query ->
            if (query.isBlank()) {
                list
            } else {
                val trimmed = query.trim()
                list.filter { it.title.contains(trimmed, ignoreCase = true) }
            }
        }.asLiveData()

    // ── Credential detail state ───────────────────────────────────────────────

    private val _selectedCredentialDetail = MutableLiveData<DetailState>(DetailState.Idle)
    val selectedCredentialDetail: LiveData<DetailState> = _selectedCredentialDetail

    fun loadCredentialDetail(credentialId: Int) {
        _selectedCredentialDetail.value = DetailState.Loading
        viewModelScope.launch {
            runCatching {
                val entity = repository.getEntityById(credentialId)
                val detail = repository.getCredentialDetail(credentialId)
                if (entity != null && detail != null) {
                    DetailState.Success(
                        credentialId,
                        detail,
                        isSynced = entity.isSynced,
                        isReceived = entity.isReceived,
                        isShared = entity.isShared && entity.serverShareId != null
                    )
                } else {
                    null
                }
            }
                .onSuccess { state ->
                    if (state != null) {
                        _selectedCredentialDetail.postValue(state)
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

    // ── Sync state (error reporting) ──────────────────────────────────────────

    private val _syncState = MutableLiveData<SyncState>(SyncState.Idle)

    /**
     * Observed by [HomeFragment] to show a Snackbar on sync error.
     * Successful syncs need no explicit signal — Room's reactive Flow automatically
     * updates the credential chip from OFFLINE → SYNCED.
     */
    val syncState: LiveData<SyncState> = _syncState

    /** Resets [syncState] to [SyncState.Idle] after the error has been consumed. */
    fun resetSyncState() {
        _syncState.value = SyncState.Idle
    }

    // ── Received Credential Refresh state ─────────────────────────────────────

    private val _refreshReceivedState = MutableLiveData<RefreshReceivedState>(RefreshReceivedState.Idle)

    /** Observed by HomeFragment to show Snackbars for individual received credential refresh. */
    val refreshReceivedState: LiveData<RefreshReceivedState> = _refreshReceivedState

    fun resetRefreshReceivedState() {
        _refreshReceivedState.value = RefreshReceivedState.Idle
    }

    // ── Global refresh state ──────────────────────────────────────────────────

    private val _isRefreshing = MutableLiveData(false)

    /** True while a global refresh is in progress — drives the spinner on [btnRefresh]. */
    val isRefreshing: LiveData<Boolean> = _isRefreshing

    private val _refreshResultState = MutableLiveData<RefreshResultState>(RefreshResultState.Idle)

    /**
     * One-shot result of the global refresh — observed by [HomeFragment] to show a Snackbar.
     * Reset to [RefreshResultState.Idle] after the event has been consumed.
     */
    val refreshResultState: LiveData<RefreshResultState> = _refreshResultState

    /** Resets [refreshResultState] to [RefreshResultState.Idle] after consumption. */
    fun resetRefreshResultState() {
        _refreshResultState.value = RefreshResultState.Idle
    }

    // ── Share state ───────────────────────────────────────────────────────────

    private val _shareExecutionState = MutableLiveData<ShareExecutionState>(ShareExecutionState.Idle)

    /**
     * Observed by [HomeFragment] to drive the loading dialog and Snackbars for credential sharing.
     */
    val shareExecutionState: LiveData<ShareExecutionState> = _shareExecutionState

    fun resetShareExecutionState() {
        _shareExecutionState.value = ShareExecutionState.Idle
    }

    /**
     * Fetches registered devices for a target user.
     */
    suspend fun fetchDevicesForUser(username: String): Result<List<com.project.vault.api.dto.DeviceDto>> {
        return runCatching {
            shareRepository.getDevicesByUsername(username)
        }
    }

    /**
     * Executes the share operation for [credentialId] to [recipientUsername] with the given [devices].
     */
    fun executeShare(
        credentialId: Int,
        credentialTitle: String,
        recipientUsername: String,
        devices: List<com.project.vault.api.dto.DeviceDto>
    ) {
        if (_shareExecutionState.value is ShareExecutionState.Loading) return
        _shareExecutionState.value = ShareExecutionState.Loading
        viewModelScope.launch {
            runCatching {
                shareRepository.shareCredential(credentialId, recipientUsername, devices)
            }.onSuccess {
                _shareExecutionState.postValue(
                    ShareExecutionState.Success(credentialTitle, recipientUsername)
                )
            }.onFailure { e ->
                _shareExecutionState.postValue(
                    ShareExecutionState.Error(e.message ?: "Failed to share credential")
                )
            }
        }
    }

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
     * If the credential was already synced and the user is logged in, it triggers a server sync.
     * If the sync fails or the user is not logged in, local changes remain saved, and a sync error is reported.
     */
    fun updateCredential(id: Int, data: CredentialFormData) {
        if (_saveState.value is SaveState.Saving) return
        _saveState.value = SaveState.Saving
        viewModelScope.launch {
            runCatching {
                val existing = repository.getEntityById(id)
                val wasSynced = existing?.isSynced == true
                repository.updateCredential(id, data)

                if (wasSynced) {
                    if (authRepository.isLoggedIn.value) {
                        runCatching {
                            syncRepository.syncCredential(id)
                        }.onFailure { syncEx ->
                            _syncState.postValue(SyncState.Error(syncEx.message ?: "Sync failed on edit"))
                        }
                    } else {
                        _syncState.postValue(SyncState.Error("You are logged out. Edits saved locally but not synced."))
                    }
                }
            }
                .onSuccess  { _saveState.postValue(SaveState.Success) }
                .onFailure  { e -> _saveState.postValue(SaveState.Error(e.message ?: "Update failed")) }
        }
    }

    /**
     * Encrypts and saves or updates [data] in Room, and immediately syncs it to all registered devices.
     * If [editId] is provided and not -1, it updates the existing entry before syncing; otherwise it inserts a new entry.
     */
    fun saveAndSyncCredential(data: CredentialFormData, editId: Int? = null) {
        if (_saveState.value is SaveState.Saving) return

        if (!authRepository.isLoggedIn.value) {
            _saveState.value = SaveState.Error("You must be logged in to sync")
            return
        }

        _saveState.value = SaveState.Saving
        viewModelScope.launch {
            runCatching {
                val credentialId = if (editId != null && editId != -1) {
                    repository.updateCredential(editId, data)
                    editId
                } else {
                    repository.saveCredential(data)
                }
                syncRepository.syncCredential(credentialId)
            }.onSuccess {
                _saveState.postValue(SaveState.Success)
            }.onFailure { e ->
                _saveState.postValue(SaveState.Error(e.message ?: "Save & Sync failed"))
            }
        }
    }

    /**
     * Saves updates to [data] in Room, syncs to owner's devices if the credential is synced,
     * and publishes the update to all devices that have received access to this shared credential.
     */
    fun saveAndShareCredential(id: Int, data: CredentialFormData) {
        if (_saveState.value is SaveState.Saving) return

        if (!authRepository.isLoggedIn.value) {
            _saveState.value = SaveState.Error("You must be logged in to share updates")
            return
        }

        _saveState.value = SaveState.Saving
        viewModelScope.launch {
            runCatching {
                val existing = repository.getEntityById(id)
                    ?: throw IllegalArgumentException("Credential #$id not found")
                val sharedCredId = existing.serverShareId?.toLongOrNull()
                    ?: throw IllegalStateException("Credential is not shared")
                val wasSynced = existing.isSynced

                // 1. Update locally
                repository.updateCredential(id, data)

                // 2. Sync to owner's devices if already synced
                if (wasSynced) {
                    runCatching {
                        syncRepository.syncCredential(id)
                    }.onFailure { syncEx ->
                        android.util.Log.e("HomeViewModel", "Failed to sync to owner devices during Save & Share", syncEx)
                    }
                }

                // 3. Publish updates to shared devices
                shareRepository.publishSharedUpdate(id, sharedCredId)
            }.onSuccess {
                _saveState.postValue(SaveState.Success)
            }.onFailure { e ->
                _saveState.postValue(SaveState.Error("Saved locally, but failed to publish updates: ${e.message}"))
            }
        }
    }

    // ── Delete state ──────────────────────────────────────────────────────────

    private val _deleteState = MutableLiveData<DeleteState>(DeleteState.Idle)
    val deleteState: LiveData<DeleteState> = _deleteState

    fun resetDeleteState() {
        _deleteState.value = DeleteState.Idle
    }

    /**
     * Deletes a credential from Room by [id], and if it was synced,
     * also deletes it from the sync server across all user devices.
     * If [deleteForSharedUsers] is true, also deletes the credential for all shared users on the server.
     * Updates [deleteState] to [DeleteState.Deleting], then [DeleteState.Success] or [DeleteState.Error].
     */
    fun deleteCredential(id: Int, deleteForSharedUsers: Boolean = false) {
        if (_deleteState.value is DeleteState.Deleting) return
        _deleteState.value = DeleteState.Deleting

        viewModelScope.launch {
            runCatching {
                val entity = repository.getEntityById(id)
                val serverCredId = entity?.serverId?.toLongOrNull()
                val serverShareId = entity?.serverShareId?.toLongOrNull()
                val isSynced = entity?.isSynced == true
                val isReceived = entity?.isReceived == true

                if (deleteForSharedUsers) {
                    if (!authRepository.isLoggedIn.value) {
                        throw IllegalStateException("You must be logged in to delete for shared users")
                    }
                    if (serverShareId != null) {
                        syncRepository.deleteSharedCredentialGlobally(serverShareId)
                    }
                }

                if (isReceived && serverShareId != null) {
                    if (authRepository.isLoggedIn.value) {
                        val deviceId = authRepository.getDeviceId()
                        syncRepository.revokeSharedCredential(serverShareId, deviceId)
                    }
                } else if (isSynced && serverCredId != null) {
                    if (authRepository.isLoggedIn.value) {
                        syncRepository.deleteSyncedCredential(serverCredId)
                    }
                }

                repository.deleteCredential(id)
            }.onSuccess {
                _deleteState.postValue(DeleteState.Success)
            }.onFailure { e ->
                _deleteState.postValue(DeleteState.Error(e.message ?: "Failed to delete credential"))
            }
        }
    }

    fun onSyncClicked(credentialId: Int) {
        if (syncingIds.contains(credentialId)) return

        // Auth guard — biometric is checked in the Fragment before this is called,
        // but we double-check session validity here as a safety net.
        if (!authRepository.isLoggedIn.value) {
            _syncState.postValue(SyncState.Error("You must be logged in to sync"))
            return
        }

        syncingIds.add(credentialId)
        _buttonLoadingEvent.value = ButtonLoadingEvent(credentialId, ButtonType.SYNC, true)

        launchSafe {
            val entity = repository.getEntityById(credentialId)
            if (entity?.isReceived == true) {
                runCatching { syncRepository.refreshReceivedCredential(credentialId) }
                    .onSuccess { result ->
                        when (result) {
                            is SyncRepository.RefreshReceivedResult.Updated ->
                                _refreshReceivedState.postValue(RefreshReceivedState.Updated(result.title))
                            is SyncRepository.RefreshReceivedResult.Revoked ->
                                _refreshReceivedState.postValue(RefreshReceivedState.Revoked(result.title))
                        }
                    }
                    .onFailure { e ->
                        _refreshReceivedState.postValue(
                            RefreshReceivedState.Error(e.message ?: "Failed to refresh credential")
                        )
                    }
            } else {
                runCatching { syncRepository.syncCredential(credentialId) }
                    .onSuccess {
                        // Room's reactive Flow automatically updates the credential chip —
                        // no additional UI action needed here.
                    }
                    .onFailure { e ->
                        _syncState.postValue(SyncState.Error(e.message ?: "Sync failed"))
                    }
            }

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

    /**
     * Triggers the global refresh pipeline after biometric auth succeeds in [HomeFragment].
     * Guards against re-entrancy while a refresh is already running.
     *
     * The pipeline:
     *  1. Calls [SyncRepository.globalRefresh] with the stored device ID.
     *  2. Posts [isRefreshing] = true/false before/after the operation.
     *  3. Posts [RefreshResultState.Success] or [RefreshResultState.Error] when done.
     */
    fun onGlobalRefreshClicked() {
        if (_isRefreshing.value == true) return   // guard against double-tap

        if (!authRepository.isLoggedIn.value) {
            _refreshResultState.postValue(RefreshResultState.Error("You must be logged in to refresh"))
            return
        }

        val deviceId = authRepository.getDeviceId()

        _isRefreshing.value = true
        launchSafe {
            runCatching { syncRepository.globalRefresh(deviceId) }
                .onSuccess { result ->
                    _refreshResultState.postValue(RefreshResultState.Success(result))
                }
                .onFailure { e ->
                    _refreshResultState.postValue(
                        RefreshResultState.Error(e.message ?: "Refresh failed")
                    )
                }
            _isRefreshing.postValue(false)
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

    sealed class DeleteState {
        object Idle     : DeleteState()
        object Deleting : DeleteState()
        object Success  : DeleteState()
        data class Error(val message: String) : DeleteState()
    }

    /**
     * One-shot state for the sync pipeline.
     * [Idle] is the resting state; [Error] is emitted when the pipeline fails.
     * Success is signalled implicitly through Room's reactive Flow updating [isSynced].
     */
    sealed class SyncState {
        object Idle                              : SyncState()
        data class Error(val message: String)    : SyncState()
    }

    sealed class RefreshReceivedState {
        object Idle : RefreshReceivedState()
        data class Updated(val title: String) : RefreshReceivedState()
        data class Revoked(val title: String) : RefreshReceivedState()
        data class Error(val message: String) : RefreshReceivedState()
    }

    sealed class DetailState {
        object Idle    : DetailState()
        object Loading : DetailState()
        data class Success(
            val id: Int,
            val data: CredentialFormData,
            val isSynced: Boolean = false,
            val isReceived: Boolean = false,
            val isShared: Boolean = false
        ) : DetailState()
        data class Error(val message: String) : DetailState()
    }

    /**
     * One-shot result state for the global refresh button.
     * [Success] carries the [SyncRepository.RefreshResult] summary for the Snackbar.
     * [Error] is posted when the pipeline fails at the network level.
     */
    sealed class RefreshResultState {
        object Idle : RefreshResultState()
        data class Success(val result: com.project.vault.repository.SyncRepository.RefreshResult) : RefreshResultState()
        data class Error(val message: String) : RefreshResultState()
    }

    sealed class ShareExecutionState {
        object Idle : ShareExecutionState()
        object Loading : ShareExecutionState()
        data class Success(val credentialTitle: String, val recipientUsername: String) : ShareExecutionState()
        data class Error(val message: String) : ShareExecutionState()
    }

    // ── Mapping helpers ───────────────────────────────────────────────────────

    private fun CredentialEntity.toUiModel() = Credential(
        id           = id,
        title        = title,
        isSynced     = isSynced,
        isShared     = isShared,
        lastSyncedAt = lastSyncedAt,
        isReceived   = isReceived
    )
}

