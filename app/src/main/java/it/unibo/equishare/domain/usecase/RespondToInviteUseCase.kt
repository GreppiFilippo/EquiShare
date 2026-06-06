/** Implements the Respond To Invite use case. */
package it.unibo.equishare.domain.usecase

import it.unibo.equishare.domain.model.InviteResponseResult
import it.unibo.equishare.domain.repository.ActivityRepository

class RespondToInviteUseCase(
    private val activity: ActivityRepository,
) {
    enum class Action { ACCEPT, DECLINE }

    suspend operator fun invoke(activityId: String, action: Action): InviteResponseResult =
        when (action) {
            Action.ACCEPT  -> activity.acceptInvite(activityId)
            Action.DECLINE -> activity.declineInvite(activityId)
        }
}
