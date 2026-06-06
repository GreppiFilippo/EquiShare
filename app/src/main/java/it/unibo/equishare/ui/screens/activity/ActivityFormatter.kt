/** Formats activity entries for display. */
package it.unibo.equishare.ui.screens.activity

import android.content.res.Resources
import it.unibo.equishare.R
import it.unibo.equishare.domain.model.ActivityEntry
import it.unibo.equishare.domain.model.ActivityKind
import it.unibo.equishare.ui.notifications.NotificationNavigationTarget
import java.time.Duration
import java.time.OffsetDateTime

class ActivityFormatter {

    fun format(entry: ActivityEntry, resources: Resources): ActivityItem {
        val description = describe(entry, resources)
        val action = when (entry.kind) {
            is ActivityKind.MemberInvited -> ActivityAction.InviteButtons(activityId = entry.id)
            else -> entry.amount?.let { money ->
                ActivityAction.AmountChip(
                    amount = money.formatted(),
                    isOwed = entry.kind.isInbound(entry.isActorCurrentUser),
                )
            } ?: ActivityAction.None
        }

        return ActivityItem(
            id = entry.id,
            groupName = entry.groupName.orEmpty(),
            description = description,
            timeLabel = relativeTime(entry.createdAt, resources),
            createdAt = entry.createdAt,
            icon = entry.groupCategory.icon,
            action = action,
            navigationTarget = NotificationNavigationTarget.forActivity(
                kind = entry.kind,
                groupId = entry.groupId,
                expenseId = entry.expenseId,
                isTargetCurrentUser = entry.isTargetCurrentUser,
            ),
        )
    }

    private fun describe(entry: ActivityEntry, resources: Resources): String {
        val actor = entry.actorDisplayName ?: resources.getString(R.string.activity_someone)
        val target = entry.targetDisplayName
        val groupName = entry.groupName
            ?.takeIf { it.isNotBlank() }
            ?: resources.getString(R.string.notification_group_fallback)
        val whoDidIt = if (entry.isActorCurrentUser) resources.getString(R.string.you_label) else actor
        return when (val kind = entry.kind) {
            ActivityKind.ExpenseCreated -> resources.getString(R.string.activity_expense_created, whoDidIt)
            ActivityKind.ExpenseUpdated -> entry.expenseTitle?.let { title ->
                resources.getString(R.string.activity_expense_updated_named, whoDidIt, title)
            } ?: resources.getString(R.string.activity_expense_updated, whoDidIt)
            ActivityKind.ExpenseDeleted -> resources.getString(R.string.activity_expense_deleted, whoDidIt)
            ActivityKind.PaymentCreated -> describePaymentCreated(entry, actor, target, resources)
            ActivityKind.PaymentDeleted -> resources.getString(R.string.activity_payment_deleted, whoDidIt)
            ActivityKind.DebtSettled    -> describeDebtSettled(entry, actor, target, resources)
            ActivityKind.MemberAdded    -> describeMemberAdded(entry, actor, target, whoDidIt, resources)
            ActivityKind.MemberRemoved  -> describeMemberRemoved(entry, actor, target, whoDidIt, resources)
            ActivityKind.MemberInvited  -> if (entry.isActorCurrentUser) {
                resources.getString(R.string.activity_you_invited_member, groupName)
            } else {
                resources.getString(R.string.activity_invited_you, actor, groupName)
            }
            ActivityKind.AdminPromoted -> if (entry.isTargetCurrentUser) {
                resources.getString(R.string.activity_you_became_admin)
            } else {
                resources.getString(R.string.activity_member_became_admin, target ?: actor)
            }
            ActivityKind.GroupDeleted -> resources.getString(R.string.activity_group_deleted, actor)
            ActivityKind.GroupUpdated -> resources.getString(R.string.activity_group_updated, whoDidIt)
            ActivityKind.SettledUp    -> resources.getString(R.string.activity_group_settled_up)
            ActivityKind.CommentAdded -> resources.getString(R.string.activity_comment_added, whoDidIt)
            is ActivityKind.Unknown   ->
                kind.raw.replace('_', ' ').replaceFirstChar { it.uppercase() }
        }
    }

