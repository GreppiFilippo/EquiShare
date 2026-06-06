/** Defines the Activity domain model. */
package it.unibo.equishare.domain.model

import java.time.OffsetDateTime

data class ActivityEntry(
    val id: String,
    val kind: ActivityKind,
    val createdAt: OffsetDateTime,
    val groupId: String?,
    val expenseId: String?,
    val paymentId: String?,
    val expenseTitle: String?,
    val groupName: String?,
    val groupCategory: GroupCategory,
    val actorUserId: String?,
    val actorDisplayName: String?,
    val targetUserId: String?,
    val targetDisplayName: String?,
    val amount: Money?,
    val isActorCurrentUser: Boolean,
    val isTargetCurrentUser: Boolean,
)

sealed interface ActivityKind {
    val dbValue: String

    data object ExpenseCreated : ActivityKind { override val dbValue = "expense_created" }
    data object ExpenseUpdated : ActivityKind { override val dbValue = "expense_updated" }
    data object ExpenseDeleted : ActivityKind { override val dbValue = "expense_deleted" }
    data object PaymentCreated : ActivityKind { override val dbValue = "payment_created" }
    data object PaymentDeleted : ActivityKind { override val dbValue = "payment_deleted" }
    data object DebtSettled    : ActivityKind { override val dbValue = "debt_settled" }
    data object MemberAdded    : ActivityKind { override val dbValue = "member_added" }
    data object MemberRemoved  : ActivityKind { override val dbValue = "member_removed" }
    data object MemberInvited  : ActivityKind { override val dbValue = "member_invited" }
    data object AdminPromoted  : ActivityKind { override val dbValue = "admin_promoted" }
    data object GroupDeleted   : ActivityKind { override val dbValue = "group_deleted" }
    data object GroupUpdated   : ActivityKind { override val dbValue = "group_updated" }
    data object SettledUp      : ActivityKind { override val dbValue = "settle_up" }
    data object CommentAdded   : ActivityKind { override val dbValue = "comment_added" }
    data class Unknown(val raw: String) : ActivityKind { override val dbValue: String get() = raw }

    companion object {
        private val known: List<ActivityKind> = listOf(
            ExpenseCreated, ExpenseUpdated, ExpenseDeleted,
            PaymentCreated, PaymentDeleted, DebtSettled,
            MemberAdded, MemberRemoved, MemberInvited, AdminPromoted,
            GroupDeleted, GroupUpdated, SettledUp, CommentAdded,
        )

        fun fromDb(value: String?): ActivityKind {
            val raw = value.orEmpty()
            val normalised = when (raw) {
                "group_settled_up", "settled_up" -> "settle_up"
                else -> raw
            }
            return known.firstOrNull { it.dbValue == normalised } ?: Unknown(raw)
        }
    }
}
