/** Implements the Activity repository using Supabase and local data. */
package it.unibo.equishare.data.repositories

import it.unibo.equishare.data.local.EquiShareLocalDataSource
import it.unibo.equishare.data.local.UserPreferencesDataSource
import it.unibo.equishare.data.remote.datasource.SupabaseActivityDataSource
import it.unibo.equishare.data.remote.mappers.string
import it.unibo.equishare.data.remote.mappers.toDomain
import it.unibo.equishare.domain.model.ActivityEntry
import it.unibo.equishare.domain.model.ActivityKind
import it.unibo.equishare.domain.model.InviteResponseResult
import it.unibo.equishare.domain.repository.ActivityRepository
import it.unibo.equishare.domain.repository.AuthRepository
import it.unibo.equishare.domain.repository.GroupsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.OffsetDateTime

class SupabaseActivityRepository(
    private val remote: SupabaseActivityDataSource,
    private val auth: AuthRepository,
    private val groupsRepository: GroupsRepository,
    private val userPreferences: UserPreferencesDataSource,
    private val local: EquiShareLocalDataSource,
) : RefreshableRepository(), ActivityRepository {

    init { watchAuth(auth.isSignedIn) }

    private val dismissedActivityIds = MutableStateFlow<Set<String>>(emptySet())

    override val activities: Flow<List<ActivityEntry>> = refreshableCacheFirst { _isForced ->
        val currentUid = auth.currentUserId ?: run { emit(emptyList()); return@refreshableCacheFirst }
        val dismissed = dismissedActivityIds.value

        // Emit cached data immediately so the screen is never blank.
        emit(
            local.activities(currentUid)
                .filterNot { it.isHiddenSelfNotification(currentUid) }
                .filterNot { it.kind is ActivityKind.AdminPromoted && !it.isTargetCurrentUser }
                .filterNot { it.id in dismissed }
        )

        // Then fetch fresh data in the background.
        try {
            val logs = remote.fetchActivityLogs()
            val targetIds = logs.mapNotNull { log ->
                (log.metadata as? JsonObject)?.string("target_user_id")
            }
            val groups = remote.fetchGroupContext(logs.mapNotNull { it.groupId }.distinct())
            val actors = remote.fetchProfiles((logs.mapNotNull { it.actorUserId } + targetIds).distinct())

            val entries = logs.asSequence()
                // member_invited is shown only to the invitee, not to the admin who sent it.
                .filterNot { log ->
                    if (ActivityKind.fromDb(log.activityType) !is ActivityKind.MemberInvited) return@filterNot false
                    val target = (log.metadata as? JsonObject)?.string("target_user_id")
                    target != currentUid
                }
                .map { log ->
                    val group = log.groupId?.let(groups::get)
                    val target = (log.metadata as? JsonObject)?.string("target_user_id")
                    val targetProfile = target?.let { actors[it] }
                    log.toDomain(
                        groupName = group?.name,
                        groupIconKey = group?.categoryIconKey,
                        groupType = group?.type,
                        actorDisplayName = log.actorUserId?.let { actors[it]?.displayLabel },
                        targetDisplayName = targetProfile?.displayLabel,
                        targetUserId = target,
                        currentUserId = currentUid,
                    )
                }
                .filterNot { it.isHiddenSelfNotification(currentUid) }
                .filterNot { it.kind is ActivityKind.AdminPromoted && !it.isTargetCurrentUser }
                .toList()
            local.replaceActivities(currentUid, entries)
            emit(entries.filterNot { it.id in dismissed })
        } catch (t: Throwable) {
            t.rethrowIfCancellation()
        }
    }

    override val unreadCount: Flow<Int> = activities.combine(userPreferences.lastSeenActivityAt) { list, lastSeen ->
        if (lastSeen == null) return@combine list.size
        val lastSeenTime = OffsetDateTime.parse(lastSeen)
        list.count { it.createdAt.isAfter(lastSeenTime) }
    }

    override suspend fun markAllAsSeen() {
        val latest = activities.firstOrNull()?.firstOrNull() ?: return
        markSeenThrough(latest.createdAt)
    }

    override suspend fun markSeenThrough(timestamp: OffsetDateTime) {
        userPreferences.setLastSeenActivityAtIfNewer(timestamp.toString())
    }

    override suspend fun acceptInvite(activityId: String): InviteResponseResult =
        respondToInvite(activityId, accept = true)

    override suspend fun declineInvite(activityId: String): InviteResponseResult =
        respondToInvite(activityId, accept = false)

    private suspend fun respondToInvite(
        activityId: String,
        accept: Boolean,
    ): InviteResponseResult = try {
        // Optimistic dismissal: card disappears immediately; reverted on failure.
        dismissedActivityIds.value = dismissedActivityIds.value + activityId
        refresh()

        val payload = remote.respondToInvite(activityId, accept)
        val status  = payload["status"]?.jsonPrimitive?.content
        val groupId = payload["group_id"]?.jsonPrimitive?.content.orEmpty()
        refresh()

        when (status) {
            "ACCEPTED"  -> {
                groupsRepository.refresh()
                InviteResponseResult.Accepted(groupId)
            }
            "DECLINED"  -> InviteResponseResult.Declined(groupId)
            "NOT_FOUND" -> InviteResponseResult.NotFound
            "FORBIDDEN" -> InviteResponseResult.Forbidden
            else        -> InviteResponseResult.Error(IllegalStateException("Unexpected status: $status"))
        }
    } catch (t: Throwable) {
        dismissedActivityIds.value = dismissedActivityIds.value - activityId
        refresh()
        InviteResponseResult.Error(t)
    }

    private fun ActivityEntry.isHiddenSelfNotification(currentUid: String): Boolean =
        actorUserId == currentUid &&
            (
                kind is ActivityKind.ExpenseCreated ||
                    kind is ActivityKind.ExpenseUpdated ||
                    kind is ActivityKind.MemberAdded
            )
}
