/** Defines the Invite Result domain model. */
package it.unibo.equishare.domain.model

/**
 * Outcome of inviting a user to a group via email. Modelled as a sealed
 * hierarchy so the UI can render the right Snackbar copy without parsing
 * strings from server responses.
 */
sealed interface InviteResult {
    data class Success(val userId: String, val displayName: String) : InviteResult
    /** A pending invitation for this email already exists for this group. */
    data class AlreadyInvited(val userId: String) : InviteResult
    data object AlreadyMember : InviteResult
    data object NotFound      : InviteResult
    data object Forbidden     : InviteResult
    data object Self          : InviteResult
    data class Error(val cause: Throwable) : InviteResult
}

/** Outcome of accepting / declining an invitation activity. */
sealed interface InviteResponseResult {
    data class Accepted(val groupId: String) : InviteResponseResult
    data class Declined(val groupId: String) : InviteResponseResult
    /** Activity row no longer maps to a pending invitation. */
    data object NotFound  : InviteResponseResult
    data object Forbidden : InviteResponseResult
    data class Error(val cause: Throwable) : InviteResponseResult
}

/** Outcome of `signUp` — distinguishes auto-login from email confirmation. */
sealed interface SignUpOutcome {
    data object SignedIn : SignUpOutcome
    data object EmailConfirmationSent : SignUpOutcome
}
