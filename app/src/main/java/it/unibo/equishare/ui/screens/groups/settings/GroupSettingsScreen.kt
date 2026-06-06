/** Renders the groups settings screen UI. */
package it.unibo.equishare.ui.screens.groups.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import it.unibo.equishare.R
import it.unibo.equishare.ui.components.avatar.Avatar
import it.unibo.equishare.ui.components.image.ImagePreviewDialog
import it.unibo.equishare.ui.components.permissions.ImageSourceBottomSheet
import it.unibo.equishare.ui.components.permissions.readPickedImage
import it.unibo.equishare.ui.components.refresh.EquiSharePullToRefresh
import it.unibo.equishare.ui.components.snackbar.EquiShareSnackbarHost
import it.unibo.equishare.ui.components.topbar.CompactBackAppBar
import it.unibo.equishare.ui.theme.EquiShareTheme
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────────
// Domain models
// ─────────────────────────────────────────────────────────────────────────────

data class GroupMember(
    val id: String,
    val displayName: String,    // e.g. "You", "Marco Ferrari"
    val email: String,
    val avatarUrl: String? = null,
    val isAdmin: Boolean = false,
    val isCurrentUser: Boolean = false,
)

// ─────────────────────────────────────────────────────────────────────────────
// UI State
// ─────────────────────────────────────────────────────────────────────────────

data class GroupSettingsUiState(
    val groupName: String = "",
    val groupDescription: String = "",
    val groupPhotoUrl: String? = null,
    val members: List<GroupMember> = emptyList(),
    val inviteEmail: String = "",
    val isInviteLoading: Boolean = false,
    val isPhotoUploading: Boolean = false,
    val isDangerActionLoading: Boolean = false,
    val isCurrentUserAdmin: Boolean = false,
    val canLeaveGroup: Boolean = true,
    val isClosed: Boolean = false,
    val isRefreshing: Boolean = false,
) {
    val adminSuccessorCandidates: List<GroupMember>
        get() = if (isCurrentUserAdmin) members.filterNot { it.isCurrentUser } else emptyList()
}

// ─────────────────────────────────────────────────────────────────────────────
// Events
// ─────────────────────────────────────────────────────────────────────────────

sealed interface GroupSettingsEvent {
    data object BackClicked : GroupSettingsEvent
    data object ChangePhotoClicked : GroupSettingsEvent
    data class PhotoPicked(val bytes: ByteArray, val mimeType: String) : GroupSettingsEvent
    data object EditGroupInfoClicked : GroupSettingsEvent
    data class GroupInfoSubmitted(val name: String, val description: String) : GroupSettingsEvent
    data class InviteEmailChanged(val value: String) : GroupSettingsEvent
    data object InviteClicked : GroupSettingsEvent
    data class RemoveMemberClicked(val memberId: String) : GroupSettingsEvent
    data class LeaveGroupClicked(val successorMemberId: String? = null) : GroupSettingsEvent
    data object DeleteGroupClicked : GroupSettingsEvent
}

// ─────────────────────────────────────────────────────────────────────────────
// Design tokens
// ─────────────────────────────────────────────────────────────────────────────

