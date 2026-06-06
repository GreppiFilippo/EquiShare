/** Manages state for the signup screen. */
package it.unibo.equishare.ui.screens.signup

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.unibo.equishare.R
import it.unibo.equishare.data.remote.GoogleSignInHelper
import it.unibo.equishare.domain.model.SignUpOutcome
import it.unibo.equishare.domain.repository.AuthRepository
import it.unibo.equishare.domain.repository.EmailAlreadyRegisteredException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SignUpViewModel(
    private val auth: AuthRepository,
    private val google: GoogleSignInHelper,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SignUpUiState())
    val uiState: StateFlow<SignUpUiState> = _uiState.asStateFlow()

    fun onEvent(event: SignUpEvent) {
        when (event) {
            is SignUpEvent.FirstNameChanged       -> _uiState.update { it.copy(firstName = event.value, firstNameError = null, error = null) }
            is SignUpEvent.LastNameChanged        -> _uiState.update { it.copy(lastName = event.value, lastNameError = null, error = null) }
            is SignUpEvent.EmailChanged           -> _uiState.update { it.copy(email = event.value, emailError = null, error = null) }
            is SignUpEvent.PasswordChanged        -> _uiState.update { it.copy(password = event.value, passwordError = null, error = null) }
            is SignUpEvent.ConfirmPasswordChanged -> _uiState.update { it.copy(confirmPassword = event.value, confirmPasswordError = null, error = null) }
            SignUpEvent.SignUpClicked             -> submit()
            is SignUpEvent.GoogleSignUpClicked    -> signInWithGoogle(event.context)
            SignUpEvent.BackClicked               -> { /* handled in Navigation.kt */ }
        }
    }

    private fun submit() {
        val s = _uiState.value
        if (!validate(s)) return
        val fullName = listOf(s.firstName, s.lastName).filter { it.isNotBlank() }.joinToString(" ")
        val email = s.email.trim()
        _uiState.update { it.copy(isLoading = true, error = null, successMessage = null) }
        viewModelScope.launch {
            auth.isEmailRegistered(email)
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = e.toSignUpErrorRes(),
                            isSubmitted = false,
                        )
                    }
                }
                .onSuccess { alreadyRegistered ->
                    if (alreadyRegistered) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                emailError = R.string.error_email_already_registered,
                                error = R.string.error_email_already_registered,
                                isSubmitted = false,
                            )
                        }
                        return@launch
                    }
                }
            if (!_uiState.value.isLoading) return@launch

            auth.signUp(fullName, email, s.password)
                .onSuccess { outcome ->
                    _uiState.update {
                        when (outcome) {
                            SignUpOutcome.SignedIn ->
                                it.copy(
                                    isLoading = false,
                                    successMessage = R.string.success_account_created,
                                    isSubmitted = true,
                                )
                            SignUpOutcome.EmailConfirmationSent ->
                                it.copy(
                                    isLoading = false,
                                    successMessage = R.string.success_check_email,
                                    isSubmitted = true,
                                )
                        }
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = e.toSignUpErrorRes(),
                            isSubmitted = false,
                        )
                    }
                }
        }
    }

    private fun signInWithGoogle(context: Context) {
        _uiState.update { it.copy(isLoading = true, error = null, successMessage = null) }
        viewModelScope.launch {
            runCatching { google.getCredential(context) }
                .mapCatching { credential ->
                    if (auth.isEmailRegistered(credential.email).getOrThrow()) {
                        throw EmailAlreadyRegisteredException()
                    }
                    auth.signInWithGoogleIdToken(credential.idToken).getOrThrow()
                }
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isSubmitted = true,
                            successMessage = R.string.success_account_created,
                        )
                    }
                }
                .onFailure {
                    e -> _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = e.toSignUpErrorRes(),
                            isSubmitted = false,
                        )
                    }
                }
        }
    }

    private fun validate(s: SignUpUiState): Boolean {
        var ok = true
        _uiState.update { current ->
            current.copy(
                firstNameError       = if (s.firstName.isBlank())           { ok = false; R.string.validation_required } else null,
                lastNameError        = if (s.lastName.isBlank())            { ok = false; R.string.validation_required } else null,
                emailError           = if (!s.email.contains("@"))          { ok = false; R.string.validation_email_invalid } else null,
                passwordError        = if (s.password.length < 8)           { ok = false; R.string.validation_password_min } else null,
                confirmPasswordError = if (s.confirmPassword != s.password) { ok = false; R.string.validation_password_mismatch } else null,
            )
        }
        return ok
    }

    @StringRes
    private fun Throwable.toSignUpErrorRes(): Int {
        val raw = message.orEmpty()
        return when {
            this is EmailAlreadyRegisteredException ->
                R.string.error_email_already_registered
            raw.contains("User already registered", ignoreCase = true) ->
                R.string.error_email_already_registered
            raw.contains("Email not confirmed", ignoreCase = true) ->
                R.string.error_email_not_confirmed
            else -> R.string.error_signup_failed
        }
    }
}
