package com.project.vault.ui.home

/**
 * Represents the type of credential a user can add.
 * Shared between [AddCredentialBottomSheet] and the form views in [home/add].
 */
enum class CredentialType {
    CARD,
    LOGIN,
    PAN,
    AADHAAR,
    OTHER;

    companion object {
        fun fromString(type: String?): CredentialType = when (type?.trim()?.uppercase()) {
            "CARD"    -> CARD
            "LOGIN"   -> LOGIN
            "PAN"     -> PAN
            "AADHAAR" -> AADHAAR
            else      -> OTHER
        }
    }
}
