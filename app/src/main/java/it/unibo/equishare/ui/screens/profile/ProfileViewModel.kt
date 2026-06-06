/** Manages state for the profile screen. */
package it.unibo.equishare.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.unibo.equishare.domain.repository.AuthRepository
import it.unibo.equishare.domain.repository.ProfileRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ProfileViewModel(
    private val profileRepository: ProfileRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    init {
        viewModelScope.launch { profileRepository.syncLanguageWithSystemSettings() }
    }

    // The typed `combine` overload tops out at five flows, so the name fields
    // are folded into a Triple first and then combined with the remaining
    // sources (avatar, notifications, theme + the name triple).
    private val nameAndEmail = combine(
        profileRepository.firstName,
        profileRepository.lastName,
        profileRepository.email,
    ) { first, last, email -> Triple(first, last, email) }
    private val settings = combine(
        profileRepository.notificationsEnabled,
        profileRepository.theme,
        profileRepository.languageTag,
    ) { notifications, theme, languageTag ->
        ProfileSettingsState(notifications, theme, languageTag)
    }
    private val avatarUpload = MutableStateFlow(AvatarUploadState())
    private val isRefreshing = MutableStateFlow(false)
    private val isLoggingOut = MutableStateFlow(false)
    private val transientState = combine(
        avatarUpload,
        isRefreshing,
        isLoggingOut,
    ) { upload, refreshing, loggingOut ->
        ProfileTransientState(upload, refreshing, loggingOut)
    }

    val uiState = combine(
        nameAndEmail,
        profileRepository.avatarUrl,
        settings,
        transientState,
    ) { (first, last, email), avatar, settings, transient ->
        ProfileUiState(
            firstName = first,
            lastName = last,
            email = email,
            avatarUrl = avatar,
            notificationsEnabled = settings.notificationsEnabled,
            selectedTheme = settings.theme,
            selectedLanguageTag = settings.languageTag,
            supportedLanguageTags = profileRepository.supportedLanguageTags,
            isAvatarUploading = transient.avatarUpload.isUploading,
            avatarError = transient.avatarUpload.errorMessage,
            isRefreshing = transient.isRefreshing,
            isLoggingOut = transient.isLoggingOut,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProfileUiState(isLoading = true),
    )

    fun onEvent(event: ProfileEvent) {
        when (event) {
            is ProfileEvent.NotificationsToggled ->
                viewModelScope.launch { profileRepository.setNotificationsEnabled(event.enabled) }
            is ProfileEvent.ThemeSelected ->
                viewModelScope.launch { profileRepository.setTheme(event.theme) }
            is ProfileEvent.LanguageSelected ->
                viewModelScope.launch { profileRepository.setLanguageTag(event.languageTag) }
            is ProfileEvent.AvatarPicked ->
                uploadAvatar(event.bytes, event.mimeType)
            ProfileEvent.AvatarErrorShown ->
                avatarUpload.update { it.copy(errorMessage = null) }
            ProfileEvent.LogOutClicked ->
                signOut()
            ProfileEvent.ChangeAvatarClicked,
            ProfileEvent.BackClicked -> { /* handled in Navigation.kt */ }
        }
    }

    private fun signOut() {
        if (isLoggingOut.value) return
        isLoggingOut.update { true }
        viewModelScope.launch {
            runCatching { authRepository.signOut() }
                .onFailure { isLoggingOut.update { false } }
        }
    }

    private fun uploadAvatar(bytes: ByteArray, mimeType: String) {
        if (avatarUpload.value.isUploading) return
        avatarUpload.update { AvatarUploadState(isUploading = true) }
        viewModelScope.launch {
            profileRepository.uploadAvatar(bytes, mimeType)
                .onFailure { error ->
                    avatarUpload.update {
                        AvatarUploadState(errorMessage = error.message ?: "Image upload failed")
                    }
                }
                .onSuccess {
                    avatarUpload.update { AvatarUploadState() }
                }
        }
    }

    fun refresh() {
        if (isRefreshing.value) return
        isRefreshing.update { true }
        viewModelScope.launch {
            profileRepository.syncLanguageWithSystemSettings()
            profileRepository.refresh()
            delay(700)
            isRefreshing.update { false }
        }
    }

    private data class AvatarUploadState(
        val isUploading: Boolean = false,
        val errorMessage: String? = null,
    )

    private data class ProfileTransientState(
        val avatarUpload: AvatarUploadState,
        val isRefreshing: Boolean,
        val isLoggingOut: Boolean,
    )

    private data class ProfileSettingsState(
        val notificationsEnabled: Boolean,
        val theme: ThemeOption,
        val languageTag: String?,
    )
}
