/** Manages state for the activity screen. */
package it.unibo.equishare.ui.screens.activity

import android.content.res.Resources
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.unibo.equishare.R
import it.unibo.equishare.domain.model.ActivityEntry
import it.unibo.equishare.domain.model.InviteResponseResult
import it.unibo.equishare.domain.repository.ActivityRepository
import it.unibo.equishare.domain.usecase.RespondToInviteUseCase
import it.unibo.equishare.ui.notifications.NotificationNavigationTarget
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.temporal.WeekFields
import java.util.Locale
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ActivityAction {
    data class AmountChip(val amount: String, val isOwed: Boolean) : ActivityAction
    data class InviteButtons(val activityId: String) : ActivityAction
    data object None : ActivityAction
}

data class ActivityItem(
    val id: String,
    val groupName: String,
    val description: String,
    val timeLabel: String,
    val createdAt: OffsetDateTime,
    val icon: ImageVector,
    val action: ActivityAction,
    val navigationTarget: NotificationNavigationTarget = NotificationNavigationTarget.ActivityCenter,
)

data class ActivitySection(
    val key: String,
    val title: String,
    val items: List<ActivityItem>,
)

data class ActivityUiState(
    val activities: List<ActivityItem> = emptyList(),
    val sections: List<ActivitySection> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
)

sealed interface ActivityEvent {
    data class ActivitiesDisplayed(val latestVisibleActivityAt: OffsetDateTime) : ActivityEvent
    data class ActivityItemClicked(val target: NotificationNavigationTarget) : ActivityEvent
    data class InviteAccepted(val activityId: String) : ActivityEvent
    data class InviteDeclined(val activityId: String) : ActivityEvent
}

class ActivityViewModel(
    private val repository: ActivityRepository,
    private val respondToInvite: RespondToInviteUseCase,
    private val appLanguageManager: it.unibo.equishare.data.local.AppLanguageManager,
) : ViewModel() {

    private val formatter = ActivityFormatter()
    private val zoneId = ZoneId.systemDefault()
    private val isRefreshing = MutableStateFlow(false)
    private val _navigation = Channel<NotificationNavigationTarget>(capacity = Channel.BUFFERED)

    private val weekFields get() = WeekFields.of(Locale.getDefault())

    val navigation: Flow<NotificationNavigationTarget> = _navigation.receiveAsFlow()

    val uiState = combine(
        repository.activities,
        appLanguageManager.languageTag,
        isRefreshing,
    ) { activities, languageTag, refreshing ->
        val localizedResources = appLanguageManager.resources(languageTag)
        val sections = activities.toSections(localizedResources)
        ActivityUiState(
            activities = sections.flatMap(ActivitySection::items),
            sections = sections,
            isRefreshing = refreshing,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ActivityUiState(isLoading = true),
    )

    fun onEvent(event: ActivityEvent) {
        when (event) {
            is ActivityEvent.ActivitiesDisplayed ->
                markVisibleActivitiesAsSeen(event.latestVisibleActivityAt)
            is ActivityEvent.InviteAccepted ->
                viewModelScope.launch {
                    when (val result = respondToInvite(event.activityId, RespondToInviteUseCase.Action.ACCEPT)) {
                        is InviteResponseResult.Accepted ->
                            if (result.groupId.isNotBlank()) {
                                _navigation.send(NotificationNavigationTarget.GroupDetail(result.groupId))
                            }
                        else -> Unit
                    }
                }
            is ActivityEvent.InviteDeclined ->
                viewModelScope.launch {
                    respondToInvite(event.activityId, RespondToInviteUseCase.Action.DECLINE)
                }
            else -> { /* navigation events are handled in Navigation.kt */ }
        }
    }

    private fun markVisibleActivitiesAsSeen(latestVisibleActivityAt: OffsetDateTime) {
        viewModelScope.launch {
            runCatching { repository.markSeenThrough(latestVisibleActivityAt) }
        }
    }

    fun refresh() {
        if (isRefreshing.value) return
        isRefreshing.update { true }
        viewModelScope.launch {
            repository.refresh()
            delay(700)
            isRefreshing.update { false }
        }
    }

    private fun List<ActivityEntry>.toSections(resources: Resources): List<ActivitySection> {
        val buckets = linkedMapOf<ActivitySectionBucket, MutableList<ActivityItem>>()

        forEach { entry ->
            val bucket = entry.toSectionBucket()
            buckets.getOrPut(bucket) { mutableListOf() }.add(formatter.format(entry, resources))
        }

        return buckets.map { (bucket, items) ->
            ActivitySection(
                key = bucket.name,
                title = bucket.title(resources),
                items = items,
            )
        }
    }

    private fun ActivityEntry.toSectionBucket(): ActivitySectionBucket {
        val today = LocalDate.now(zoneId)
        val date = createdAt.atZoneSameInstant(zoneId).toLocalDate()

        return when {
            date == today -> ActivitySectionBucket.TODAY
            date == today.minusDays(1) -> ActivitySectionBucket.YESTERDAY
            date.isSameWeekAs(today) -> ActivitySectionBucket.THIS_WEEK
            date.year == today.year && date.month == today.month -> ActivitySectionBucket.THIS_MONTH
            else -> ActivitySectionBucket.OLDER
        }
    }

    private fun LocalDate.isSameWeekAs(other: LocalDate): Boolean =
        get(weekFields.weekBasedYear()) == other.get(weekFields.weekBasedYear()) &&
            get(weekFields.weekOfWeekBasedYear()) == other.get(weekFields.weekOfWeekBasedYear())
}

private enum class ActivitySectionBucket {
    TODAY,
    YESTERDAY,
    THIS_WEEK,
    THIS_MONTH,
    OLDER;

    fun title(resources: Resources): String = when (this) {
        TODAY -> resources.getString(R.string.activity_section_today)
        YESTERDAY -> resources.getString(R.string.activity_section_yesterday)
        THIS_WEEK -> resources.getString(R.string.activity_section_this_week)
        THIS_MONTH -> resources.getString(R.string.activity_section_this_month)
        OLDER -> resources.getString(R.string.activity_section_older)
    }
}
