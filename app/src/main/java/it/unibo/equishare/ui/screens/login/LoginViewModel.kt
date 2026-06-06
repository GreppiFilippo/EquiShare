/** Manages state for the login screen. */
package it.unibo.equishare.ui.screens.login

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.unibo.equishare.R
import it.unibo.equishare.data.remote.GoogleSignInHelper
import it.unibo.equishare.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    @param:StringRes val error: Int? = null,
    val isLoggedIn: Boolean = false,
)

class LoginViewModel(
    private val auth: AuthRepository,
    private val google: GoogleSignInHelper,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            auth.isSignedIn.collect { signedIn ->
                _uiState.update { it.copy(isLoggedIn = signedIn) }
            }
        }
    }

    fun onEmailChange(email: String)       = _uiState.update { it.copy(email = email, error = null) }
    fun onPasswordChange(password: String) = _uiState.update { it.copy(password = password, error = null) }

    fun onSignInClick() {
        val s = _uiState.value
        if (s.email.isBlank() || s.password.isBlank()) {
            _uiState.update { it.copy(error = R.string.error_email_password_required) }
            return
        }
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            auth.signIn(s.email, s.password)
                .onSuccess { _uiState.update { it.copy(isLoading = false, isLoggedIn = true) } }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, error = e.toAuthErrorRes()) } }
        }
    }

    fun onGoogleSignInClick(context: Context) {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            runCatching { google.getIdToken(context) }
                .mapCatching { token -> auth.signInWithGoogleIdToken(token).getOrThrow() }
                .onSuccess { _uiState.update { it.copy(isLoading = false, isLoggedIn = true) } }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, error = e.toAuthErrorRes()) } }
        }
    }

    @StringRes
    private fun Throwable.toAuthErrorRes(): Int {
        val raw = message.orEmpty()
        return when {
            raw.contains("Email not confirmed", ignoreCase = true) ->
                R.string.error_email_not_confirmed
            raw.contains("Invalid login credentials", ignoreCase = true) ->
                R.string.error_invalid_credentials
            raw.contains("User already registered", ignoreCase = true) ->
                R.string.error_email_already_registered
            else -> R.string.error_sign_in_failed
        }
    }
}
