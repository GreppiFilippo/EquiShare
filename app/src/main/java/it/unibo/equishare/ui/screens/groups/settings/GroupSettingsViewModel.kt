/** Manages state for the groups settings screen. */
package it.unibo.equishare.ui.screens.groups.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.unibo.equishare.domain.model.InviteResult
import it.unibo.equishare.domain.repository.GroupsRepository
import it.unibo.equishare.domain.usecase.InviteMemberUseCase
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface GroupSettingsFeedback {
    data class InviteSent(val displayName: String) : GroupSettingsFeedback
    data class InviteAlreadyInvited(val displayName: String) : GroupSettingsFeedback
    data object InviteAlreadyMember : GroupSettingsFeedback
    data object InviteNotFound      : GroupSettingsFeedback
    data object InviteForbidden     : GroupSettingsFeedback
    data object InviteSelf          : GroupSettingsFeedback
    data object InviteError         : GroupSettingsFeedback
    data object PhotoUpdated        : GroupSettingsFeedback
    data object PhotoError          : GroupSettingsFeedback
    data object LeaveBlockedByBalance       : GroupSettingsFeedback
    data object LeaveSuccessorRequired      : GroupSettingsFeedback
    data object LeaveError                  : GroupSettingsFeedback
    data object RemoveMemberBlockedByBalance : GroupSettingsFeedback
    data object RemoveMemberError           : GroupSettingsFeedback
    data object DeleteForbidden             : GroupSettingsFeedback
    data object DeleteError                 : GroupSettingsFeedback
    data object GroupInfoUpdated            : GroupSettingsFeedback
    data object GroupInfoUpdateError        : GroupSettingsFeedback
}

private data class GroupSettingsTransientState(
    val inviteEmail: String,
    val isInviteLoading: Boolean,
    val isPhotoUploading: Boolean,
    val isDangerActionLoading: Boolean,
    val isClosed: Boolean,
    val isRefreshing: Boolean,
)

private data class GroupSettingsDangerState(
    val isDangerActionLoading: Boolean,
    val isClosed: Boolean,
    val isRefreshing: Boolean,
)

