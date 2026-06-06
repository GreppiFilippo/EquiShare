/** Renders the groups create screen UI. */
package it.unibo.equishare.ui.screens.groups.create

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import it.unibo.equishare.R
import it.unibo.equishare.domain.model.ImageUpload
import it.unibo.equishare.ui.components.permissions.ImageSourceBottomSheet
import it.unibo.equishare.domain.model.AppCategory
import it.unibo.equishare.ui.components.buttons.EquiShareFab
import it.unibo.equishare.ui.components.image.ImagePreviewDialog
import it.unibo.equishare.ui.components.permissions.readPickedImage
import it.unibo.equishare.ui.components.refresh.EquiSharePullToRefresh
import it.unibo.equishare.ui.components.snackbar.EquiShareSnackbarHost
import it.unibo.equishare.ui.components.topbar.CompactBackAppBar
import java.util.Locale

data class NewGroupUiState(
    val groupName: String = "",
    val description: String = "",
    val photoUri: String? = null,
    val photoUpload: ImageUpload? = null,
    val categories: List<AppCategory> = emptyList(),
    val selectedCategory: AppCategory? = null,
    val groupNameError: String? = null,
    val isCategoriesLoading: Boolean = true,
    val categoriesError: String? = null,
    val isLoading: Boolean = false,
    val isCreated: Boolean = false,
    val createError: Boolean = false,
    val isRefreshing: Boolean = false,
) {
    val isFormValid: Boolean
        get() = groupName.isNotBlank() && selectedCategory != null && !isCategoriesLoading
}

sealed interface NewGroupEvent {
    data object BackClicked : NewGroupEvent
    data object AddPhotoClicked : NewGroupEvent
    data class GroupNameChanged(val value: String) : NewGroupEvent
    data class DescriptionChanged(val value: String) : NewGroupEvent
    data class CategorySelected(val category: AppCategory) : NewGroupEvent
    data class PhotoPicked(val localUri: String, val upload: ImageUpload) : NewGroupEvent
    data object CreateGroupClicked : NewGroupEvent
}

