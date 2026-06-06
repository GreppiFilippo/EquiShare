/** Renders the profile screen UI. */
package it.unibo.equishare.ui.screens.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import it.unibo.equishare.R
import it.unibo.equishare.ui.components.avatar.Avatar
import it.unibo.equishare.ui.components.image.ImagePreviewDialog
import it.unibo.equishare.ui.components.permissions.ImageSourceBottomSheet
import it.unibo.equishare.ui.components.permissions.readPickedImage
import it.unibo.equishare.ui.components.refresh.EquiSharePullToRefresh
import it.unibo.equishare.ui.components.snackbar.EquiShareSnackbarHost
import it.unibo.equishare.ui.components.topbar.CompactBackAppBar
import java.util.Locale
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────────
// Theme option enum
// ─────────────────────────────────────────────────────────────────────────────

enum class ThemeOption { LIGHT, DARK, SYSTEM }

// ─────────────────────────────────────────────────────────────────────────────
// UI State
// ─────────────────────────────────────────────────────────────────────────────

data class ProfileUiState(
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val avatarUrl: String? = null,
    val notificationsEnabled: Boolean = true,
    val selectedTheme: ThemeOption = ThemeOption.SYSTEM,
    val selectedLanguageTag: String? = null,
    val supportedLanguageTags: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val isAvatarUploading: Boolean = false,
    val avatarError: String? = null,
    val isRefreshing: Boolean = false,
    val isLoggingOut: Boolean = false,
)

// ─────────────────────────────────────────────────────────────────────────────
// Events
// ─────────────────────────────────────────────────────────────────────────────

sealed interface ProfileEvent {
    data object BackClicked : ProfileEvent
    data object ChangeAvatarClicked : ProfileEvent
    data class AvatarPicked(val bytes: ByteArray, val mimeType: String) : ProfileEvent
    data object AvatarErrorShown : ProfileEvent
    data class NotificationsToggled(val enabled: Boolean) : ProfileEvent
    data class ThemeSelected(val theme: ThemeOption) : ProfileEvent
    data class LanguageSelected(val languageTag: String?) : ProfileEvent
    data object LogOutClicked : ProfileEvent
}

// ─────────────────────────────────────────────────────────────────────────────
// Design tokens
// ─────────────────────────────────────────────────────────────────────────────