    private fun describeDebtSettled(entry: ActivityEntry, actor: String, target: String?, resources: Resources): String = when {
        entry.isActorCurrentUser && target != null ->
            resources.getString(R.string.activity_debt_settled_you_paid_target, target)
        !entry.isActorCurrentUser && entry.isTargetCurrentUser ->
            resources.getString(R.string.activity_debt_settled_actor_paid_you, actor)
        !entry.isActorCurrentUser && target != null ->
            resources.getString(R.string.activity_debt_settled_actor_paid_target, actor, target)
        entry.isActorCurrentUser ->
            resources.getString(R.string.activity_debt_settled_you)
        else ->
            resources.getString(R.string.activity_debt_settled_actor, actor)
    }

    private fun describePaymentCreated(entry: ActivityEntry, actor: String, target: String?, resources: Resources): String = when {
        entry.isActorCurrentUser && target != null ->
            resources.getString(R.string.activity_you_paid_target, target)
        !entry.isActorCurrentUser && entry.isTargetCurrentUser ->
            resources.getString(R.string.activity_actor_paid_you, actor)
        !entry.isActorCurrentUser && target != null ->
            resources.getString(R.string.activity_actor_paid_target, actor, target)
        entry.isActorCurrentUser ->
            resources.getString(R.string.activity_you_made_payment)
        else ->
            resources.getString(R.string.activity_actor_made_payment, actor)
    }

    private fun describeMemberAdded(
        entry: ActivityEntry,
        actor: String,
        target: String?,
        whoDidIt: String,
        resources: Resources,
    ): String = when {
        entry.isActorCurrentUser && target != null ->
            resources.getString(R.string.activity_you_added_member, target)
        !entry.isActorCurrentUser && entry.isTargetCurrentUser ->
            resources.getString(R.string.activity_actor_added_you, actor)
        !entry.isActorCurrentUser && target != null ->
            resources.getString(R.string.activity_actor_added_member, actor, target)
        else -> resources.getString(R.string.activity_joined_group, whoDidIt)
    }

    private fun describeMemberRemoved(
        entry: ActivityEntry,
        actor: String,
        target: String?,
        whoDidIt: String,
        resources: Resources,
    ): String {
        val isAdminRemoval = entry.targetUserId != null && entry.actorUserId != entry.targetUserId
        return when {
            !isAdminRemoval -> resources.getString(R.string.activity_left_group, whoDidIt)
            entry.isTargetCurrentUser -> resources.getString(R.string.activity_actor_removed_you, actor)
            entry.isActorCurrentUser && target != null -> resources.getString(R.string.activity_you_removed_member, target)
            target != null -> resources.getString(R.string.activity_actor_removed_target, actor, target)
            else -> resources.getString(R.string.activity_group_updated, whoDidIt)
        }
    }

    private fun relativeTime(instant: OffsetDateTime, resources: Resources): String {
        val diff = Duration.between(instant, OffsetDateTime.now())
        if (diff.isNegative) return resources.getString(R.string.activity_time_just_now)

        val seconds = diff.seconds
        val minutes = diff.toMinutes()
        val hours   = diff.toHours()
        val days    = diff.toDays()

        return when {
            seconds < 60 -> resources.getString(R.string.activity_time_just_now)
            minutes < 60 -> resources.getQuantityString(R.plurals.activity_time_minutes_ago, minutes.toInt(), minutes.toInt())
            hours < 24   -> resources.getQuantityString(R.plurals.activity_time_hours_ago, hours.toInt(), hours.toInt())
            days < 7     -> resources.getQuantityString(R.plurals.activity_time_days_ago, days.toInt(), days.toInt())
            days < 30    -> {
                val weeks = (days / 7).toInt()
                resources.getQuantityString(R.plurals.activity_time_weeks_ago, weeks, weeks)
            }
            days < 365   -> {
                val months = (days / 30).toInt()
                resources.getQuantityString(R.plurals.activity_time_months_ago, months, months)
            }
            else         -> {
                val years = (days / 365).toInt()
                resources.getQuantityString(R.plurals.activity_time_years_ago, years, years)
            }
        }
    }
}

private fun ActivityKind.isInbound(isActorCurrentUser: Boolean): Boolean = when (this) {
    ActivityKind.PaymentCreated, ActivityKind.PaymentDeleted,
    ActivityKind.DebtSettled -> !isActorCurrentUser
    else -> true
}
