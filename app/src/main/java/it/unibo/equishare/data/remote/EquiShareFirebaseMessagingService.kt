/** Handles incoming Firebase push notifications. */
package it.unibo.equishare.data.remote

import android.content.Context
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import it.unibo.equishare.R
import it.unibo.equishare.domain.repository.AuthRepository
import it.unibo.equishare.domain.repository.DeviceTokenRepository
import it.unibo.equishare.domain.repository.ProfileRepository
import it.unibo.equishare.ui.notifications.NotificationCategory
import it.unibo.equishare.ui.notifications.NotificationManager
import it.unibo.equishare.ui.notifications.NotificationNavigationTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class EquiShareFirebaseMessagingService : FirebaseMessagingService() {

    private val notificationManager: NotificationManager by inject()
    private val deviceTokenRepository: DeviceTokenRepository by inject()
    private val profileRepository: ProfileRepository by inject()
    private val auth: AuthRepository by inject()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Received new FCM token: ${token.take(16)}…")
        scope.launch {
            try {
                deviceTokenRepository.registerToken(token)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register FCM token", e)
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        scope.launch {
            // Respect the in-app notifications switch.
            val enabled = profileRepository.notificationsEnabled.firstOrNull() ?: true
            if (!enabled) return@launch

            val data = message.data
            val type = data["type"].orEmpty()
            val activityId = data["activity_id"].orEmpty()
            val recipientUserId = data["recipient_user_id"].orEmpty()
            val currentUserId = auth.currentUserId
            // Only drop the payload if we KNOW who the current user is and the
            // recipient is explicitly someone else.  When currentUserId is null
            // (Supabase session still loading when FCM woke this service in
            // background) we allow it through — the Edge Function already targeted
            // this device's token, so the message is for this user.
            if (recipientUserId.isNotBlank() && currentUserId != null && recipientUserId != currentUserId) {
                Log.d(TAG, "Ignoring FCM payload for another user: ${recipientUserId.take(8)}")
                return@launch
            }
            val category = categoryFor(type)
            val isAddressedToMe = isCurrentUser(data["recipient_user_id"])
            val isTargetCurrentUser = isCurrentUser(data["target_user_id"])
            val navigationTarget = NotificationNavigationTarget.forActivity(
                type = type,
                groupId = data["group_id"],
                expenseId = data["expense_id"],
                isTargetCurrentUser = isTargetCurrentUser,
            )

            val (title, body) = renderLocalized(
                context = this@EquiShareFirebaseMessagingService,
                data = data,
                isAddressedToMe = isAddressedToMe,
                isTargetCurrentUser = isTargetCurrentUser,
            )

            // Use the activity id as notification id so the same event delivered
            // twice (Realtime + FCM) doesn't stack into two cards.
            val notificationId = activityId.ifEmpty { (title + body) }.hashCode()

            notificationManager.showActivityNotification(
                category = category,
                title = title,
                text = body,
                notificationId = notificationId,
                navigationTarget = navigationTarget,
            )
        }
    }

    private fun isCurrentUser(userId: String?): Boolean {
        if (userId.isNullOrEmpty()) return false
        return auth.currentUserId == userId
    }

    private fun categoryFor(type: String): NotificationCategory = when (type) {
        "member_invited" -> NotificationCategory.INVITES
        "expense_created", "expense_updated", "expense_deleted" -> NotificationCategory.EXPENSES
        "payment_created", "payment_deleted", "debt_settled" -> NotificationCategory.PAYMENTS
        "member_added", "member_removed", "admin_promoted", "group_deleted", "group_updated", "settle_up" -> NotificationCategory.GROUPS
        "comment_added" -> NotificationCategory.COMMENTS
        else -> NotificationCategory.GENERAL
    }

    private fun renderLocalized(
        context: Context,
        data: Map<String, String>,
        isAddressedToMe: Boolean,
        isTargetCurrentUser: Boolean,
    ): Pair<String, String> {
        val type = data["type"].orEmpty()
        val group = data["group_name"].orEmpty()
            .ifBlank { context.getString(R.string.notification_group_fallback) }
        val actor = data["actor_name"].orEmpty()
            .ifBlank { context.getString(R.string.notification_someone) }
        val target = data["target_name"].orEmpty()
        val removalReason = data["removal_reason"].orEmpty()
        val expenseTitle = data["expense_title"].orEmpty()
        val formattedAmount = formatAmount(data["amount"].orEmpty(), data["currency"].orEmpty())

        return when (type) {
            "member_invited" -> context.getString(R.string.notification_invite_title) to
                context.getString(R.string.notification_invite_text, actor, group)

            "expense_created" -> context.getString(R.string.notification_title_expense_created, group) to
                context.getString(R.string.notification_text_expense_created, actor)
            "expense_updated" -> context.getString(R.string.notification_title_expense_updated, group) to
                if (expenseTitle.isBlank()) {
                    context.getString(R.string.notification_text_expense_updated, actor)
                } else {
                    context.getString(R.string.notification_text_expense_updated_named, actor, expenseTitle)
                }
            "expense_deleted" -> context.getString(R.string.notification_title_expense_deleted, group) to
                context.getString(R.string.notification_text_expense_deleted, actor)

            "payment_created" -> {
                val title = context.getString(R.string.notification_title_payment_created, group)
                val body = if (isAddressedToMe) {
                    context.getString(R.string.notification_text_payment_received, actor)
                } else {
                    context.getString(R.string.notification_text_payment_created, actor)
                }
                title to body
            }
            "payment_deleted" -> context.getString(R.string.notification_title_payment_deleted, group) to
                context.getString(R.string.notification_text_payment_deleted, actor)

            "debt_settled" -> context.getString(R.string.notification_title_debt_settled, group) to
                context.getString(R.string.notification_text_debt_settled, actor, formattedAmount)

            "member_added" -> context.getString(R.string.notification_title_member_added, group) to
                context.getString(R.string.notification_text_member_added, target.ifBlank { actor })
            "member_removed" -> when {
                removalReason == "removed" && isTargetCurrentUser ->
                    context.getString(R.string.notification_title_removed_from_group, group) to
                        context.getString(R.string.notification_text_removed_from_group, actor)
                removalReason == "removed" && target.isNotBlank() ->
                    context.getString(R.string.notification_title_member_removed_by_admin, group) to
                        context.getString(R.string.notification_text_member_removed_by_admin, actor, target)
                else ->
                    context.getString(R.string.notification_title_member_removed, group) to
                        context.getString(R.string.notification_text_member_removed, target.ifBlank { actor })
            }
            "admin_promoted" -> context.getString(R.string.notification_title_admin_promoted, group) to
                context.getString(R.string.notification_text_admin_promoted)

            "group_updated" -> context.getString(R.string.notification_title_group_updated, group) to
                context.getString(R.string.notification_text_group_updated, actor)
            "group_deleted" -> context.getString(R.string.notification_title_group_deleted, group) to
                context.getString(R.string.notification_text_group_deleted, actor)
            "settle_up" -> context.getString(R.string.notification_title_settle_up, group) to
                context.getString(R.string.notification_text_settle_up)
            "comment_added" -> context.getString(R.string.notification_title_comment_added, group) to
                context.getString(R.string.notification_text_comment_added, actor)

            else -> context.getString(R.string.notification_default_title) to
                context.getString(R.string.notification_default_text)
        }
    }

    private fun formatAmount(amount: String, currency: String): String {
        val value = amount.toDoubleOrNull() ?: return amount.ifBlank { "" }
        val formatted = "%.2f".format(value)
        if (currency.isBlank()) return formatted
        val symbol = runCatching {
            java.util.Currency.getInstance(currency.uppercase()).symbol
        }.getOrNull()
        return if (symbol != null && symbol != currency) "$symbol$formatted" else "$formatted $currency"
    }

    private companion object {
        const val TAG = "EquiShareFcm"
    }
}
