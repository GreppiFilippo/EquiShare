/** Requests notification permission when needed. */
package it.unibo.equishare.ui.components.permissions

import android.Manifest
import android.os.Build
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import it.unibo.equishare.R
import it.unibo.equishare.utils.PermissionStatus
import it.unibo.equishare.utils.rememberSinglePermission

@Composable
fun NotificationPermissionEffect() {
    // POST_NOTIFICATIONS was introduced with Android 13 (API 33). Anything
    // older has notifications enabled by default — nothing to ask.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val permissionHandler = rememberSinglePermission(
        permission = Manifest.permission.POST_NOTIFICATIONS,
    )

    var showRationale by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!NotificationPromptTracker.hasAskedThisSession &&
            permissionHandler.status == PermissionStatus.Unknown
        ) {
            NotificationPromptTracker.hasAskedThisSession = true
            showRationale = true
        }
    }

    if (showRationale) {
        AlertDialog(
            onDismissRequest = { showRationale = false },
            title = {
                Text(text = stringResource(R.string.notification_permission_title))
            },
            text = {
                Text(
                    text = stringResource(R.string.notification_permission_message),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showRationale = false
                    // Triggers the Android system prompt. That dialog itself
                    // is shown in the device's system locale — we can't
                    // override it from the app side.
                    permissionHandler.launchPermissionRequest()
                }) {
                    Text(text = stringResource(R.string.notification_permission_allow))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRationale = false }) {
                    Text(text = stringResource(R.string.notification_permission_skip))
                }
            },
        )
    }
}

private object NotificationPromptTracker {
    @Volatile var hasAskedThisSession: Boolean = false
}