// ─────────────────────────────────────────────────────────────────────────────
// Screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier,
    uiState: ProfileUiState,
    onEvent: (ProfileEvent) -> Unit,
    onRefresh: () -> Unit = {},
) {
    // Local-only override that holds the avatar Uri the user just picked.
    // Until the actual upload flow is wired up, this gives the user instant
    // visual feedback that their selection was registered.
    var pendingAvatarUri by rememberSaveable { mutableStateOf<String?>(null) }
    var previewAvatarUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var showImagePicker by rememberSaveable { mutableStateOf(false) }
    var showLanguagePicker by rememberSaveable { mutableStateOf(false) }
    val effectiveAvatarUrl = pendingAvatarUri ?: uiState.avatarUrl
    val context = LocalContext.current
    val displayLocale = LocalConfiguration.current.locales[0]
    val uploadError = uiState.avatarError
    val genericUploadError = stringResource(R.string.image_upload_failed)
    val imageReadError = stringResource(R.string.image_read_failed)
    val cameraPermissionDeniedMessage = stringResource(R.string.camera_permission_rationale)
    val languageSystem = stringResource(R.string.language_system)
    val selectedLanguageLabel = remember(
        uiState.selectedLanguageTag,
        languageSystem,
        displayLocale,
    ) {
        uiState.selectedLanguageTag?.let { languageDisplayName(it, displayLocale) }
            ?: languageSystem
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()

    LaunchedEffect(uploadError) {
        if (uploadError != null) {
            pendingAvatarUri = null
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(genericUploadError)
            onEvent(ProfileEvent.AvatarErrorShown)
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { EquiShareSnackbarHost(hostState = snackbarHostState) },
        topBar = {
            CompactBackAppBar(
                onBackClick = { onEvent(ProfileEvent.BackClicked) },
            )
        },
    ) { innerPadding ->
        EquiSharePullToRefresh(
            isRefreshing = uiState.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.padding(innerPadding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {

            // ── Avatar ─────────────────────────────────────────────────────
            Box(
                modifier = Modifier.padding(top = 24.dp),
                contentAlignment = Alignment.BottomEnd,
            ) {
                // Avatar ring
                Box(
                    modifier = Modifier
                        .size(112.dp)
                        .shadow(elevation = 6.dp, shape = CircleShape)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(
                            width = 4.dp,
                            color = MaterialTheme.colorScheme.surface,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Avatar(
                        imageUrl = effectiveAvatarUrl,
                        displayName = "${uiState.firstName} ${uiState.lastName}",
                        size = 104.dp,
                        contentDescription = stringResource(R.string.profile_picture),
                        onClicked = {
                            if (effectiveAvatarUrl != null) {
                                previewAvatarUrl = effectiveAvatarUrl
                            }
                        },
                    )
                    if (uiState.isAvatarUploading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            strokeWidth = 3.dp,
                        )
                    }
                }

                // Camera FAB
                FloatingActionButton(
                    onClick = {
                        // Show the image-source sheet locally and forward the
                        // event to the VM in case it ever needs to react to
                        // the click (e.g. analytics).
                        showImagePicker = true
                        onEvent(ProfileEvent.ChangeAvatarClicked)
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .offset(x = (-4).dp, y = (-4).dp),
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 6.dp,
                        pressedElevation = 12.dp,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = stringResource(R.string.change_avatar),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            // ── Greeting ───────────────────────────────────────────────────
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.hi_greeting, uiState.firstName, uiState.lastName),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
            )

            Spacer(Modifier.height(24.dp))

            // ── Profile Info section ───────────────────────────────────────
            SectionLabel(text = stringResource(R.string.profile_info))

            Spacer(Modifier.height(8.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                ProfileInfoRow(label = stringResource(R.string.first_name), value = uiState.firstName)
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    thickness = 1.dp
                )
                ProfileInfoRow(label = stringResource(R.string.last_name), value = uiState.lastName)
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    thickness = 1.dp
                )
                ProfileInfoRow(label = stringResource(R.string.email), value = uiState.email)
            }

            Spacer(Modifier.height(24.dp))

            // ── Settings section ───────────────────────────────────────────
            SectionLabel(text = stringResource(R.string.settings))

            Spacer(Modifier.height(8.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                // Notifications row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 54.dp)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = stringResource(R.string.notifications),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = uiState.notificationsEnabled,
                        onCheckedChange = { onEvent(ProfileEvent.NotificationsToggled(it)) },
                    )
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    thickness = 1.dp
                )

                // Theme row
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.SettingsSuggest,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp),
                        )
                        Text(
                            text = stringResource(R.string.theme),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    ThemeSegmentedControl(
                        selected = uiState.selectedTheme,
                        onSelect = { onEvent(ProfileEvent.ThemeSelected(it)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    thickness = 1.dp
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .clickable { showLanguagePicker = true }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.language),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = selectedLanguageLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── Log Out button ─────────────────────────────────────────────
            OutlinedButton(
                onClick = { onEvent(ProfileEvent.LogOutClicked) },
                enabled = !uiState.isLoggingOut,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .heightIn(min = 50.dp),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.error),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
            ) {
                if (uiState.isLoggingOut) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.log_out),
                    style = MaterialTheme.typography.labelLarge,
                )
            }

                Spacer(Modifier.height(32.dp))
            }
        }
    }

    if (showImagePicker) {
        ImageSourceBottomSheet(
            onDismiss = { showImagePicker = false },
            onImagePicked = { uri ->
                uri.readPickedImage(context)
                    .onSuccess { picked ->
                        pendingAvatarUri = picked.uri
                        onEvent(ProfileEvent.AvatarPicked(picked.upload.bytes, picked.upload.mimeType))
                    }
                    .onFailure {
                        snackbarScope.launch {
                            snackbarHostState.currentSnackbarData?.dismiss()
                            snackbarHostState.showSnackbar(imageReadError)
                        }
                    }
                showImagePicker = false
            },
            onPermissionDenied = {
                snackbarScope.launch {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(cameraPermissionDeniedMessage)
                }
            },
        )
    }

    previewAvatarUrl?.let { url ->
        ImagePreviewDialog(
            imageUri = url,
            contentDescription = stringResource(R.string.profile_picture),
            title = stringResource(R.string.profile_picture),
            onDismiss = { previewAvatarUrl = null },
            onEdit = {
                previewAvatarUrl = null
                showImagePicker = true
                onEvent(ProfileEvent.ChangeAvatarClicked)
            },
        )
    }

    if (showLanguagePicker) {
        LanguagePickerDialog(
            selectedLanguageTag = uiState.selectedLanguageTag,
            supportedLanguageTags = uiState.supportedLanguageTags,
            onLanguageSelected = { tag ->
                onEvent(ProfileEvent.LanguageSelected(tag))
                showLanguagePicker = false
            },
            onDismiss = { showLanguagePicker = false },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sub-composables
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
    )
}

@Composable
private fun ProfileInfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ThemeSegmentedControl(
    selected: ThemeOption,
    onSelect: (ThemeOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    data class Segment(val option: ThemeOption, val icon: ImageVector, val label: String)

    val segments = listOf(
        Segment(ThemeOption.LIGHT, Icons.Default.LightMode, stringResource(R.string.light)),
        Segment(ThemeOption.DARK, Icons.Default.DarkMode, stringResource(R.string.dark)),
        Segment(ThemeOption.SYSTEM, Icons.Default.SettingsSuggest, stringResource(R.string.system)),
    )

    val containerShape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .height(48.dp)
            .clip(containerShape)
            .border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                containerShape,
            )
    ) {
        segments.forEachIndexed { index, segment ->
            val isSelected = selected == segment.option
            if (index > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Color.Transparent
                        }
                    )
                    .clickable { onSelect(segment.option) },
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = segment.icon,
                        contentDescription = null,
                        tint = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = segment.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Preview
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LanguagePickerDialog(
    selectedLanguageTag: String?,
    supportedLanguageTags: List<String>,
    onLanguageSelected: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val displayLocale = LocalConfiguration.current.locales[0]
    val systemLabel = stringResource(R.string.language_system)
    val systemDescription = stringResource(R.string.language_system_description)
    val selectableLanguageTags = remember(supportedLanguageTags, selectedLanguageTag) {
        if (
            selectedLanguageTag == null ||
            supportedLanguageTags.any { it.equals(selectedLanguageTag, ignoreCase = true) }
        ) {
            supportedLanguageTags
        } else {
            supportedLanguageTags + selectedLanguageTag
        }
    }

    val options = remember(selectableLanguageTags, displayLocale, systemLabel, systemDescription) {
        listOf(
            LanguageSelectionOption(
                tag = null,
                title = systemLabel,
                subtitle = systemDescription,
            )
        ) + selectableLanguageTags.map { tag ->
            LanguageSelectionOption(
                tag = tag,
                title = languageDisplayName(tag, displayLocale),
                subtitle = tag,
            )
        }.sortedBy { it.title.lowercase(displayLocale) }
    }
    val filteredOptions = remember(options, query) {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) {
            options
        } else {
            options.filter { it.matches(normalizedQuery) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.language)) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                        )
                    },
                    placeholder = { Text(stringResource(R.string.language_search)) },
                )
                Spacer(Modifier.height(12.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                ) {
                    if (filteredOptions.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.language_no_results),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 16.dp),
                            )
                        }
                    } else {
                        items(
                            items = filteredOptions,
                            key = { it.tag ?: "system" },
                        ) { option ->
                            LanguageOptionRow(
                                option = option,
                                selected = languageTagsEqual(option.tag, selectedLanguageTag),
                                onClick = { onLanguageSelected(option.tag) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.close))
            }
        },
    )
}

@Composable
private fun LanguageOptionRow(
    option: LanguageSelectionOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = option.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            option.subtitle?.let { subtitle ->
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private data class LanguageSelectionOption(
    val tag: String?,
    val title: String,
    val subtitle: String?,
) {
    fun matches(query: String): Boolean =
        title.contains(query, ignoreCase = true) ||
            subtitle?.contains(query, ignoreCase = true) == true ||
            tag?.contains(query, ignoreCase = true) == true
}

private fun languageDisplayName(
    tag: String,
    displayLocale: Locale,
): String {
    val locale = Locale.forLanguageTag(tag)
    val localizedName = locale.getDisplayName(displayLocale).toDisplayTitle(displayLocale)
    val nativeName = locale.getDisplayName(locale).toDisplayTitle(locale)

    return if (localizedName.equals(nativeName, ignoreCase = true)) {
        localizedName
    } else {
        "$localizedName ($nativeName)"
    }
}

private fun String.toDisplayTitle(locale: Locale): String =
    replaceFirstChar { char ->
        if (char.isLowerCase()) char.titlecase(locale) else char.toString()
    }

private fun languageTagsEqual(
    first: String?,
    second: String?,
): Boolean =
    first?.equals(second, ignoreCase = true) ?: (second == null)