@OptIn(ExperimentalCoroutinesApi::class)
class GroupSettingsViewModel(
    private val repository: GroupsRepository,
    private val inviteMember: InviteMemberUseCase,
) : ViewModel() {

    val groupId = MutableStateFlow<String?>(null)
    private val inviteEmail = MutableStateFlow("")
    private val isInviteLoading = MutableStateFlow(false)
    private val isPhotoUploading = MutableStateFlow(false)
    private val isDangerActionLoading = MutableStateFlow(false)
    // Flipped on successful destructive action; the screen navigates away only after
    // the server confirms the change, preventing the group from reappearing on back.
    private val isClosed = MutableStateFlow(false)
    private val isRefreshing = MutableStateFlow(false)

    private val _feedback = Channel<GroupSettingsFeedback>(capacity = Channel.BUFFERED)
    val feedback: Flow<GroupSettingsFeedback> = _feedback.receiveAsFlow()

    private val dangerState = combine(
        isDangerActionLoading,
        isClosed,
        isRefreshing,
    ) { dangerLoading, closed, refreshing ->
        GroupSettingsDangerState(
            isDangerActionLoading = dangerLoading,
            isClosed = closed,
            isRefreshing = refreshing,
        )
    }

    private val transientState = combine(
        inviteEmail,
        isInviteLoading,
        isPhotoUploading,
        dangerState,
    ) { email, inviteLoading, photoUploading, danger ->
        GroupSettingsTransientState(
            inviteEmail = email,
            isInviteLoading = inviteLoading,
            isPhotoUploading = photoUploading,
            isDangerActionLoading = danger.isDangerActionLoading,
            isClosed = danger.isClosed,
            isRefreshing = danger.isRefreshing,
        )
    }

    val uiState: StateFlow<GroupSettingsUiState> = combine(
        groupId.flatMapLatest { id ->
            if (id == null) MutableStateFlow(null) else repository.getSettingsById(id)
        },
        groupId.flatMapLatest { id ->
            if (id == null) MutableStateFlow(emptyList()) else repository.getGroupMembers(id)
        },
        transientState,
    ) { group, members, transient ->
        val currentUserRole = members.firstOrNull { it.isCurrentUser }?.role
            ?: group?.currentUserRole
        GroupSettingsUiState(
            groupName = group?.name.orEmpty(),
            groupDescription = group?.description.orEmpty(),
            groupPhotoUrl = group?.avatarUrl,
            members = members.map { member ->
                GroupMember(
                    id = member.userId,
                    displayName = member.displayName,
                    email = member.email,
                    avatarUrl = member.avatarUrl,
                    isAdmin = member.role.canManage,
                    isCurrentUser = member.isCurrentUser,
                )
            },
            inviteEmail = transient.inviteEmail,
            isInviteLoading = transient.isInviteLoading,
            isPhotoUploading = transient.isPhotoUploading,
            isDangerActionLoading = transient.isDangerActionLoading,
            isCurrentUserAdmin = currentUserRole?.canManage == true,
            canLeaveGroup = group?.currentUserBalance?.isZero != false,
            isClosed = transient.isClosed,
            isRefreshing = transient.isRefreshing,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = GroupSettingsUiState(),
    )

    fun onEvent(event: GroupSettingsEvent) {
        val gid = groupId.value
        when (event) {
            is GroupSettingsEvent.InviteEmailChanged -> inviteEmail.update { event.value }
            is GroupSettingsEvent.PhotoPicked         -> uploadGroupPhoto(event.bytes, event.mimeType)
            GroupSettingsEvent.InviteClicked         -> inviteCurrent()
            is GroupSettingsEvent.RemoveMemberClicked -> {
                if (gid != null) removeMember(gid, event.memberId)
            }
            GroupSettingsEvent.DeleteGroupClicked -> deleteCurrentGroup()
            is GroupSettingsEvent.LeaveGroupClicked -> leaveCurrentGroup(event.successorMemberId)
            is GroupSettingsEvent.GroupInfoSubmitted -> {
                if (gid != null) submitGroupInfo(gid, event.name, event.description)
            }
            GroupSettingsEvent.ChangePhotoClicked,
            GroupSettingsEvent.EditGroupInfoClicked,
            GroupSettingsEvent.BackClicked -> { /* handled in Navigation.kt */ }
        }
    }

    private fun inviteCurrent() {
        val gid = groupId.value ?: return
        val email = inviteEmail.value

        isInviteLoading.update { true }
        viewModelScope.launch {
            val result = inviteMember(gid, email)
            val displayFallback = email.trim().substringBefore("@").ifBlank { email.trim() }
            _feedback.trySend(result.toFeedback(displayFallback))
            if (result is InviteResult.Success) inviteEmail.update { "" }
            isInviteLoading.update { false }
        }
    }

    private fun uploadGroupPhoto(bytes: ByteArray, mimeType: String) {
        val gid = groupId.value ?: return
        if (isPhotoUploading.value) return
        isPhotoUploading.update { true }
        viewModelScope.launch {
            repository.uploadGroupPhoto(gid, bytes, mimeType)
                .onSuccess { _feedback.trySend(GroupSettingsFeedback.PhotoUpdated) }
                .onFailure { _feedback.trySend(GroupSettingsFeedback.PhotoError) }
            isPhotoUploading.update { false }
        }
    }

    private fun submitGroupInfo(gid: String, name: String, description: String) {
        val cleanName = name.trim()
        val cleanDescription = description.trim()
        if (cleanName.isBlank()) return
        viewModelScope.launch {
            runCatching {
                repository.updateGroup(
                    id = gid,
                    name = cleanName,
                    description = cleanDescription,
                )
            }
                .onSuccess { _feedback.trySend(GroupSettingsFeedback.GroupInfoUpdated) }
                .onFailure { _feedback.trySend(GroupSettingsFeedback.GroupInfoUpdateError) }
        }
    }

    private fun deleteCurrentGroup() {
        val gid = groupId.value ?: return
        if (!uiState.value.isCurrentUserAdmin) {
            _feedback.trySend(GroupSettingsFeedback.DeleteForbidden)
            return
        }
        if (isDangerActionLoading.value) return
        isDangerActionLoading.update { true }
        viewModelScope.launch {
            repository.delete(gid)
                .onSuccess { isClosed.update { true } }
                .onFailure { _feedback.trySend(GroupSettingsFeedback.DeleteError) }
            isDangerActionLoading.update { false }
        }
    }

    private fun leaveCurrentGroup(successorMemberId: String?) {
        val gid = groupId.value ?: return
        val state = uiState.value
        if (!state.canLeaveGroup) {
            _feedback.trySend(GroupSettingsFeedback.LeaveBlockedByBalance)
            return
        }
        if (state.isCurrentUserAdmin && state.adminSuccessorCandidates.size > 1 && successorMemberId == null) {
            _feedback.trySend(GroupSettingsFeedback.LeaveSuccessorRequired)
            return
        }
        if (isDangerActionLoading.value) return
        isDangerActionLoading.update { true }
        viewModelScope.launch {
            repository.leaveGroup(gid, successorMemberId)
                .onSuccess { isClosed.update { true } }
                .onFailure { error ->
                    // Both the client-side require() and the server-side
                    // trigger surface unsettled balances; in either case the
                    // exception carries "settled" in its message so we can
                    // route the right Snackbar.
                    if (error.message?.contains("settled", ignoreCase = true) == true) {
                        _feedback.trySend(GroupSettingsFeedback.LeaveBlockedByBalance)
                    } else {
                        _feedback.trySend(GroupSettingsFeedback.LeaveError)
                    }
                }
            isDangerActionLoading.update { false }
        }
    }

    private fun removeMember(gid: String, memberId: String) {
        viewModelScope.launch {
            repository.removeMember(gid, memberId)
                .onFailure { error ->
                    if (error.message?.contains("settled", ignoreCase = true) == true) {
                        _feedback.trySend(GroupSettingsFeedback.RemoveMemberBlockedByBalance)
                    } else {
                        _feedback.trySend(GroupSettingsFeedback.RemoveMemberError)
                    }
                }
        }
    }

    fun setGroupId(id: String) {
        if (groupId.value != id) {
            groupId.value = id
        }
    }

    fun consumeClosed() {
        isClosed.update { false }
    }

    fun refresh() {
        if (isRefreshing.value) return
        isRefreshing.update { true }
        viewModelScope.launch {
            repository.refresh()
            delay(700)
            isRefreshing.update { false }
        }
    }

    private fun InviteResult.toFeedback(displayFallback: String): GroupSettingsFeedback =
        when (this) {
            is InviteResult.Success         -> GroupSettingsFeedback.InviteSent(displayName)
            is InviteResult.AlreadyInvited  -> GroupSettingsFeedback.InviteAlreadyInvited(displayFallback)
            InviteResult.AlreadyMember      -> GroupSettingsFeedback.InviteAlreadyMember
            InviteResult.NotFound           -> GroupSettingsFeedback.InviteNotFound
            InviteResult.Forbidden          -> GroupSettingsFeedback.InviteForbidden
            InviteResult.Self               -> GroupSettingsFeedback.InviteSelf
            is InviteResult.Error           -> GroupSettingsFeedback.InviteError
        }
}
