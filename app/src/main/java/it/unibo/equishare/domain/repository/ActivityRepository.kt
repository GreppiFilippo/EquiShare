/** Defines the Activity repository contract. */
package it.unibo.equishare.domain.repository

import it.unibo.equishare.domain.model.ActivityEntry
import it.unibo.equishare.domain.model.InviteResponseResult
import java.time.OffsetDateTime
import kotlinx.coroutines.flow.Flow

interface ActivityRepository {
    val activities: Flow<List<ActivityEntry>>
    val unreadCount: Flow<Int>

    suspend fun acceptInvite(activityId: String): InviteResponseResult
    suspend fun declineInvite(activityId: String): InviteResponseResult

    suspend fun markAllAsSeen()
    suspend fun markSeenThrough(timestamp: OffsetDateTime)
    fun refresh()
}
