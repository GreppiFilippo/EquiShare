/** Manages top-level app state for navigation chrome. */
package it.unibo.equishare.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.unibo.equishare.domain.repository.ActivityRepository
import it.unibo.equishare.domain.repository.AuthRepository
import it.unibo.equishare.domain.repository.AuthState
import it.unibo.equishare.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class MainAppBarState(
    val avatarUrl: String? = null,
    val displayName: String = "",
)

class MainViewModel(
    private val activityRepository: ActivityRepository,
    profileRepository: ProfileRepository,
    authRepository: AuthRepository,
) : ViewModel() {

    val authState: StateFlow<AuthState> = authRepository.authState
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = AuthState.LOADING,
        )

    val unreadCount: StateFlow<Int> = activityRepository.unreadCount
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0
        )

    val appBarState: StateFlow<MainAppBarState> = combine(
        profileRepository.avatarUrl,
        profileRepository.fullName,
    ) { avatarUrl, fullName ->
        MainAppBarState(
            avatarUrl = avatarUrl,
            displayName = fullName,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MainAppBarState(),
    )
}
