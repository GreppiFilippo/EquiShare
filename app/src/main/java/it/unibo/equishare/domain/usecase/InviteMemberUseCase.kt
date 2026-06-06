/** Implements the Invite Member use case. */
package it.unibo.equishare.domain.usecase

import it.unibo.equishare.domain.model.InviteResult
import it.unibo.equishare.domain.repository.GroupsRepository

class InviteMemberUseCase(
    private val groups: GroupsRepository,
) {
    suspend operator fun invoke(groupId: String, rawEmail: String): InviteResult {
        val email = rawEmail.trim()
        if (email.isBlank()) return InviteResult.Error(BlankEmailException())
        return groups.inviteMember(groupId, email)
    }

    class BlankEmailException : IllegalArgumentException("Email must not be blank")
}
