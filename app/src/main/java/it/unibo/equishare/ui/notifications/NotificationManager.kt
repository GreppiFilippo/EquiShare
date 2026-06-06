/** Shows system notifications for app events. */
package it.unibo.equishare.ui.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import it.unibo.equishare.MainActivity
import it.unibo.equishare.R

interface NotificationManager {
    fun showActivityNotification(
        category: NotificationCategory,
        title: String,
        text: String,
        notificationId: Int = title.hashCode(),
        navigationTarget: NotificationNavigationTarget = NotificationNavigationTarget.ActivityCenter,
    )
}

enum class NotificationCategory(val channelId: String) {
    INVITES("invites_channel"),
    EXPENSES("expenses_channel"),
    PAYMENTS("payments_channel"),
    GROUPS("groups_channel"),
    COMMENTS("comments_channel"),
    GENERAL("general_channel"),
}

class SystemNotificationManager(private val context: Context) : it.unibo.equishare.ui.notifications.NotificationManager {

    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        // One channel per category. Importance HIGH means heads-up even with app open.
        val channels = listOf(
            NotificationCategory.INVITES to (
                R.string.channel_invites_name to R.string.channel_invites_description
            ),
            NotificationCategory.EXPENSES to (
                R.string.channel_expenses_name to R.string.channel_expenses_description
            ),
            NotificationCategory.PAYMENTS to (
                R.string.channel_payments_name to R.string.channel_payments_description
            ),
            NotificationCategory.GROUPS to (
                R.string.channel_groups_name to R.string.channel_groups_description
            ),
            NotificationCategory.COMMENTS to (
                R.string.channel_comments_name to R.string.channel_comments_description
            ),
            NotificationCategory.GENERAL to (
                R.string.channel_general_name to R.string.channel_general_description
            ),
        )
        channels.forEach { (cat, names) ->
            val (nameRes, descRes) = names
            val channel = NotificationChannel(
                cat.channelId,
                context.getString(nameRes),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = context.getString(descRes) }
            manager.createNotificationChannel(channel)
        }
    }

    override fun showActivityNotification(
        category: NotificationCategory,
        title: String,
        text: String,
        notificationId: Int,
        navigationTarget: NotificationNavigationTarget,
    ) {
        // Tap on the notification → bring the app to the relevant page.
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            navigationTarget.addToIntent(this)
        }
        val pending = PendingIntent.getActivity(
            context,
            notificationId,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, category.channelId)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setAutoCancel(true)
            .setContentIntent(pending)

        manager.notify(notificationId, builder.build())
    }
}