@Composable
fun NewGroupScreen(
    modifier: Modifier = Modifier,
    uiState: NewGroupUiState,
    onEvent: (NewGroupEvent) -> Unit,
    onRefresh: () -> Unit = {}
) {
    val scrollState = rememberScrollState()
    val canSubmit = uiState.isFormValid && !uiState.isLoading
    val isExpanded by remember(canSubmit) {
        derivedStateOf { scrollState.value == 0 && canSubmit }
    }
    // Locally held image override + bottom-sheet visibility. The picked Uri is
    // displayed immediately; persisting it server-side is handled separately
    // when the group is created.
    var pendingPhotoUri by rememberSaveable { mutableStateOf<String?>(null) }
    var previewPhotoUri by rememberSaveable { mutableStateOf<String?>(null) }
    var showImagePicker by rememberSaveable { mutableStateOf(false) }
    val effectivePhotoUri = pendingPhotoUri ?: uiState.photoUri
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val imageReadError = stringResource(R.string.image_read_failed)
    val cameraPermissionDeniedMessage = stringResource(R.string.camera_permission_rationale)
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { EquiShareSnackbarHost(hostState = snackbarHostState) },
        topBar = {
            CompactBackAppBar(
                title = stringResource(R.string.new_group),
                onBackClick = { onEvent(NewGroupEvent.BackClicked) },
            )
        },
        floatingActionButton = {
            EquiShareFab(
                onClick = { onEvent(NewGroupEvent.CreateGroupClicked) },
                expanded = isExpanded,
                enabled = canSubmit,
                contentDescription = stringResource(R.string.create_group_action),
                icon = {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                        )
                    }
                },
                text = { Text(text = stringResource(R.string.create_group_action)) },
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
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp)
                    .padding(top = 24.dp, bottom = 96.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
            PhotoPicker(
                photoUri = effectivePhotoUri,
                onClick = {
                    if (effectivePhotoUri != null) {
                        previewPhotoUri = effectivePhotoUri
                    } else {
                        // Surface the chooser; the camera-permission prompt is
                        // only raised if the user picks "Take a photo".
                        showImagePicker = true
                        onEvent(NewGroupEvent.AddPhotoClicked)
                    }
                },
            )

            OutlinedTextField(
                value = uiState.groupName,
                onValueChange = { onEvent(NewGroupEvent.GroupNameChanged(it)) },
                label = { Text(stringResource(R.string.group_name_label)) },
                isError = uiState.groupNameError != null,
                supportingText = uiState.groupNameError?.let { { Text(it) } },
                singleLine = true,
                trailingIcon = if (uiState.groupName.isNotEmpty()) {
                    {
                        IconButton(onClick = { onEvent(NewGroupEvent.GroupNameChanged("")) }) {
                            Icon(
                                imageVector = Icons.Default.Cancel,
                                contentDescription = stringResource(R.string.clear),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    null
                },
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = outlinedTextFieldColors(),
            )

            OutlinedTextField(
                value = uiState.description,
                onValueChange = { onEvent(NewGroupEvent.DescriptionChanged(it)) },
                label = { Text(stringResource(R.string.description)) },
                singleLine = false,
                minLines = 3,
                trailingIcon = if (uiState.description.isNotEmpty()) {
                    {
                        IconButton(onClick = { onEvent(NewGroupEvent.DescriptionChanged("")) }) {
                            Icon(
                                imageVector = Icons.Default.Cancel,
                                contentDescription = stringResource(R.string.clear),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    null
                },
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = outlinedTextFieldColors(),
            )

            Text(
                text = stringResource(R.string.category_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )

            CategoryPicker(
                uiState = uiState,
                locale = locale,
                onCategorySelected = { onEvent(NewGroupEvent.CategorySelected(it)) },
            )

                if (uiState.createError) {
                    Text(
                        text = stringResource(R.string.create_group_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    if (showImagePicker) {
        ImageSourceBottomSheet(
            onDismiss = { showImagePicker = false },
            onImagePicked = { uri ->
                uri.readPickedImage(context)
                    .onSuccess { picked ->
                        pendingPhotoUri = picked.uri
                        onEvent(NewGroupEvent.PhotoPicked(picked.uri, picked.upload))
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

    previewPhotoUri?.let { uri ->
        ImagePreviewDialog(
            imageUri = uri,
            contentDescription = stringResource(R.string.group_photo),
            title = stringResource(R.string.group_photo),
            onDismiss = { previewPhotoUri = null },
            onEdit = {
                previewPhotoUri = null
                showImagePicker = true
                onEvent(NewGroupEvent.AddPhotoClicked)
            },
        )
    }
}

@Composable
private fun PhotoPicker(
    photoUri: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(
                    width = 1.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(28.dp),
                )
                .clickable { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            if (photoUri != null) {
                AsyncImage(
                    model = photoUri,
                    contentDescription = stringResource(R.string.add_group_photo),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(140.dp)
                        .clip(RoundedCornerShape(28.dp)),
                )
            } else {
                // Single, large "add a photo" affordance — replaces the previous
                // AttachMoney + AddAPhoto stack which read more like an expense
                // affordance than a group identity one.
                Icon(
                    imageVector = Icons.Default.AddAPhoto,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(44.dp),
                )
            }
        }

        Text(
            text = stringResource(R.string.add_group_photo),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun CategoryPicker(
    uiState: NewGroupUiState,
    locale: Locale,
    onCategorySelected: (AppCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        uiState.isCategoriesLoading -> {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .height(112.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
        uiState.categoriesError != null -> {
            Text(
                text = uiState.categoriesError,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = modifier.fillMaxWidth(),
            )
        }
        uiState.categories.isEmpty() -> {
            Text(
                text = stringResource(R.string.select_category),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = modifier.fillMaxWidth(),
            )
        }
        else -> {
            Column(
                modifier = modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                uiState.categories
                    .chunked(2)
                    .forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            rowItems.forEach { category ->
                                CategoryCard(
                                    category = category,
                                    locale = locale,
                                    isSelected = uiState.selectedCategory?.id == category.id,
                                    onClick = { onCategorySelected(category) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (rowItems.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
            }
        }
    }
}

@Composable
private fun CategoryCard(
    category: AppCategory,
    locale: Locale,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bgColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    val labelColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val labelWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
    val categoryLabel = category.localizedName(locale)

    Box(
        modifier = modifier
            .heightIn(min = 96.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(
                width = 0.5.dp,
                color = borderColor,
                shape = RoundedCornerShape(8.dp),
            )
            .clickable { onClick() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = category.category.icon,
                contentDescription = categoryLabel,
                tint = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = categoryLabel,
                style = MaterialTheme.typography.labelLarge,
                color = labelColor,
                fontWeight = labelWeight,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun outlinedTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    cursorColor = MaterialTheme.colorScheme.primary,
    focusedContainerColor = MaterialTheme.colorScheme.background,
    unfocusedContainerColor = MaterialTheme.colorScheme.background,
)

private fun AppCategory.localizedName(locale: Locale): String = localizedName(locale.language)

