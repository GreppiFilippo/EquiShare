/** Renders the signup screen UI. */
package it.unibo.equishare.ui.screens.signup

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import it.unibo.equishare.R
import it.unibo.equishare.ui.components.buttons.GoogleAuthButton
import it.unibo.equishare.ui.components.buttons.GoogleAuthMode
import it.unibo.equishare.ui.components.textfield.EquiShareTextField
import it.unibo.equishare.ui.theme.EquiShareTheme

// ─────────────────────────────────────────────────────────────────────────────
// UI State  (owned by the ViewModel, observed by the screen)
// ─────────────────────────────────────────────────────────────────────────────

data class SignUpUiState(
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val isLoading: Boolean = false,
    @param:StringRes val firstNameError: Int? = null,
    @param:StringRes val lastNameError: Int? = null,
    @param:StringRes val emailError: Int? = null,
    @param:StringRes val passwordError: Int? = null,
    @param:StringRes val confirmPasswordError: Int? = null,
    @param:StringRes val error: Int? = null,
    @param:StringRes val successMessage: Int? = null,
    val isSubmitted: Boolean = false,
)

// ─────────────────────────────────────────────────────────────────────────────
// Events  (user interactions bubble up to the ViewModel)
// ─────────────────────────────────────────────────────────────────────────────

sealed interface SignUpEvent {
    data class FirstNameChanged(val value: String) : SignUpEvent
    data class LastNameChanged(val value: String) : SignUpEvent
    data class EmailChanged(val value: String) : SignUpEvent
    data class PasswordChanged(val value: String) : SignUpEvent
    data class ConfirmPasswordChanged(val value: String) : SignUpEvent
    data object SignUpClicked : SignUpEvent
    data class GoogleSignUpClicked(val context: Context) : SignUpEvent
    data object BackClicked : SignUpEvent
}

// ─────────────────────────────────────────────────────────────────────────────
// Screen  (pure / dumb – no ViewModel reference)
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignUpScreen(
    uiState: SignUpUiState,
    onEvent: (SignUpEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var isPasswordVisible by rememberSaveable { mutableStateOf(false) }
    var isConfirmPasswordVisible by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { onEvent(SignUpEvent.BackClicked) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                // Mirror the leading icon width so the title stays centred
                actions = { Spacer(Modifier.size(48.dp)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 55.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {

            // ── Hero text ──────────────────────────────────────────────────
            Text(
                text = stringResource(R.string.join_future_sharing),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.signup_hero_sub),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )

            Spacer(Modifier.height(16.dp))

            // ── Status banner (error or success) ───────────────────────────
            uiState.error?.let { errorRes ->
                Text(
                    text = stringResource(errorRes),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                )
            } ?: uiState.successMessage?.let { successRes ->
                if (uiState.isSubmitted) {
                    Text(
                        text = stringResource(R.string.signup_completed_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                    )
                }
                Text(
                    text = stringResource(successRes),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                )
            }

            if (!uiState.isSubmitted) {
                // ── Form fields ────────────────────────────────────────────
                EquiShareTextField(
                    value = uiState.firstName,
                    onValueChange = { onEvent(SignUpEvent.FirstNameChanged(it)) },
                    label = stringResource(R.string.first_name),
                    errorMessage = uiState.firstNameError,
                )

                Spacer(Modifier.height(11.dp))

                EquiShareTextField(
                    value = uiState.lastName,
                    onValueChange = { onEvent(SignUpEvent.LastNameChanged(it)) },
                    label = stringResource(R.string.last_name),
                    errorMessage = uiState.lastNameError,
                )

                Spacer(Modifier.height(11.dp))

                EquiShareTextField(
                    value = uiState.email,
                    onValueChange = { onEvent(SignUpEvent.EmailChanged(it)) },
                    label = stringResource(R.string.email),
                    errorMessage = uiState.emailError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                )

                Spacer(Modifier.height(11.dp))

                EquiShareTextField(
                    value = uiState.password,
                    onValueChange = { onEvent(SignUpEvent.PasswordChanged(it)) },
                    label = stringResource(R.string.password),
                    errorMessage = uiState.passwordError,
                    visualTransformation = if (isPasswordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                            Icon(
                                imageVector = if (isPasswordVisible) {
                                    Icons.Outlined.VisibilityOff
                                } else {
                                    Icons.Outlined.Visibility
                                },
                                contentDescription = stringResource(
                                    if (isPasswordVisible) {
                                        R.string.hide_password
                                    } else {
                                        R.string.show_password
                                    }
                                ),
                            )
                        }
                    },
                )

                Spacer(Modifier.height(11.dp))

                EquiShareTextField(
                    value = uiState.confirmPassword,
                    onValueChange = { onEvent(SignUpEvent.ConfirmPasswordChanged(it)) },
                    label = stringResource(R.string.confirm_password),
                    errorMessage = uiState.confirmPasswordError,
                    visualTransformation = if (isConfirmPasswordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { isConfirmPasswordVisible = !isConfirmPasswordVisible }) {
                            Icon(
                                imageVector = if (isConfirmPasswordVisible) {
                                    Icons.Outlined.VisibilityOff
                                } else {
                                    Icons.Outlined.Visibility
                                },
                                contentDescription = stringResource(
                                    if (isConfirmPasswordVisible) {
                                        R.string.hide_password
                                    } else {
                                        R.string.show_password
                                    }
                                ),
                            )
                        }
                    },
                )

                Spacer(Modifier.height(11.dp))

                // ── Primary CTA ────────────────────────────────────────────
                Button(
                    onClick = { onEvent(SignUpEvent.SignUpClicked) },
                    enabled = !uiState.isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 61.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.sign_up),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }

                // ── Divider ────────────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Box(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.background)
                            .padding(horizontal = 16.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.or_register_with),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // ── Google button ──────────────────────────────────────────
                GoogleAuthButton(
                    onClick = {
                        if (!uiState.isLoading) onEvent(SignUpEvent.GoogleSignUpClicked(context))
                    },
                    mode = GoogleAuthMode.SIGN_UP,
                )
            } else {
                TextButton(onClick = { onEvent(SignUpEvent.BackClicked) }) {
                    Text(text = stringResource(R.string.back_to_login))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Preview
// ─────────────────────────────────────────────────────────────────────────────

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun SignUpScreenPreview() {
    EquiShareTheme {
        SignUpScreen(
            uiState = SignUpUiState(
                firstName = "First Name",
                lastName = "Last Name",
                email = "Email",
                password = "Password",
                confirmPassword = "Confirm Password",
            ),
            onEvent = {},
        )
    }
}
