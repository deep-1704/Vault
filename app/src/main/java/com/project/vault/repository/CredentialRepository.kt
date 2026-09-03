package com.project.vault.repository

import com.google.gson.Gson
import com.project.vault.entity.CredentialEntity
import com.project.vault.entity.dao.CredentialDao
import com.project.vault.security.CryptoManager
import com.project.vault.ui.home.add.CredentialFormData
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for credential storage and retrieval.
 *
 * Orchestrates the full encrypt-then-store pipeline:
 *  1. Serialise [CredentialFormData] to a flat JSON map with Gson.
 *  2. Encrypt the JSON string with [CryptoManager] (RSA-2048 / OAEP / SHA-256).
 *  3. Persist the resulting [CredentialEntity] via [CredentialDao].
 *
 * For the home list, [title] and [credType] are stored as plaintext columns so
 * the list view never needs to decrypt anything. [encJsonContent] is only decrypted
 * when the user opens a credential detail view.
 *
 * JSON schema stored inside [CredentialEntity.encJsonContent]:
 *
 *   Card:  { "title", "credType": "CARD",  "holderName", "cardNumber",
 *             "expiryMonth", "expiryYear", "cvv" }
 *   Login: { "title", "credType": "LOGIN", "username", "password" }
 */
@Singleton
class CredentialRepository @Inject constructor(
    private val dao: CredentialDao,
    private val crypto: CryptoManager
) {
    private val gson = Gson()

    // ── Read ─────────────────────────────────────────────────────────────────

    /**
     * Returns a hot [Flow] of all credentials ordered newest-first.
     * Room re-emits automatically after any insert or delete — no manual refresh needed.
     */
    fun getAllFlow(): Flow<List<CredentialEntity>> = dao.getAllFlow()

    // ── Write ────────────────────────────────────────────────────────────────

    /**
     * Serialises [formData] to JSON, encrypts it, then inserts a [CredentialEntity].
     *
     * Must be called from a coroutine (suspend function). Any exception from
     * [CryptoManager] or Room propagates to the caller.
     *
     * @return The local auto-generated primary key ID of the inserted entity.
     */
    suspend fun saveCredential(formData: CredentialFormData): Int {
        val (title, credType, jsonMap) = buildJsonMap(formData)
        val json        = gson.toJson(jsonMap)
        val ciphertext  = crypto.encrypt(json)

        val insertedId = dao.insert(
            CredentialEntity(
                title          = title,
                credType       = credType,
                encJsonContent = ciphertext
            )
        )
        return insertedId.toInt()
    }

    // ── Decryption & Details (for detail view) ──────────────────────────────

    /**
     * Decrypts a single entity's [CredentialEntity.encJsonContent] and returns
     * the plaintext JSON string. Parsing that JSON into a typed object is the
     * caller's responsibility.
     */
    fun decryptContent(entity: CredentialEntity): String =
        crypto.decrypt(entity.encJsonContent)

    /**
     * Retrieves a credential by [id], decrypts its encrypted content, and returns
     * the typed [CredentialFormData] (either [CardCredentialData] or [LoginCredentialData]).
     */
    suspend fun getCredentialDetail(id: Int): CredentialFormData? {
        val entity = dao.getById(id) ?: return null
        val decryptedJson = try {
            decryptContent(entity)
        } catch (e: Exception) {
            "{}"
        }
        val type = object : com.google.gson.reflect.TypeToken<Map<String, String>>() {}.type
        val map: Map<String, String> = try {
            gson.fromJson(decryptedJson, type) ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }

        return when (entity.credType.uppercase()) {
            "CARD" -> CredentialFormData.CardCredentialData(
                title       = map["title"] ?: entity.title,
                holderName  = map["holderName"] ?: "",
                cardNumber  = map["cardNumber"] ?: "",
                expiryMonth = map["expiryMonth"] ?: "",
                expiryYear  = map["expiryYear"] ?: "",
                cvv         = map["cvv"] ?: ""
            )
            "LOGIN" -> CredentialFormData.LoginCredentialData(
                title    = map["title"] ?: entity.title,
                username = map["username"] ?: "",
                password = map["password"] ?: ""
            )
            else -> null
        }
    }

    /**
     * Updates an existing credential record with new [formData].
     */
    suspend fun updateCredential(id: Int, formData: CredentialFormData) {
        val existing = dao.getById(id) ?: return
        val (title, credType, jsonMap) = buildJsonMap(formData)
        val json = gson.toJson(jsonMap)
        val ciphertext = crypto.encrypt(json)

        val updated = existing.copy(
            title          = title,
            credType       = credType,
            encJsonContent = ciphertext
        )
        dao.update(updated)
    }

    /**
     * Retrieves the raw [CredentialEntity] by its local [id].
     */
    suspend fun getEntityById(id: Int): CredentialEntity? = dao.getById(id)

    /**
     * Deletes a credential from the database by its [id].
     */
    suspend fun deleteCredential(id: Int) {
        dao.deleteById(id)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private data class JsonBuildResult(
        val title: String,
        val credType: String,
        val map: Map<String, String>
    )

    private fun buildJsonMap(formData: CredentialFormData): JsonBuildResult =
        when (formData) {
            is CredentialFormData.CardCredentialData -> JsonBuildResult(
                title    = formData.title,
                credType = "CARD",
                map      = mapOf(
                    "title"       to formData.title,
                    "credType"    to "CARD",
                    "holderName"  to formData.holderName,
                    "cardNumber"  to formData.cardNumber,
                    "expiryMonth" to formData.expiryMonth,
                    "expiryYear"  to formData.expiryYear,
                    "cvv"         to formData.cvv
                )
            )
            is CredentialFormData.LoginCredentialData -> JsonBuildResult(
                title    = formData.title,
                credType = "LOGIN",
                map      = mapOf(
                    "title"    to formData.title,
                    "credType" to "LOGIN",
                    "username" to formData.username,
                    "password" to formData.password
                )
            )
        }
}

