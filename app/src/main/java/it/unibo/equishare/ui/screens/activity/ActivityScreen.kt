/** Renders the activity screen UI. */
package it.unibo.equishare.ui.screens.activity

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import it.unibo.equishare.R
import it.unibo.equishare.ui.components.animations.EquiMotion
import it.unibo.equishare.ui.components.animations.animateListItemEntry
import it.unibo.equishare.ui.components.refresh.EquiSharePullToRefresh
import it.unibo.equishare.ui.theme.EquiShareTheme
import java.time.OffsetDateTime

private val PositiveAmountGreen = Color(0xFF006E1C) // "Marco paid you" — inbound
private val NegativeAmountRed   = Color(0xFFBA1A1A) // "You paid Laura"  — outbound

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityContent(
    modifier: Modifier = Modifier,
    uiState: ActivityUiState,
    onEvent: (ActivityEvent) -> Unit,
    onRefresh: () -> Unit = {}
) {
    val latestVisibleActivityAt = uiState.activities.firstOrNull()?.createdAt
    LaunchedEffect(uiState.isLoading, latestVisibleActivityAt) {
        if (!uiState.isLoading && latestVisibleActivityAt != null) {
            onEvent(ActivityEvent.ActivitiesDisplayed(latestVisibleActivityAt))
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { innerPadding ->
        Crossfade(
            targetState = uiState.isLoading,
            animationSpec = tween(durationMillis = 280, easing = EquiMotion.EmphasizedStandard),
            label = "activityLoadingCrossfade",
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) { loading ->
            when {
                loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }

                uiState.sections.isEmpty() -> EquiSharePullToRefresh(
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = onRefresh,
                ) {
                    EmptyState()
                }

                else -> EquiSharePullToRefresh(
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = onRefresh,
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            top = 8.dp,
                            end = 16.dp,
                            bottom = 16.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item {
                            Text(
                                text = stringResource(R.string.recent_activity),
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .padding(bottom = 4.dp)
                                    .animateListItemEntry(index = 0),
                            )
                        }

                        var animationIndex = 1
                        uiState.sections.forEach { section ->
                            val sectionAnimationIndex = animationIndex
                            item(key = "section-${section.key}") {
                                ActivitySectionHeader(
                                    title = section.title,
                                    modifier = Modifier.animateListItemEntry(index = sectionAnimationIndex),
                                )
                            }
                            animationIndex += 1

                            val rowStartAnimationIndex = animationIndex
                            itemsIndexed(
                                section.items,
                                key = { _, item -> item.id },
                            ) { index, item ->
                                ActivityRow(
                                    item = item,
                                    onClick = { onEvent(ActivityEvent.ActivityItemClicked(item.navigationTarget)) },
                                    onAccept = { onEvent(ActivityEvent.InviteAccepted(item.id)) },
                                    onDecline = { onEvent(ActivityEvent.InviteDeclined(item.id)) },
                                    modifier = Modifier.animateListItemEntry(index = rowStartAnimationIndex + index),
                                )
                            }
                            animationIndex += section.items.size
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.NotificationsNone,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(34.dp),
                )
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = stringResource(R.string.activity_empty_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.activity_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ActivitySectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 2.dp),
    )
}

@Composable
private fun ActivityRow(
    item: ActivityItem,
    onClick: () -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Flat white card with a hairline outline — matches the Figma examples.
    // No elevation because the design intentionally reads as flat tiles.
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                // ── Category badge ─────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp),
                    )
                }

                Spacer(Modifier.width(12.dp))

                // ── Text column ────────────────────────────────────────────
                Column(modifier = Modifier.weight(1f)) {
                    if (item.groupName.isNotBlank()) {
                        Text(
                            text = item.groupName,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(2.dp))
                    }
                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = item.timeLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // ── Right rail: amount chip if present ─────────────────────
                if (item.action is ActivityAction.AmountChip) {
                    Spacer(Modifier.width(8.dp))
                    AmountChip(
                        amount = item.action.amount,
                        isOwed = item.action.isOwed,
                    )
                }
            }

            // ── Invite buttons row (only for member_invited) ───────────────
            if (item.action is ActivityAction.InviteButtons) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Button(
                        onClick = onAccept,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.accept),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = onDecline,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, NegativeAmountRed),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = NegativeAmountRed,
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.decline),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AmountChip(
    amount: String,
    isOwed: Boolean,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Text(
            text = amount,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = if (isOwed) PositiveAmountGreen else NegativeAmountRed,
        )
    }
}