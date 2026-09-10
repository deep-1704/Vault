package com.project.vault.ui.home

/**
 * Represents the type of credential a user can add.
 * Shared between [AddCredentialBottomSheet] and the form views in [home/add].
 */
enum class CredentialType {
    CARD,
    LOGIN,
    OTHER;

    companion object {
        fun fromString(type: String?): CredentialType = when (type?.trim()?.uppercase()) {
            "CARD"  -> CARD
            "LOGIN" -> LOGIN
            else    -> OTHER
        }
    }
}
