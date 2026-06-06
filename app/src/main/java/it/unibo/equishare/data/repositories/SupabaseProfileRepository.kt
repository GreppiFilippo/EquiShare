/** Implements the Profile repository using Supabase and local data. */
package it.unibo.equishare.data.repositories

import it.unibo.equishare.data.local.AppLanguageManager
import it.unibo.equishare.data.local.EquiShareLocalDataSource
import it.unibo.equishare.data.local.UserPreferencesDataSource
import it.unibo.equishare.data.remote.datasource.SupabaseProfileDataSource
import it.unibo.equishare.data.remote.dto.ProfileDto
import it.unibo.equishare.domain.model.Currency
import it.unibo.equishare.domain.repository.AuthRepository
import it.unibo.equishare.domain.repository.ProfileRepository
import it.unibo.equishare.ui.screens.profile.ThemeOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SupabaseProfileRepository(
    private val remote: SupabaseProfileDataSource,
    private val auth: AuthRepository,
    private val userPreferences: UserPreferencesDataSource,
    private val appLanguageManager: AppLanguageManager,
    private val local: EquiShareLocalDataSource,
) : RefreshableRepository(), ProfileRepository {

    init { watchAuth(auth.isSignedIn) }

    private val profile: Flow<ProfileDto?> = refreshableCacheFirst { _isForced ->
        val uid = auth.currentUserId ?: run { emit(null); return@refreshableCacheFirst }
        emit(local.profile(uid))
        try {
            val fresh = remote.fetchProfile(uid)?.also { local.upsertProfile(it) }
            emit(fresh)
        } catch (t: Throwable) {
            t.rethrowIfCancellation()
        }
    }

    override val firstName: Flow<String> =
        profile.map { it?.fullName?.substringBefore(' ', "").orEmpty() }
    override val lastName: Flow<String> =
        profile.map { it?.fullName?.substringAfter(' ', "").orEmpty() }
    override val fullName: Flow<String> = profile.map { it?.fullName.orEmpty() }
    override val email:    Flow<String> = profile.map { it?.email.orEmpty() }
    override val avatarUrl: Flow<String?> = profile.map { it?.avatarUrl }
    override val defaultCurrency: Flow<Currency> =
        profile.map { Currency.fromCode(it?.defaultCurrency) }

    override val notificationsEnabled: Flow<Boolean> = userPreferences.notificationsEnabled
    override val theme: Flow<ThemeOption> = userPreferences.theme
    override val languageTag: Flow<String?> = appLanguageManager.languageTag
    override val supportedLanguageTags: List<String> = appLanguageManager.supportedLanguageTags

    override suspend fun setNotificationsEnabled(enabled: Boolean) =
        userPreferences.setNotificationsEnabled(enabled)

    override suspend fun setTheme(theme: ThemeOption) = userPreferences.setTheme(theme)

    override suspend fun setLanguageTag(languageTag: String?) =
        appLanguageManager.setLanguageTag(languageTag)

    override suspend fun syncLanguageWithSystemSettings() =
        appLanguageManager.syncWithSystemAppLanguage()

    override suspend fun updateName(firstName: String, lastName: String) {
        val uid = auth.currentUserId ?: return
        val composed = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ")
        remote.updateName(uid, composed)
        refresh()
    }

    override suspend fun setAvatarUrl(url: String?) {
        val uid = auth.currentUserId ?: return
        remote.updateAvatarUrl(uid, url)
        refresh()
    }

    override suspend fun setDefaultCurrency(currency: Currency) {
        val uid = auth.currentUserId ?: return
        remote.updateDefaultCurrency(uid, currency.code)
        refresh()
    }

    override suspend fun uploadAvatar(bytes: ByteArray, mimeType: String): Result<String> =
        runCatching {
            val uid = auth.currentUserId ?: error("Not signed in")
            val cacheBusted = remote.uploadAvatar(uid, bytes, mimeType)
            setAvatarUrl(cacheBusted)
            cacheBusted
        }
}
