/** Defines the Profile repository contract. */
package it.unibo.equishare.domain.repository

import it.unibo.equishare.domain.model.Currency
import it.unibo.equishare.ui.screens.profile.ThemeOption
import kotlinx.coroutines.flow.Flow

interface ProfileRepository {
    val firstName: Flow<String>
    val lastName: Flow<String>
    val fullName: Flow<String>
    val email: Flow<String>
    val avatarUrl: Flow<String?>
    val defaultCurrency: Flow<Currency>

    val notificationsEnabled: Flow<Boolean>
    val theme: Flow<ThemeOption>
    val languageTag: Flow<String?>
    val supportedLanguageTags: List<String>

    suspend fun setNotificationsEnabled(enabled: Boolean)
    suspend fun setTheme(theme: ThemeOption)
    suspend fun setLanguageTag(languageTag: String?)
    suspend fun syncLanguageWithSystemSettings()

    suspend fun updateName(firstName: String, lastName: String)
    suspend fun setAvatarUrl(url: String?)
    suspend fun setDefaultCurrency(currency: Currency)

    /**
     * Uploads the given image bytes to Supabase Storage and writes the public
     * URL into `profiles.avatar_url`. Returns the public URL on success, or a
     * failure with the underlying exception.
     *
     * Implementations are expected to use a deterministic path under the
     * current user's id (e.g. `<uid>/avatar.jpg`) and `upsert = true` so a
     * second pick simply replaces the previous file.
     */
    suspend fun uploadAvatar(bytes: ByteArray, mimeType: String): Result<String>

    fun refresh()
}
