/** Defines navigation targets opened from notifications. */
package it.unibo.equishare.ui.notifications

import android.content.Intent
import it.unibo.equishare.domain.model.ActivityKind

sealed interface NotificationNavigationTarget {
    data object ActivityCenter : NotificationNavigationTarget
    data class GroupDetail(val groupId: String) : NotificationNavigationTarget
    data class ExpenseInfo(val expenseId: String) : NotificationNavigationTarget

    fun addToIntent(intent: Intent) {
        intent.putExtra(EXTRA_FROM_NOTIFICATION, true)
        when (this) {
            ActivityCenter -> {
                intent.putExtra(EXTRA_DESTINATION, DESTINATION_ACTIVITY)
            }
            is GroupDetail -> {
                intent.putExtra(EXTRA_DESTINATION, DESTINATION_GROUP)
                intent.putExtra(EXTRA_GROUP_ID, groupId)
            }
            is ExpenseInfo -> {
                intent.putExtra(EXTRA_DESTINATION, DESTINATION_EXPENSE)
                intent.putExtra(EXTRA_EXPENSE_ID, expenseId)
            }
        }
    }

    companion object {
        private const val EXTRA_FROM_NOTIFICATION = "it.unibo.equishare.extra.FROM_NOTIFICATION"
        private const val EXTRA_DESTINATION = "it.unibo.equishare.extra.NOTIFICATION_DESTINATION"
        private const val EXTRA_GROUP_ID = "it.unibo.equishare.extra.NOTIFICATION_GROUP_ID"
        private const val EXTRA_EXPENSE_ID = "it.unibo.equishare.extra.NOTIFICATION_EXPENSE_ID"

        private const val DESTINATION_ACTIVITY = "activity"
        private const val DESTINATION_GROUP = "group"
        private const val DESTINATION_EXPENSE = "expense"

        fun fromIntent(intent: Intent?): NotificationNavigationTarget? {
            if (intent?.getBooleanExtra(EXTRA_FROM_NOTIFICATION, false) != true) return null
            return when (intent.getStringExtra(EXTRA_DESTINATION)) {
                DESTINATION_EXPENSE ->
                    intent.getStringExtra(EXTRA_EXPENSE_ID)
                        ?.takeIf(String::isNotBlank)
                        ?.let(::ExpenseInfo)
                        ?: ActivityCenter
                DESTINATION_GROUP ->
                    intent.getStringExtra(EXTRA_GROUP_ID)
                        ?.takeIf(String::isNotBlank)
                        ?.let(::GroupDetail)
                        ?: ActivityCenter
                DESTINATION_ACTIVITY -> ActivityCenter
                else -> ActivityCenter
            }
        }

        fun forActivity(
            type: String,
            groupId: String?,
            expenseId: String?,
            isTargetCurrentUser: Boolean = false,
        ): NotificationNavigationTarget =
            forActivity(ActivityKind.fromDb(type), groupId, expenseId, isTargetCurrentUser)

        fun forActivity(
            kind: ActivityKind,
            groupId: String?,
            expenseId: String?,
            isTargetCurrentUser: Boolean = false,
        ): NotificationNavigationTarget = when (kind) {
            ActivityKind.ExpenseCreated,
            ActivityKind.ExpenseUpdated,
            ActivityKind.ExpenseDeleted,
            ActivityKind.CommentAdded ->
                expenseId.asTargetId()?.let(::ExpenseInfo) ?: groupOrActivity(groupId)

            ActivityKind.PaymentCreated,
            ActivityKind.PaymentDeleted,
            ActivityKind.DebtSettled,
            ActivityKind.MemberAdded,
            ActivityKind.AdminPromoted,
            ActivityKind.GroupUpdated,
            ActivityKind.SettledUp,
            is ActivityKind.Unknown ->
                groupOrActivity(groupId)

            ActivityKind.MemberRemoved ->
                if (isTargetCurrentUser) ActivityCenter else groupOrActivity(groupId)

            ActivityKind.MemberInvited,
            ActivityKind.GroupDeleted ->
                ActivityCenter
        }

        private fun groupOrActivity(groupId: String?): NotificationNavigationTarget =
            groupId.asTargetId()?.let(::GroupDetail) ?: ActivityCenter

        private fun String?.asTargetId(): String? = this?.takeIf(String::isNotBlank)
    }
}
