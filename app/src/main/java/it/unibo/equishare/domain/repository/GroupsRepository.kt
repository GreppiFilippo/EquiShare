/** Defines the Groups repository contract. */
package it.unibo.equishare.domain.repository

import it.unibo.equishare.domain.model.AppCategory
import it.unibo.equishare.domain.model.Group
import it.unibo.equishare.domain.model.GroupBalanceSummary
import it.unibo.equishare.domain.model.GroupMember
import it.unibo.equishare.domain.model.GroupSettings
import it.unibo.equishare.domain.model.GroupType
import it.unibo.equishare.domain.model.InviteResult
import kotlinx.coroutines.flow.Flow

interface GroupsRepository {
    val groups: Flow<List<Group>>

    fun getById(id: String): Flow<Group?>
    fun getSettingsById(id: String): Flow<GroupSettings?>
    fun getGroupMembers(groupId: String): Flow<List<GroupMember>>
    fun getCurrentUserGroupBalances(groupId: String): Flow<GroupBalanceSummary>
    suspend fun currentUserCanAccess(groupId: String): Boolean

    suspend fun getGroupCategories(): List<AppCategory>

    suspend fun create(
        name: String,
        description: String?,
        type: GroupType,
        categoryId: String?,
    ): String

    suspend fun updateGroup(
        id: String,
        name: String? = null,
        description: String? = null,
        avatarUrl: String? = null,
    )

    suspend fun uploadGroupPhoto(groupId: String, bytes: ByteArray, mimeType: String): Result<String>

    suspend fun archive(id: String)
    suspend fun delete(id: String): Result<Unit>
    suspend fun leaveGroup(id: String, successorUserId: String? = null): Result<Unit>
    suspend fun removeMember(groupId: String, userId: String): Result<Unit>
    suspend fun inviteMember(groupId: String, email: String): InviteResult

    /** Persists the favorite flag both locally (optimistic) and remotely (best-effort). */
    suspend fun setFavorite(groupId: String, isFavorite: Boolean)

    fun refresh()
}
