/** Lets users choose camera or gallery image sources. */
package it.unibo.equishare.ui.components.permissions

import android.Manifest
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import it.unibo.equishare.R
import it.unibo.equishare.utils.PermissionStatus
import it.unibo.equishare.utils.rememberSinglePermission
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageSourceBottomSheet(
    onDismiss: () -> Unit,
    onImagePicked: (Uri) -> Unit,
    onPermissionDenied: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Holds the Uri we asked the camera app to write to, so we can forward it
    // to the caller once the activity result comes back.
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCameraUri
        pendingCameraUri = null
        if (success && uri != null) {
            onImagePicked(uri)
            scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onImagePicked(uri)
            scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
        }
    }

    // Camera permission handler — when granted we immediately launch the
    // camera; when permanently denied we surface a short Toast so the user
    // knows they need to flip the switch in system settings.
    val cameraPermission = rememberSinglePermission(
        permission = Manifest.permission.CAMERA,
    ) { status ->
        when (status) {
            PermissionStatus.Granted -> launchCamera(context, cameraLauncher) { pendingCameraUri = it }
            PermissionStatus.PermanentlyDenied -> onPermissionDenied()
            else -> Unit
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.image_source_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            SourceOption(
                label = stringResource(R.string.image_source_take_photo),
                onClick = {
                    // Ask for camera permission only when the user actually
                    // picks "Take a photo". If it's already granted we can
                    // skip straight to launching the camera intent.
                    if (cameraPermission.status == PermissionStatus.Granted) {
                        launchCamera(context, cameraLauncher) { pendingCameraUri = it }
                    } else {
                        cameraPermission.launchPermissionRequest()
                    }
                },
                leading = { Icon(Icons.Default.PhotoCamera, contentDescription = null) },
            )

            SourceOption(
                label = stringResource(R.string.image_source_pick_gallery),
                onClick = {
                    galleryLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                leading = { Icon(Icons.Default.PhotoLibrary, contentDescription = null) },
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SourceOption(
    label: String,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun launchCamera(
    context: Context,
    launcher: androidx.activity.result.ActivityResultLauncher<Uri>,
    setPendingUri: (Uri) -> Unit,
) {
    val authority = "${context.packageName}.fileprovider"
    val cameraDir = File(context.cacheDir, "camera").apply { mkdirs() }
    val file = File.createTempFile("capture_", ".jpg", cameraDir)
    val uri: Uri = FileProvider.getUriForFile(context, authority, file)
    setPendingUri(uri)
    launcher.launch(uri)
}
