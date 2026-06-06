/** Renders the groups list screen UI. */
package it.unibo.equishare.ui.screens.groups.list

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import it.unibo.equishare.R
import it.unibo.equishare.ui.components.animations.EquiMotion
import it.unibo.equishare.ui.components.animations.animateListItemEntry
import it.unibo.equishare.ui.components.buttons.EquiShareFab
import it.unibo.equishare.ui.components.refresh.EquiSharePullToRefresh
import it.unibo.equishare.ui.theme.EquiShareTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsContent(
    modifier: Modifier = Modifier,
    uiState: GroupsUiState,
    onEvent: (GroupsEvent) -> Unit,
    onRefresh: () -> Unit = {}
) {
    // NOTE: the bottom navigation bar is owned by the parent NavHost wrapper
    // (see EquiShareNavGraph) so it stays fixed while tab content fades, and
    // no `bottomBar` is declared here on purpose.
    val listState = rememberLazyListState()
    val expandedFab by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            EquiShareFab(
                onClick = { onEvent(GroupsEvent.CreateGroupClicked) },
                expanded = expandedFab,
                contentDescription = stringResource(R.string.create_group),
                icon = {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                    )
                },
                text = { Text(stringResource(R.string.create_group)) },
            )
        },
    ) { innerPadding ->
        Crossfade(
            targetState = uiState.isLoading,
            animationSpec = tween(durationMillis = 280, easing = EquiMotion.EmphasizedStandard),
            label = "groupsLoadingCrossfade",
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) { loading ->
            if (loading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else {
                EquiSharePullToRefresh(
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = onRefresh,
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            top = 8.dp,
                            end = 16.dp,
                            bottom = 16.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        // ── Titolo pagina ──────────────────────────────────
                        item {
                            Text(
                                text = stringResource(R.string.your_groups),
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .padding(bottom = 8.dp)
                                    .animateListItemEntry(index = 0),
                            )
                        }

                        if (uiState.groups.isEmpty()) {
                            item {
                                EmptyGroupsState(modifier = Modifier.animateListItemEntry(index = 1))
                            }
                        } else {
                            // ── Sezione Preferiti ──────────────────────────
                            if (uiState.favoriteGroups.isNotEmpty()) {
                                item {
                                    SectionLabel(
                                        text = stringResource(R.string.favorites),
                                        icon = {
                                            Icon(
                                                imageVector = Icons.Filled.Star,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        },
                                        modifier = Modifier.animateListItemEntry(index = 1),
                                    )
                                }
                                item {
                                    GroupsCard(
                                        groups = uiState.favoriteGroups,
                                        onEvent = onEvent,
                                        onReorder = { reorderedFavorites ->
                                            onEvent(
                                                GroupsEvent.ReorderGroups(
                                                    (reorderedFavorites + uiState.otherGroups).map { it.id }
                                                )
                                            )
                                        },
                                        startIndex = 2,
                                    )
                                }
                            }

                            // ── Sezione Tutti i gruppi ─────────────────────
                            // Mostra l'etichetta "All Groups" solo se ci sono anche preferiti
                            if (uiState.favoriteGroups.isNotEmpty() && uiState.otherGroups.isNotEmpty()) {
                                item {
                                    SectionLabel(
                                        text = stringResource(R.string.all_groups),
                                        modifier = Modifier.animateListItemEntry(
                                            index = 2 + uiState.favoriteGroups.size,
                                        ),
                                    )
                                }
                            }

                            if (uiState.otherGroups.isNotEmpty()) {
                                item {
                                    GroupsCard(
                                        groups = uiState.otherGroups,
                                        onEvent = onEvent,
                                        onReorder = { reorderedOtherGroups ->
                                            onEvent(
                                                GroupsEvent.ReorderGroups(
                                                    (uiState.favoriteGroups + reorderedOtherGroups).map { it.id }
                                                )
                                            )
                                        },
                                        startIndex = 3 + uiState.favoriteGroups.size,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Componenti interni
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        icon?.invoke()
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GroupsCard(
    groups: List<GroupItem>,
    onEvent: (GroupsEvent) -> Unit,
    onReorder: (List<GroupItem>) -> Unit,
    startIndex: Int,
    modifier: Modifier = Modifier,
) {
    val orderedGroups = remember { groups.toMutableStateList() }
    val itemHeights = remember { mutableStateMapOf<String, Int>() }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var dragStartOrder by remember { mutableStateOf<List<String>>(emptyList()) }
    var pendingOrderIds by remember { mutableStateOf<List<String>?>(null) }
    val density = LocalDensity.current
    val dragShadow = with(density) { 6.dp.toPx() }
    val canReorder = orderedGroups.size > 1

    LaunchedEffect(groups, draggingId, pendingOrderIds) {
        if (draggingId != null) return@LaunchedEffect

        val currentIds = orderedGroups.map { it.id }
        val newIds = groups.map { it.id }
        val sameSet = currentIds.toSet() == newIds.toSet()
        if (!sameSet) {
            pendingOrderIds = null
            orderedGroups.clear()
            orderedGroups.addAll(groups)
            return@LaunchedEffect
        }
        if (pendingOrderIds != null) {
            if (newIds == pendingOrderIds) {
                pendingOrderIds = null
            } else {
                return@LaunchedEffect
            }
        }
        if (currentIds != newIds) {
            orderedGroups.clear()
            orderedGroups.addAll(groups)
        }
    }

    fun endDrag() {
        val newOrder = orderedGroups.map { it.id }
        if (dragStartOrder.isNotEmpty() && newOrder != dragStartOrder) {
            pendingOrderIds = newOrder
            onReorder(orderedGroups.toList())
        }
        draggingId = null
        draggingIndex = null
        dragOffset = 0f
        dragStartOrder = emptyList()
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column {
            orderedGroups.forEachIndexed { index, group ->
                key(group.id) {
                    val isDragging = group.id == draggingId
                    val entryIndex = remember(group.id) { startIndex + index }
                    val rowDragModifier = if (canReorder) {
                        Modifier.pointerInput(group.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    val startIndex = orderedGroups.indexOfFirst { it.id == group.id }
                                    if (startIndex == -1) return@detectDragGesturesAfterLongPress
                                    draggingId = group.id
                                    draggingIndex = startIndex
                                    dragOffset = 0f
                                    dragStartOrder = orderedGroups.map { it.id }
                                },
                                onDragEnd = { endDrag() },
                                onDragCancel = { endDrag() },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val currentIndex = draggingIndex ?: return@detectDragGesturesAfterLongPress
                                    dragOffset += dragAmount.y
                                    if (dragOffset > 0f && currentIndex < orderedGroups.lastIndex) {
                                        val nextIndex = currentIndex + 1
                                        val nextId = orderedGroups[nextIndex].id
                                        val nextHeight = itemHeights[nextId]?.toFloat() ?: return@detectDragGesturesAfterLongPress
                                        if (dragOffset > nextHeight / 2f) {
                                            orderedGroups.move(currentIndex, nextIndex)
                                            draggingIndex = nextIndex
                                            dragOffset -= nextHeight
                                        }
                                    } else if (dragOffset < 0f && currentIndex > 0) {
                                        val previousIndex = currentIndex - 1
                                        val previousId = orderedGroups[previousIndex].id
                                        val previousHeight = itemHeights[previousId]?.toFloat() ?: return@detectDragGesturesAfterLongPress
                                        if (-dragOffset > previousHeight / 2f) {
                                            orderedGroups.move(currentIndex, previousIndex)
                                            draggingIndex = previousIndex
                                            dragOffset += previousHeight
                                        }
                                    }
                                },
                            )
                        }
                    } else {
                        Modifier
                    }

                    GroupRow(
                        group = group,
                        onClick = { onEvent(GroupsEvent.GroupClicked(group.id)) },
                        onToggleFavorite = { onEvent(GroupsEvent.ToggleFavorite(group.id)) },
                        isDragging = isDragging,
                        modifier = rowDragModifier
                            .animateListItemEntry(index = entryIndex)
                            .onSizeChanged { itemHeights[group.id] = it.height }
                            .graphicsLayer {
                                translationY = if (isDragging) dragOffset else 0f
                                shadowElevation = if (isDragging) dragShadow else 0f
                            }
                            .zIndex(if (isDragging) 1f else 0f),
                    )
                    if (index < orderedGroups.lastIndex) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant,
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyGroupsState(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 360.dp)
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.CardGiftcard,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(34.dp),
            )
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = stringResource(R.string.groups_empty_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.groups_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

    }
}

@Composable
private fun GroupRow(
    group: GroupItem,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
    isDragging: Boolean = false,
) {
    val rowBg = when {
        isDragging -> MaterialTheme.colorScheme.surfaceContainerHigh
        group.isHighlighted -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.12f)
        else -> Color.Transparent
    }
    val avatarBg = if (group.isHighlighted) {
        MaterialTheme.colorScheme.tertiaryContainer
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }

    val balanceLabel = when (group.balanceStatus) {
        BalanceStatus.OWED    -> stringResource(R.string.you_are_owed)
        BalanceStatus.OWES    -> stringResource(R.string.you_owe)
        BalanceStatus.SETTLED -> stringResource(R.string.settled_up)
    }
    val amountColor = when (group.balanceStatus) {
        BalanceStatus.OWED    -> MaterialTheme.colorScheme.primary
        BalanceStatus.OWES    -> MaterialTheme.colorScheme.error
        BalanceStatus.SETTLED -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(rowBg)
            .clickable { onClick() }
            .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Avatar circle
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(avatarBg),
            contentAlignment = Alignment.Center,
        ) {
            if (group.emoji != null) {
                Text(text = group.emoji, style = MaterialTheme.typography.titleMedium)
            } else if (group.icon != null) {
                Icon(
                    imageVector = group.icon,
                    contentDescription = null,
                    tint = if (group.isHighlighted) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(Modifier.width(16.dp))

        // Nome + conteggio membri
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = group.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.member_count, group.memberCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Saldo
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = balanceLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = group.balanceAmount,
                style = MaterialTheme.typography.bodyLarge,
                color = amountColor,
            )
        }

        Spacer(Modifier.width(4.dp))

        // Icona stella preferiti
        IconButton(
            onClick = onToggleFavorite,
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                imageVector = if (group.isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                contentDescription = stringResource(
                    if (group.isFavorite) R.string.remove_from_favorites
                    else R.string.add_to_favorites
                ),
                tint = if (group.isFavorite) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private fun <T> MutableList<T>.move(fromIndex: Int, toIndex: Int) {
    if (fromIndex == toIndex) return
    add(toIndex, removeAt(fromIndex))
}
