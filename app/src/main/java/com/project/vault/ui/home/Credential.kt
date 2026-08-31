package com.project.vault.ui.home

/**
 * Lightweight UI model for the home screen credential list.
 *
 * This is NOT a Room @Entity — it is mapped from [com.project.vault.entity.CredentialEntity]
 * inside [HomeViewModel.credentials] so the list view stays decoupled from the DB layer.
 * Sensitive fields live only inside the encrypted [CredentialEntity.encJsonContent] blob
 * and are never present here.
 */
data class Credential(
    val id: Int,
    val title: String,
    val status: CredentialStatus
)

enum class CredentialStatus {
    SYNCED,
    SHARED,
    OFFLINE
}
