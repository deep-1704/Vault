package com.project.vault.ui.home

/**
 * UI-only mock model for the home screen credential list.
 *
 * This is NOT a Room @Entity — it lives in the UI layer exclusively.
 * When the database layer is built out, replace this with a proper
 * @Entity in the entity/ package and a corresponding DAO + Repository method.
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