// ─────────────────────────────────────────────────────────────────────────────
// Screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun GroupSettingsScreen(
    uiState: GroupSettingsUiState,
    onEvent: (GroupSettingsEvent) -> Unit,
    modifier: Modifier = Modifier,
    feedback: Flow<GroupSettingsFeedback> = emptyFlow(),
    onRefresh: () -> Unit = {},
) {
    // Confirmation dialog visibility is screen-local — the actual destructive
    // events are only emitted once the user taps the dialog's Confirm button,
    // so a misclick on Leave/Delete in the danger zone is always recoverable.
    var showLeaveDialog by rememberSaveable { mutableStateOf(false) }
    var showAdminSuccessorDialog by rememberSaveable { mutableStateOf(false) }
    var selectedSuccessorId by rememberSaveable { mutableStateOf<String?>(null) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    // Member removal confirmation: stores (memberId, displayName) of the candidate.
    var pendingRemoveMemberId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingRemoveMemberName by rememberSaveable { mutableStateOf<String?>(null) }
    var showEditInfoDialog by rememberSaveable { mutableStateOf(false) }
    var pendingGroupPhotoUri by rememberSaveable { mutableStateOf<String?>(null) }
    var previewGroupPhotoUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var previewMemberAvatarUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var previewMemberName by rememberSaveable { mutableStateOf<String?>(null) }
    var showImagePicker by rememberSaveable { mutableStateOf(false) }
    val effectiveGroupPhotoUrl = pendingGroupPhotoUri ?: uiState.groupPhotoUrl
    val adminSuccessorCandidates = uiState.adminSuccessorCandidates
    val automaticAdminSuccessor = adminSuccessorCandidates.singleOrNull()

    // Snackbar plumbing for invite feedback. Strings are resolved here (not
    // in the ViewModel) so the message follows the user's current locale.
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()
    val msgInviteAlready = stringResource(R.string.invite_feedback_already_member)
    val msgInviteNotFound = stringResource(R.string.invite_feedback_not_found)
    val msgInviteForbidden = stringResource(R.string.invite_feedback_forbidden)
    val msgInviteSelf = stringResource(R.string.invite_feedback_self)
    val msgInviteError = stringResource(R.string.invite_feedback_error)
    val inviteSuccessTemplate = stringResource(R.string.invite_feedback_success)
    val inviteAlreadyInvitedTemplate = stringResource(R.string.invite_feedback_already_invited)
    val photoUpdatedMessage = stringResource(R.string.photo_upload_success)
    val photoErrorMessage = stringResource(R.string.image_upload_failed)
    val leaveBlockedMessage = stringResource(R.string.leave_group_blocked_balance)
    val leaveSuccessorRequiredMessage = stringResource(R.string.leave_group_successor_required)
    val leaveErrorMessage = stringResource(R.string.leave_group_failed)
    val removeMemberBlockedMessage = stringResource(R.string.remove_member_blocked_balance)
    val removeMemberErrorMessage = stringResource(R.string.remove_member_failed)
    val removeMemberConfirmTitle = stringResource(R.string.remove_member_confirm_title)
    val removeMemberConfirmMessage = stringResource(R.string.remove_member_confirm_message)
    val removeMemberConfirmAction = stringResource(R.string.remove_member_confirm_action)
    val deleteForbiddenMessage = stringResource(R.string.delete_group_admin_only)
    val deleteErrorMessage = stringResource(R.string.delete_group_failed)
    val imageReadError = stringResource(R.string.image_read_failed)
    val photoAdminOnlyMessage = stringResource(R.string.group_photo_admin_only)
    val cameraPermissionDeniedMessage = stringResource(R.string.camera_permission_rationale)
    val groupInfoUpdatedMessage = stringResource(R.string.group_info_updated)
    val groupInfoUpdateFailedMessage = stringResource(R.string.group_info_update_failed)
    val context = LocalContext.current

    LaunchedEffect(showAdminSuccessorDialog, adminSuccessorCandidates) {
        if (showAdminSuccessorDialog && selectedSuccessorId !in adminSuccessorCandidates.map { it.id }) {
            selectedSuccessorId = null
        }
    }

    LaunchedEffect(feedback) {
        feedback.collect { event ->
            val text = when (event) {
                is GroupSettingsFeedback.InviteSent ->
                    inviteSuccessTemplate.format(event.displayName)
                is GroupSettingsFeedback.InviteAlreadyInvited ->
                    inviteAlreadyInvitedTemplate.format(event.displayName)
                GroupSettingsFeedback.InviteAlreadyMember -> msgInviteAlready
                GroupSettingsFeedback.InviteNotFound      -> msgInviteNotFound
                GroupSettingsFeedback.InviteForbidden     -> msgInviteForbidden
                GroupSettingsFeedback.InviteSelf          -> msgInviteSelf
                GroupSettingsFeedback.InviteError         -> msgInviteError
                GroupSettingsFeedback.PhotoUpdated        -> photoUpdatedMessage
                GroupSettingsFeedback.PhotoError          -> {
                    pendingGroupPhotoUri = null
                    photoErrorMessage
                }
                GroupSettingsFeedback.LeaveBlockedByBalance -> leaveBlockedMessage
                GroupSettingsFeedback.LeaveSuccessorRequired -> leaveSuccessorRequiredMessage
                GroupSettingsFeedback.LeaveError            -> leaveErrorMessage
                GroupSettingsFeedback.RemoveMemberBlockedByBalance -> removeMemberBlockedMessage
                GroupSettingsFeedback.RemoveMemberError     -> removeMemberErrorMessage
                GroupSettingsFeedback.DeleteForbidden       -> deleteForbiddenMessage
                GroupSettingsFeedback.DeleteError           -> deleteErrorMessage
                GroupSettingsFeedback.GroupInfoUpdated      -> groupInfoUpdatedMessage
                GroupSettingsFeedback.GroupInfoUpdateError  -> groupInfoUpdateFailedMessage
            }
            // Replace any in-flight Snackbar so rapid-fire invites don't pile up.
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message = text)
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { EquiShareSnackbarHost(hostState = snackbarHostState) },
        topBar = {
            CompactBackAppBar(
                title = stringResource(R.string.group_settings),
                onBackClick = { onEvent(GroupSettingsEvent.BackClicked) },
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
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {

            // ── 1. Group Photo ─────────────────────────────────────────────
            // Only admins can change the photo. Non-admin taps surface a
            // toast instead of opening the picker so the rule is explicit.
            GroupPhotoCard(
                photoUrl = effectiveGroupPhotoUrl,
                isUploading = uiState.isPhotoUploading,
                canEdit = uiState.isCurrentUserAdmin,
                onClick = {
                    if (effectiveGroupPhotoUrl != null) {
                        previewGroupPhotoUrl = effectiveGroupPhotoUrl
                    } else if (uiState.isCurrentUserAdmin) {
                        showImagePicker = true
                        onEvent(GroupSettingsEvent.ChangePhotoClicked)
                    } else {
                        snackbarScope.launch {
                            snackbarHostState.currentSnackbarData?.dismiss()
                            snackbarHostState.showSnackbar(photoAdminOnlyMessage)
                        }
                    }
                },
            )

            // ── 2. Group Information ───────────────────────────────────────
            // The pencil affordance is admin-only; everyone else sees a
            // read-only card.
            GroupInfoCard(
                groupName = uiState.groupName,
                groupDescription = uiState.groupDescription,
                canEdit = uiState.isCurrentUserAdmin,
                onEditClick = {
                    showEditInfoDialog = true
                    onEvent(GroupSettingsEvent.EditGroupInfoClicked)
                },
            )

            // ── 3. Invite Members ──────────────────────────────────────────
            InviteMembersCard(
                emailValue = uiState.inviteEmail,
                onEmailChange = { onEvent(GroupSettingsEvent.InviteEmailChanged(it)) },
                onInviteClick = { onEvent(GroupSettingsEvent.InviteClicked) },
                isLoading = uiState.isInviteLoading,
            )

            // ── 4. Members list ────────────────────────────────────────────
            MembersCard(
                members = uiState.members,
                isCurrentUserAdmin = uiState.isCurrentUserAdmin,
                onRemoveMember = { memberId ->
                    // Store the target and open the confirmation dialog;
                    // the actual event is emitted only after the user confirms.
                    val member = uiState.members.firstOrNull { it.id == memberId }
                    pendingRemoveMemberId = memberId
                    pendingRemoveMemberName = member?.displayName.orEmpty()
                },
                onAvatarClick = { member ->
                    member.avatarUrl?.let { url ->
                        previewMemberAvatarUrl = url
                        previewMemberName = member.displayName
                    }
                },
            )

            // ── 5. Danger Zone ─────────────────────────────────────────────
                DangerZoneCard(
                    canLeaveGroup = uiState.canLeaveGroup,
                    showDeleteGroup = uiState.isCurrentUserAdmin,
                    isLoading = uiState.isDangerActionLoading,
                    onLeaveGroup = {
                        if (uiState.isCurrentUserAdmin && adminSuccessorCandidates.size > 1) {
                            selectedSuccessorId = null
                            showAdminSuccessorDialog = true
                        } else {
                            showLeaveDialog = true
                        }
                    },
                    onDeleteGroup = { showDeleteDialog = true },
                )
            }
        }
    }

    // ── Confirmation dialogs ────────────────────────────────────────────────

    if (showLeaveDialog) {
        val leaveTitle = when {
            uiState.isCurrentUserAdmin && adminSuccessorCandidates.isEmpty() ->
                stringResource(R.string.leave_group_delete_confirm_title)
            uiState.isCurrentUserAdmin && automaticAdminSuccessor != null ->
                stringResource(R.string.leave_group_auto_transfer_confirm_title)
            else ->
                stringResource(R.string.leave_group_confirm_title)
        }
        val leaveMessage = when {
            uiState.isCurrentUserAdmin && adminSuccessorCandidates.isEmpty() ->
                stringResource(R.string.leave_group_delete_confirm_message)
            uiState.isCurrentUserAdmin && automaticAdminSuccessor != null ->
                stringResource(
                    R.string.leave_group_auto_transfer_confirm_message,
                    automaticAdminSuccessor.displayName,
                )
            else ->
                stringResource(R.string.leave_group_confirm_message)
        }
        DestructiveConfirmDialog(
            title = leaveTitle,
            message = leaveMessage,
            confirmLabel = stringResource(R.string.leave_group_confirm_action),
            onDismiss = { showLeaveDialog = false },
            onConfirm = {
                showLeaveDialog = false
                onEvent(GroupSettingsEvent.LeaveGroupClicked(automaticAdminSuccessor?.id))
            },
        )
    }

    if (showAdminSuccessorDialog && uiState.isCurrentUserAdmin) {
        AdminSuccessorDialog(
            candidates = adminSuccessorCandidates,
            selectedMemberId = selectedSuccessorId,
            onMemberSelected = { selectedSuccessorId = it },
            onAvatarClick = { member ->
                member.avatarUrl?.let { url ->
                    previewMemberAvatarUrl = url
                    previewMemberName = member.displayName
                }
            },
            onDismiss = { showAdminSuccessorDialog = false },
            onConfirm = { successorId ->
                showAdminSuccessorDialog = false
                selectedSuccessorId = null
                onEvent(GroupSettingsEvent.LeaveGroupClicked(successorId))
            },
        )
    }

    if (showDeleteDialog) {
        DestructiveConfirmDialog(
            title = stringResource(R.string.delete_group_confirm_title),
            message = stringResource(R.string.delete_group_confirm_message),
            confirmLabel = stringResource(R.string.delete_group_confirm_action),
            onDismiss = { showDeleteDialog = false },
            onConfirm = {
                showDeleteDialog = false
                onEvent(GroupSettingsEvent.DeleteGroupClicked)
            },
        )
    }

    if (pendingRemoveMemberId != null) {
        DestructiveConfirmDialog(
            title = removeMemberConfirmTitle,
            message = removeMemberConfirmMessage.format(pendingRemoveMemberName.orEmpty()),
            confirmLabel = removeMemberConfirmAction,
            onDismiss = {
                pendingRemoveMemberId = null
                pendingRemoveMemberName = null
            },
            onConfirm = {
                val id = pendingRemoveMemberId
                pendingRemoveMemberId = null
                pendingRemoveMemberName = null
                if (id != null) onEvent(GroupSettingsEvent.RemoveMemberClicked(id))
            },
        )
    }

    // The bottom sheet is only opened from the admin code path. The extra
    // `isCurrentUserAdmin` guard is belt-and-braces against a stale state
    // (e.g. the user's role was downgraded while the sheet was open).
    if (showImagePicker && uiState.isCurrentUserAdmin) {
        ImageSourceBottomSheet(
            onDismiss = { showImagePicker = false },
            onImagePicked = { uri ->
                uri.readPickedImage(context)
                    .onSuccess { picked ->
                        pendingGroupPhotoUri = picked.uri
                        onEvent(GroupSettingsEvent.PhotoPicked(picked.upload.bytes, picked.upload.mimeType))
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

    previewGroupPhotoUrl?.let { url ->
        ImagePreviewDialog(
            imageUri = url,
            contentDescription = stringResource(R.string.group_photo),
            title = stringResource(R.string.group_photo),
            onDismiss = { previewGroupPhotoUrl = null },
            onEdit = if (uiState.isCurrentUserAdmin) {
                {
                    previewGroupPhotoUrl = null
                    showImagePicker = true
                    onEvent(GroupSettingsEvent.ChangePhotoClicked)
                }
            } else {
                null
            },
        )
    }

    previewMemberAvatarUrl?.let { url ->
        val previewTitle = previewMemberName ?: stringResource(R.string.profile_picture)
        ImagePreviewDialog(
            imageUri = url,
            contentDescription = previewTitle,
            title = previewTitle,
            onDismiss = {
                previewMemberAvatarUrl = null
                previewMemberName = null
            },
        )
    }

    if (showEditInfoDialog && uiState.isCurrentUserAdmin) {
        EditGroupInfoDialog(
            initialName = uiState.groupName,
            initialDescription = uiState.groupDescription,
            onDismiss = { showEditInfoDialog = false },
            onConfirm = { name, description ->
                showEditInfoDialog = false
                onEvent(GroupSettingsEvent.GroupInfoSubmitted(name, description))
            },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Edit Group Info Dialog
// ─────────────────────────────────────────────────────────────────────────────
//
// Admin-only dialog for updating the group's name + description. Name is
// required; description is free-form. Save is disabled while the name is
// blank so the dialog can't dispatch a no-op update.

@Composable
private fun EditGroupInfoDialog(
    initialName: String,
    initialDescription: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, description: String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var description by rememberSaveable { mutableStateOf(initialDescription) }
    val canSave = name.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        icon = {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = {
            Text(
                text = stringResource(R.string.edit_group_info),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.group_name_label)) },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.description)) },
                    shape = RoundedCornerShape(8.dp),
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim(), description.trim()) },
                enabled = canSave,
            ) {
                Text(
                    text = stringResource(R.string.save),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel))
            }
        },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Destructive Confirmation Dialog
// ─────────────────────────────────────────────────────────────────────────────
//
// Shared M3 AlertDialog used for both Leave and Delete. Confirm CTA is tinted
// `error` to flag the irreversibility; Cancel is the neutral default.

@Composable
private fun AdminSuccessorDialog(
    candidates: List<GroupMember>,
    selectedMemberId: String?,
    onMemberSelected: (String) -> Unit,
    onAvatarClick: (GroupMember) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        icon = {
            Icon(
                imageVector = Icons.Default.PersonAdd,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = {
            Text(
                text = stringResource(R.string.leave_group_select_admin_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.leave_group_select_admin_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    candidates.forEach { member ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onMemberSelected(member.id) }
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = selectedMemberId == member.id,
                                onClick = { onMemberSelected(member.id) },
                            )
                            Spacer(Modifier.width(8.dp))
                            Avatar(
                                imageUrl = member.avatarUrl,
                                displayName = member.displayName,
                                size = 36.dp,
                                contentDescription = member.displayName,
                                onClicked = { onAvatarClick(member) },
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = member.displayName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                if (member.email.isNotBlank()) {
                                    Text(
                                        text = member.email,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { selectedMemberId?.let(onConfirm) },
                enabled = selectedMemberId != null,
            ) {
                Text(
                    text = stringResource(R.string.leave_group_select_admin_confirm),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun DestructiveConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        icon = {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text(
                    text = confirmLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel))
            }
        },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// 1 — Group Photo Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun GroupPhotoCard(
    photoUrl: String?,
    isUploading: Boolean,
    canEdit: Boolean,
    onClick: () -> Unit,
) {
    SettingsCard(borderColor = MaterialTheme.colorScheme.outlineVariant) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionLabel(text = stringResource(R.string.group_photo))

            // Photo area — only clickable for admins. The box layout is
            // identical for members so the screen doesn't reflow based on
            // role.
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .border(
                        width = 1.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(24.dp),
                    )
                    .then(
                        if (canEdit) Modifier.clickable { onClick() }
                        else Modifier
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (photoUrl != null) {
                    AsyncImage(
                        model = photoUrl,
                        contentDescription = stringResource(R.string.group_photo),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(24.dp)),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.AddAPhoto,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(48.dp),
                    )
                }
                if (isUploading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        strokeWidth = 3.dp,
                    )
                }
            }

            // Helper copy: admins see the "click to change" hint; members
            // see a notice that only admins can update the photo.
            Text(
                text = stringResource(
                    if (canEdit) R.string.click_to_change_photo
                    else R.string.group_photo_admin_only,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 2 — Group Information Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun GroupInfoCard(
    groupName: String,
    groupDescription: String,
    canEdit: Boolean,
    onEditClick: () -> Unit,
) {
    SettingsCard(borderColor = MaterialTheme.colorScheme.outlineVariant) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 24.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // Only reserve a gutter for the edit icon when an admin
                    // will actually see it; non-admins would otherwise have a
                    // dangling empty column.
                    .padding(end = if (canEdit) 36.dp else 0.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SectionLabel(text = stringResource(R.string.group_information))

                Text(
                    text = groupName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Text(
                    text = groupDescription,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Edit icon — top-end corner. Admin-only.
            if (canEdit) {
                IconButton(
                    onClick = onEditClick,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(30.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = stringResource(R.string.edit_group_info),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 3 — Invite Members Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun InviteMembersCard(
    emailValue: String,
    onEmailChange: (String) -> Unit,
    onInviteClick: () -> Unit,
    isLoading: Boolean,
) {
    val canInvite = emailValue.isNotBlank()

    SettingsCard(borderColor = MaterialTheme.colorScheme.outlineVariant) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionLabel(text = stringResource(R.string.invite_members))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = emailValue,
                    onValueChange = onEmailChange,
                    placeholder = {
                        Text(
                            text = stringResource(R.string.enter_email_address),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        cursorColor = MaterialTheme.colorScheme.primary,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    ),
                )

                // Invite button — disabled until email is typed
                Button(
                    onClick = onInviteClick,
                    enabled = canInvite && !isLoading,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                        disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                    modifier = Modifier.heightIn(min = 56.dp),
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.PersonAdd,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.invite),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 4 — Members Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MembersCard(
    members: List<GroupMember>,
    isCurrentUserAdmin: Boolean,
    onRemoveMember: (String) -> Unit,
    onAvatarClick: (GroupMember) -> Unit,
) {
    SettingsCard(borderColor = MaterialTheme.colorScheme.outlineVariant) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionLabel(text = stringResource(R.string.members_count_label, members.size))

            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                members.forEach { member ->
                    MemberRow(
                        member = member,
                        showRemoveButton = isCurrentUserAdmin && !member.isCurrentUser,
                        onRemoveClick = { onRemoveMember(member.id) },
                        onAvatarClick = onAvatarClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun MemberRow(
    member: GroupMember,
    showRemoveButton: Boolean,
    onRemoveClick: () -> Unit,
    onAvatarClick: (GroupMember) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 70.dp)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(
            imageUrl = member.avatarUrl,
            displayName = member.displayName,
            size = 40.dp,
            contentDescription = member.displayName,
            onClicked = { onAvatarClick(member) },
        )

        Spacer(Modifier.width(16.dp))

        // Name + email
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = member.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (member.isCurrentUser) FontWeight.Medium else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (member.isAdmin) {
                    AdminChip()
                }
            }
            Text(
                text = member.email,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Remove button (only for non-admin rows when current user is admin)
        if (showRemoveButton) {
            IconButton(
                onClick = onRemoveClick,
                modifier = Modifier.size(30.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.remove_member, member.displayName),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun AdminChip() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text = stringResource(R.string.admin),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 5 — Danger Zone Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DangerZoneCard(
    canLeaveGroup: Boolean,
    showDeleteGroup: Boolean,
    isLoading: Boolean,
    onLeaveGroup: () -> Unit,
    onDeleteGroup: () -> Unit,
) {
    SettingsCard(borderColor = MaterialTheme.colorScheme.errorContainer) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // "DANGER ZONE" label in red
            Text(
                text = stringResource(R.string.danger_zone),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
            )

            // Leave Group
            OutlinedButton(
                onClick = onLeaveGroup,
                enabled = canLeaveGroup && !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.error),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Logout,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.leave_group),
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            if (!canLeaveGroup) {
                Text(
                    text = stringResource(R.string.leave_group_blocked_balance),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Delete Group
            if (showDeleteGroup) {
                OutlinedButton(
                    onClick = onDeleteGroup,
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.error),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.delete_group),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared helpers
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SettingsCard(
    borderColor: Color,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(0.5.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        content()
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Preview
// ─────────────────────────────────────────────────────────────────────────────

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun GroupSettingsScreenPreview() {
    EquiShareTheme {
        GroupSettingsScreen(
            uiState = GroupSettingsUiState(
                groupName = "Tokyo Trip",
                groupDescription = "The perfect vacation",
                groupPhotoUrl = null,           // swap for a real URL to see photo
                isCurrentUserAdmin = true,
                members = listOf(
                    GroupMember(
                        id = "0",
                        displayName = "You",
                        email = "mario.rossi@mail.com",
                        isAdmin = true,
                        isCurrentUser = true,
                    ),
                    GroupMember(
                        id = "1",
                        displayName = "Marco Ferrari",
                        email = "marco.f@mail.com",
                    ),
                    GroupMember(
                        id = "2",
                        displayName = "Sofia Romano",
                        email = "sofia.r@mail.com",
                    ),
                    GroupMember(
                        id = "3",
                        displayName = "Laura Bianchi",
                        email = "laura.b@mail.com",
                    ),
                    GroupMember(
                        id = "4",
                        displayName = "Giovanni Verdi",
                        email = "g.verdi@mail.com",
                    ),
                ),
            ),
            onEvent = {},
        )
    }
}
