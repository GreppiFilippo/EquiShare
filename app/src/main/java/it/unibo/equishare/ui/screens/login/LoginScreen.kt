/** Renders the login screen UI. */
package it.unibo.equishare.ui.screens.login

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import it.unibo.equishare.ui.components.animations.EquiMotion
import it.unibo.equishare.ui.components.animations.pressScale
import it.unibo.equishare.ui.components.buttons.GoogleAuthButton
import it.unibo.equishare.ui.components.buttons.GoogleAuthMode
import it.unibo.equishare.ui.components.textfield.EquiShareTextField
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.unibo.equishare.R
import it.unibo.equishare.ui.components.logo.EquiShareLogo
import it.unibo.equishare.ui.theme.EquiShareTheme

@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onSignUpClick: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LoginContent(
        uiState = uiState,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onSignInClick = viewModel::onSignInClick,
        onGoogleSignInClick = { viewModel.onGoogleSignInClick(context) },
        onSignUpClick = onSignUpClick
    )
}

@Composable
fun LoginContent(
    uiState: LoginUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSignInClick: () -> Unit,
    onGoogleSignInClick: () -> Unit,
    onSignUpClick: () -> Unit,
) {
    var isPasswordVisible by rememberSaveable { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 55.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {

            EquiShareLogo(modifier = Modifier.size(120.dp))

            Spacer(Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.login_hero_sub),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.secondary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(24.dp))

            EquiShareTextField(
                value = uiState.email,
                onValueChange = onEmailChange,
                label = stringResource(R.string.email),
                placeholder = stringResource(R.string.email_placeholder),
                leadingIcon = Icons.Outlined.Email,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            )

            Spacer(Modifier.height(8.dp))

            EquiShareTextField(
                value = uiState.password,
                onValueChange = onPasswordChange,
                label = stringResource(R.string.password),
                leadingIcon = Icons.Outlined.Lock,
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

            // Hold the most recent error message so the exit animation can
            // still render meaningful text while the banner shrinks away.
            var lastError by remember { mutableStateOf<Int?>(null) }
            LaunchedEffect(uiState.error) {
                uiState.error?.let { lastError = it }
            }
            AnimatedVisibility(
                visible = uiState.error != null,
                enter = fadeIn(tween(220, easing = EquiMotion.EmphasizedStandard))
                    + expandVertically(tween(260, easing = EquiMotion.EmphasizedDecelerate)),
                exit = fadeOut(tween(160, easing = EquiMotion.EmphasizedAccelerate))
                    + shrinkVertically(tween(200, easing = EquiMotion.EmphasizedAccelerate)),
            ) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    lastError?.let { errorRes ->
                        Text(
                            text = stringResource(errorRes),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            val signInInteraction = remember { MutableInteractionSource() }
            Button(
                onClick = onSignInClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .pressScale(signInInteraction, pressedScale = 0.97f),
                interactionSource = signInInteraction,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.sign_in),
                    style = MaterialTheme.typography.labelLarge,
                    fontSize = 16.sp
                )
            }

            Spacer(Modifier.height(24.dp))

            OrDivider()

            Spacer(Modifier.height(24.dp))

            // Official Google sign-in artwork (drawable-night/ picks the dark
            // variant automatically). We let the asset render at its intrinsic
            // 175x40 dp size — no fillMaxWidth, which would stretch it.
            GoogleAuthButton(
                onClick = onGoogleSignInClick,
                mode = GoogleAuthMode.SIGN_IN,
            )

            Spacer(Modifier.height(16.dp))

            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.dont_have_account),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(
                    onClick = onSignUpClick,
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.sign_up),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun OrDivider() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant,
            thickness = 1.dp
        )
        Text(
            text = stringResource(R.string.or_continue_with),
            modifier = Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant,
            thickness = 1.dp
        )
    }
}

@Preview(showBackground = true)
@Composable
fun LoginScreenPreview() {
    EquiShareTheme {
        LoginContent(
            uiState = LoginUiState(email = "test@example.com"),
            onEmailChange = {},
            onPasswordChange = {},
            onSignInClick = {},
            onGoogleSignInClick = {},
            onSignUpClick = {}
        )
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
fun LoginScreenDarkPreview() {
    EquiShareTheme(darkTheme = true) {
        LoginContent(
            uiState = LoginUiState(email = "test@example.com"),
            onEmailChange = {},
            onPasswordChange = {},
            onSignInClick = {},
            onGoogleSignInClick = {},
            onSignUpClick = {}
        )
    }
}
