/** Manages state for the groups list screen. */
package it.unibo.equishare.ui.screens.groups.list

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.unibo.equishare.data.local.UserPreferencesDataSource
import it.unibo.equishare.domain.model.Group
import it.unibo.equishare.domain.repository.GroupsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class BalanceStatus { OWED, OWES, SETTLED }

data class GroupItem(
    val id: String,
    val name: String,
    val memberCount: Int,
    val emoji: String?,
    val icon: ImageVector?,
    val balanceStatus: BalanceStatus,
    val balanceAmount: String,
    val isHighlighted: Boolean = false,
    val isFavorite: Boolean = false,
)

data class GroupsUiState(
    val userName: String = "",
    val groups: List<GroupItem> = emptyList(),
    val favoriteGroups: List<GroupItem> = emptyList(),
    val otherGroups: List<GroupItem> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
)

sealed interface GroupsEvent {
    data class GroupClicked(val groupId: String) : GroupsEvent
    data class ToggleFavorite(val groupId: String) : GroupsEvent
    data class ReorderGroups(val groupIds: List<String>) : GroupsEvent
    data object CreateGroupClicked : GroupsEvent
}

class GroupsViewModel(
    private val repository: GroupsRepository,
    private val preferences: UserPreferencesDataSource,
    private val appLanguageManager: it.unibo.equishare.data.local.AppLanguageManager,
) : ViewModel() {
    private val isRefreshing = MutableStateFlow(false)

    private val baseInputs = combine(
        repository.groups,
        isRefreshing,
        appLanguageManager.languageTag,
    ) { groups, refreshing, _ ->
        BaseInputs(
            groups = groups,
            refreshing = refreshing,
        )
    }

    val uiState = combine(
        baseInputs,
        preferences.groupOrderIds,
    ) { inputs, groupOrderIds ->
        val items = inputs.groups.map { it.toUi() }
        val orderedItems = orderGroups(items, groupOrderIds)
        GroupsUiState(
            groups = orderedItems,
            favoriteGroups = orderedItems.filter { it.isFavorite },
            otherGroups = orderedItems.filter { !it.isFavorite },
            isRefreshing = inputs.refreshing,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = GroupsUiState(isLoading = true),
    )

    fun onEvent(event: GroupsEvent) {
        when (event) {
            is GroupsEvent.ToggleFavorite -> viewModelScope.launch {
                val current = uiState.value.groups.firstOrNull { it.id == event.groupId }?.isFavorite ?: false
                repository.setFavorite(event.groupId, !current)
            }
            is GroupsEvent.ReorderGroups -> viewModelScope.launch {
                preferences.setGroupOrder(event.groupIds)
            }
            else -> Unit // altri eventi gestiti dal NavController
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

    private fun Group.toUi(): GroupItem = GroupItem(
        id = id,
        name = name,
        memberCount = memberCount,
        emoji = null,
        icon = category.icon,
        balanceStatus = when {
            balance.isPositive -> BalanceStatus.OWED
            balance.isNegative -> BalanceStatus.OWES
            else               -> BalanceStatus.SETTLED
        },
        balanceAmount = balance.abs().formatted(),
        isFavorite = isFavorite,
    )

    private data class BaseInputs(
        val groups: List<Group>,
        val refreshing: Boolean,
    )

    private fun orderGroups(
        items: List<GroupItem>,
        orderIds: List<String>,
    ): List<GroupItem> {
        if (orderIds.isEmpty()) return items
        val byId = items.associateBy { it.id }
        val ordered = orderIds.mapNotNull(byId::get)
        if (ordered.size == items.size) return ordered
        val orderedIds = ordered.asSequence().map { it.id }.toSet()
        val remaining = items.filterNot { it.id in orderedIds }
        return ordered + remaining
    }
}
