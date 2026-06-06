/** Provides helpers for runtime permission handling. */
package it.unibo.equishare.utils

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

enum class PermissionStatus {
    Unknown,
    Granted,
    Denied,
    PermanentlyDenied;

    val isGranted get() = this == Granted
    val isDenied get() = (this == Denied) || (this == PermanentlyDenied)
}

interface MultiplePermissionHandler {
    val statuses: Map<String, PermissionStatus>
    fun launchPermissionRequest()
}

interface PermissionHandler {
    val status: PermissionStatus
    fun launchPermissionRequest()
}

/**
 * Helper function to find the Activity from a Context.
 */
private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

@Composable
fun rememberMultiplePermissions(
    permissions: List<String>,
    onResult: (status: Map<String, PermissionStatus>) -> Unit,
): MultiplePermissionHandler {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    var statuses by remember {
        mutableStateOf(
            permissions.associateWith { permission ->
                if (ContextCompat.checkSelfPermission(
                        context,
                        permission
                    ) == PackageManager.PERMISSION_GRANTED)
                    PermissionStatus.Granted
                else
                    PermissionStatus.Unknown
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { newPermissions ->
        statuses = newPermissions.mapValues { (permission, isGranted) ->
            when {
                isGranted -> PermissionStatus.Granted
                activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, permission) ->
                    PermissionStatus.Denied
                else -> PermissionStatus.PermanentlyDenied
            }
        }
        onResult(statuses)
    }

    val permissionHandler = remember(permissionLauncher) {
        object : MultiplePermissionHandler {
            override val statuses get() = statuses
            override fun launchPermissionRequest() =
                permissionLauncher.launch(permissions.toTypedArray())
        }
    }
    return permissionHandler
}

/**
 * Compose helper around [ActivityResultContracts.RequestPermission]. Mirrors
 * [rememberMultiplePermissions] but for a single permission — the result is
 * exposed as a [PermissionStatus] so callers can distinguish a soft denial
 * (we can prompt again) from a permanent one (we have to send users to the
 * system settings).
 */
@Composable
fun rememberSinglePermission(
    permission: String,
    onResult: (status: PermissionStatus) -> Unit = {},
): PermissionHandler {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    var status by remember {
        mutableStateOf(
            if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED)
                PermissionStatus.Granted
            else
                PermissionStatus.Unknown
        )
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        status = when {
            isGranted -> PermissionStatus.Granted
            activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, permission) ->
                PermissionStatus.Denied
            else -> PermissionStatus.PermanentlyDenied
        }
        onResult(status)
    }

    return remember(launcher) {
        object : PermissionHandler {
            override val status get() = status
            override fun launchPermissionRequest() = launcher.launch(permission)
        }
    }
}
