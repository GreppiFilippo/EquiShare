/** Renders the groups details screen UI. */
package it.unibo.equishare.ui.screens.groups.details

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.derivedStateOf
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import it.unibo.equishare.R
import it.unibo.equishare.ui.components.animations.EquiMotion
import it.unibo.equishare.ui.components.snackbar.EquiShareSnackbarHost
import it.unibo.equishare.ui.components.animations.animateListItemEntry
import it.unibo.equishare.ui.components.animations.pressScale
import it.unibo.equishare.ui.components.topbar.EquiShareTopBarMaxStatusInset
import it.unibo.equishare.ui.components.buttons.EquiShareFab
import it.unibo.equishare.ui.components.avatar.Avatar
import it.unibo.equishare.ui.components.image.ImagePreviewDialog
import it.unibo.equishare.ui.components.refresh.EquiSharePullToRefresh
import java.math.BigDecimal
import java.math.RoundingMode

enum class BalanceDirection { OWED_TO_YOU, YOU_OWE }

data class MemberBalance(
    val memberId: String,
    val memberName: String,        // short first name
    val memberAvatarUrl: String? = null,
    val direction: BalanceDirection,
    val amount: String,            // formatted, e.g. "€150.00"
    val amountValue: Double = 0.0,
    val currencyCode: String = "EUR",
)

data class ExpenseItem(
    val id: String,
    val title: String,
    val paidByLabel: String,
    val dateLabel: String,
    val amount: String,            // e.g. "€450.00"
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val isSettlement: Boolean = false,
)

data class ExpenseSection(
    val key: String,
    val title: String,
    val items: List<ExpenseItem>,
)

enum class SettlementFeedback { SUCCESS, ERROR }

data class GroupDetailUiState(
    val groupName: String = "",
    val groupDescription: String = "",
    val groupPhotoUrl: String? = null,
    val balances: List<MemberBalance> = emptyList(),
    val totalGroupSpending: String = "",
    val totalToReceive: String = "",
    val totalYouOwe: String = "",
    val baseCurrencyCode: String = "EUR",
    val expenses: List<ExpenseItem> = emptyList(),
    val expenseSections: List<ExpenseSection> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isRecordingSettlement: Boolean = false,
    val settlementFeedback: SettlementFeedback? = null,
)

sealed interface GroupDetailEvent {
    data object BackClicked : GroupDetailEvent
    data object MoreOptionsClicked : GroupDetailEvent
    data class ExpenseClicked(val expenseId: String) : GroupDetailEvent
    data object AddExpenseClicked : GroupDetailEvent
    data class SettleDebtConfirmed(val memberId: String, val amount: String) : GroupDetailEvent
    data object SettlementFeedbackConsumed : GroupDetailEvent
}
@Composable
fun GroupDetailScreen(
    modifier: Modifier = Modifier,
    uiState: GroupDetailUiState,
    onEvent: (GroupDetailEvent) -> Unit,
    onRefresh: () -> Unit = {},
) {
    val settlementSuccessMessage = stringResource(R.string.settle_debt_success)
    val settlementErrorMessage = stringResource(R.string.settle_debt_error)
    val profilePictureLabel = stringResource(R.string.profile_picture)
    var settlementTarget by remember { mutableStateOf<MemberBalance?>(null) }
    var settlementAmount by rememberSaveable { mutableStateOf("") }
    var previewMemberAvatarUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var previewMemberName by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    rememberCoroutineScope()

    LaunchedEffect(uiState.settlementFeedback) {
        when (uiState.settlementFeedback) {
            SettlementFeedback.SUCCESS -> {
                settlementTarget = null
                settlementAmount = ""
                onEvent(GroupDetailEvent.SettlementFeedbackConsumed)
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(settlementSuccessMessage)
            }

            SettlementFeedback.ERROR -> {
                onEvent(GroupDetailEvent.SettlementFeedbackConsumed)
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(settlementErrorMessage)
            }

            null -> Unit
        }
    }

    val scrollState = rememberScrollState()
    val expandedFab by remember { derivedStateOf { scrollState.value == 0 } }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Crossfade(
            targetState = uiState.isLoading,
            animationSpec = tween(durationMillis = 280, easing = EquiMotion.EmphasizedStandard),
            label = "groupDetailLoadingCrossfade",
            modifier = Modifier.fillMaxSize(),
        ) { loading ->
            if (loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                EquiSharePullToRefresh(
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = onRefresh,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState),
                    ) {

                    // ── Hero header ────────────────────────────────────────────
                    HeroHeader(
                        groupName = uiState.groupName,
                        groupDescription = uiState.groupDescription,
                        photoUrl = uiState.groupPhotoUrl,
                        onBackClick = { onEvent(GroupDetailEvent.BackClicked) },
                        onMoreClick = { onEvent(GroupDetailEvent.MoreOptionsClicked) },
                    )

                    // ── Body ───────────────────────────────────────────────────
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // Counter that increments across all animated items so
                        // they cascade in a single, visually pleasing wave.
                        var staggerIndex = 0

                        Spacer(Modifier.height(12.dp))

                        // ── Your Balances ──────────────────────────────────────
                        SectionLabel(
                            text = stringResource(R.string.your_balances),
                            modifier = Modifier.animateListItemEntry(index = staggerIndex++),
                        )

                        uiState.balances.forEach { balance ->
                            BalanceCard(
                                balance = balance,
                                onClick = {
                                    if (balance.direction == BalanceDirection.YOU_OWE) {
                                        settlementTarget = balance
                                        settlementAmount = ""
                                    }
                                },
                                onAvatarClick = {
                                    balance.memberAvatarUrl?.let { url ->
                                        previewMemberAvatarUrl = url
                                        previewMemberName = balance.memberName
                                    }
                                },
                                modifier = Modifier.animateListItemEntry(index = staggerIndex++),
                            )
                        }

                        Spacer(Modifier.height(4.dp))

                        // ── Spending Summary ───────────────────────────────────
                        SpendingSummaryCard(
                            totalSpending = uiState.totalGroupSpending,
                            totalToReceive = uiState.totalToReceive,
                            totalYouOwe = uiState.totalYouOwe,
                            modifier = Modifier.animateListItemEntry(index = staggerIndex++),
                        )

                        Spacer(Modifier.height(4.dp))

                        // ── Expenses ───────────────────────────────────────────
                        SectionLabel(
                            text = stringResource(R.string.expenses),
                            modifier = Modifier.animateListItemEntry(index = staggerIndex++),
                        )

                        if (uiState.expenseSections.isEmpty()) {
                            EmptyExpensesState(
                                modifier = Modifier.animateListItemEntry(index = staggerIndex++),
                            )
                        } else {
                            uiState.expenseSections.forEach { section ->
                                ExpenseSectionHeader(
                                    title = section.title,
                                    modifier = Modifier.animateListItemEntry(index = staggerIndex++),
                                )

                                section.items.forEach { expense ->
                                    if (expense.isSettlement) {
                                        SettlementRow(
                                            expense = expense,
                                            modifier = Modifier.animateListItemEntry(index = staggerIndex++),
                                        )
                                    } else {
                                        ExpenseRow(
                                            expense = expense,
                                            onClick = { onEvent(GroupDetailEvent.ExpenseClicked(expense.id)) },
                                            modifier = Modifier.animateListItemEntry(index = staggerIndex++),
                                        )
                                    }
                                }
                            }
                        }

                        // Bottom padding so the FAB and the system navigation bar
                        // never cover the last row.
                        Spacer(
                            Modifier
                                .height(72.dp)
                                .navigationBarsPadding(),
                        )
                    }
                    }
                }
            }
        }

        if (!uiState.isLoading) {
            val addExpenseLabel = stringResource(R.string.add_expense)
            EquiShareFab(
                onClick = { onEvent(GroupDetailEvent.AddExpenseClicked) },
                expanded = expandedFab,
                contentDescription = addExpenseLabel,
                icon = {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                    )
                },
                text = { Text(text = addExpenseLabel) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 16.dp, bottom = 24.dp),
            )
        }

        EquiShareSnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 80.dp),
        )

        settlementTarget?.let { target ->
            SettleDebtDialog(
                balance = target,
                amount = settlementAmount,
                isSettling = uiState.isRecordingSettlement,
                onAmountChange = { settlementAmount = it },
                onDismiss = {
                    if (!uiState.isRecordingSettlement) {
                        settlementTarget = null
                        settlementAmount = ""
                    }
                },
                onConfirm = {
                    onEvent(GroupDetailEvent.SettleDebtConfirmed(target.memberId, settlementAmount))
                },
            )
        }
    }

    previewMemberAvatarUrl?.let { url ->
        val previewTitle = previewMemberName ?: profilePictureLabel
        ImagePreviewDialog(
            imageUri = url,
            contentDescription = previewTitle,
            title = previewTitle,
            onDismiss = {
                previewMemberAvatarUrl = null
                previewMemberName = null
            },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Hero Header
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HeroHeader(
    groupName: String,
    groupDescription: String,
    photoUrl: String?,
    onBackClick: () -> Unit,
    onMoreClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val topInset = WindowInsets.statusBars
        .asPaddingValues()
        .calculateTopPadding()
        .coerceAtMost(EquiShareTopBarMaxStatusInset)
    val heroGradientTop = Brush.verticalGradient(
        0f to Color.Black.copy(alpha = 0.30f),
        0.35f to Color.Black.copy(alpha = 0.25f),
        1f to Color.Transparent,
    )
    val heroGradientBottom = Brush.verticalGradient(
        0f to Color.Transparent,
        0.28f to Color.Black.copy(alpha = 0.25f),
        1f to Color.Black.copy(alpha = 0.50f),
    )
    val heroOverlay = Brush.verticalGradient(
        0f to Color.Black.copy(alpha = 0.30f),
        0.2f to Color.Black.copy(alpha = 0.10f),
        1f to Color.Black.copy(alpha = 0.50f),
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(252.dp)
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)),
    ) {
        // Background image
        if (photoUrl != null) {
            AsyncImage(
                model = photoUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colorScheme.primaryContainer),
            )
        }

        // Overall gradient scrim
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(heroOverlay),
        )

        // Top gradient for icon legibility
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .align(Alignment.TopCenter)
                .background(heroGradientTop),
        )

        // Bottom gradient for text legibility
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .align(Alignment.BottomCenter)
                .background(heroGradientBottom),
        )

        // Back button
        val backInteractionSource = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .padding(start = 4.dp, top = topInset + 8.dp)
                .align(Alignment.TopStart)
                .pressScale(backInteractionSource, pressedScale = 0.88f)
                .size(40.dp)
                .clip(CircleShape)
                .background(colorScheme.surfaceVariant.copy(alpha = 0.92f))
                .clickable(
                    interactionSource = backInteractionSource,
                    indication = LocalIndication.current,
                ) { onBackClick() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }

        // More options button
        val moreInteractionSource = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .padding(end = 4.dp, top = topInset + 8.dp)
                .align(Alignment.TopEnd)
                .pressScale(moreInteractionSource, pressedScale = 0.88f)
                .size(40.dp)
                .clip(CircleShape)
                .background(colorScheme.surfaceVariant.copy(alpha = 0.92f))
                .clickable(
                    interactionSource = moreInteractionSource,
                    indication = LocalIndication.current,
                ) { onMoreClick() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.more_options),
                tint = colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }

        // Group name + description pinned to bottom-start
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = groupName,
                fontSize = 36.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                letterSpacing = 0.5.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = groupDescription,
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                color = Color.White.copy(alpha = 0.9f),
                letterSpacing = 0.4.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Balance Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BalanceCard(
    modifier: Modifier = Modifier,
    balance: MemberBalance,
    onClick: () -> Unit,
    onAvatarClick: (() -> Unit)? = null
) {
    val colorScheme = MaterialTheme.colorScheme
    val canSettle = balance.direction == BalanceDirection.YOU_OWE
    val amountColor = when (balance.direction) {
        BalanceDirection.OWED_TO_YOU -> colorScheme.primary
        BalanceDirection.YOU_OWE     -> colorScheme.error
    }
    val directionLabel = when (balance.direction) {
        BalanceDirection.OWED_TO_YOU -> stringResource(R.string.owes_you)
        BalanceDirection.YOU_OWE     -> stringResource(R.string.you_owe_member)
    }
    val trendIcon = when (balance.direction) {
        BalanceDirection.OWED_TO_YOU -> Icons.AutoMirrored.Filled.TrendingUp
        BalanceDirection.YOU_OWE     -> Icons.AutoMirrored.Filled.TrendingDown
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(
                    imageUrl = balance.memberAvatarUrl,
                    displayName = balance.memberName,
                    size = 32.dp,
                    contentDescription = balance.memberName,
                    onClicked = { onAvatarClick?.invoke() },
                )

                Spacer(Modifier.width(12.dp))

                // Name + direction
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = balance.memberName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = colorScheme.onSurface,
                        letterSpacing = 0.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = directionLabel,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        color = colorScheme.onSurfaceVariant,
                        letterSpacing = 0.5.sp,
                    )
                }

                // Amount with trend icon
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = trendIcon,
                        contentDescription = null,
                        tint = amountColor,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = balance.amount,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = amountColor,
                        letterSpacing = 0.5.sp,
                    )
                }
            }

            if (canSettle) {
                HorizontalDivider(color = colorScheme.outlineVariant)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Payments,
                                contentDescription = null,
                                tint = colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Text(
                            text = stringResource(R.string.settle_debt_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    FilledTonalButton(
                        onClick = onClick,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Payments,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.settle_debt_action),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Settle Debt Dialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SettleDebtDialog(
    balance: MemberBalance,
    amount: String,
    isSettling: Boolean,
    onAmountChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val maxAmount = remember(balance.amountValue) {
        BigDecimal.valueOf(balance.amountValue).setScale(2, RoundingMode.HALF_UP)
    }
    val parsedAmount = remember(amount) { amount.toPositiveSettlementAmountOrNull() }
    val isTooHigh = parsedAmount != null && parsedAmount > maxAmount
    val canConfirm = parsedAmount != null && !isTooHigh && !isSettling

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.settle_debt_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(
                        R.string.settle_debt_message,
                        balance.memberName,
                        balance.amount,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = onAmountChange,
                    label = { Text(stringResource(R.string.settle_debt_amount_label)) },
                    singleLine = true,
                    isError = isTooHigh,
                    enabled = !isSettling,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (isTooHigh) {
                    Text(
                        text = stringResource(R.string.settle_debt_amount_too_high, balance.amount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canConfirm,
                onClick = onConfirm,
            ) {
                if (isSettling) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(text = stringResource(R.string.settle_debt_confirm))
                }
            }
        },
        dismissButton = {
            TextButton(
                enabled = !isSettling,
                onClick = onDismiss,
            ) {
                Text(text = stringResource(R.string.cancel))
            }
        },
    )
}

// Spending Summary Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SpendingSummaryCard(
    totalSpending: String,
    totalToReceive: String,
    totalYouOwe: String,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.secondaryContainer),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Total group spending
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.total_group_spending),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.onSecondaryContainer,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = totalSpending,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSecondaryContainer,
                    letterSpacing = 0.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Total to receive
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.total_to_receive),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    color = colorScheme.onSecondaryContainer,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = totalToReceive,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.primary,
                    letterSpacing = 0.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Total you owe
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.total_you_owe),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    color = colorScheme.onSecondaryContainer,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = totalYouOwe,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.error,
                    letterSpacing = 0.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Expense Row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ExpenseRow(
    expense: ExpenseItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Category icon badge
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = expense.icon,
                    contentDescription = null,
                    tint = colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }

            Spacer(Modifier.width(12.dp))

            // Title + paid-by meta
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = expense.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal,
                    color = colorScheme.onSurface,
                    letterSpacing = 0.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = expense.paidByLabel,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    color = colorScheme.onSurfaceVariant,
                    letterSpacing = 0.25.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = expense.dateLabel,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
                    letterSpacing = 0.25.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.width(8.dp))

            // Amount
            Text(
                text = expense.amount,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSurface,
                letterSpacing = 0.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Settlement Row  (non-clickable, visually distinct)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SettlementRow(
    expense: ExpenseItem,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.tertiaryContainer.copy(alpha = 0.25f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(
            0.5.dp,
            colorScheme.tertiary.copy(alpha = 0.3f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(colorScheme.tertiaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = expense.icon,
                    contentDescription = null,
                    tint = colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = expense.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.onSurface,
                    letterSpacing = 0.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = expense.paidByLabel,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    color = colorScheme.onSurfaceVariant,
                    letterSpacing = 0.25.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = expense.dateLabel,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
                    letterSpacing = 0.25.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.width(8.dp))

            Text(
                text = expense.amount,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.tertiary,
                letterSpacing = 0.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Add Expense floating button
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EmptyExpensesState(
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Receipt,
                    contentDescription = null,
                    tint = colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(30.dp),
                )
            }

            Spacer(Modifier.height(18.dp))

            Text(
                text = stringResource(R.string.group_expenses_empty_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.group_expenses_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// Shared helpers
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 0.5.sp,
        modifier = modifier,
    )
}

private fun String.toPositiveSettlementAmountOrNull(): BigDecimal? =
    trim()
        .replace(',', '.')
        .toBigDecimalOrNull()
        ?.setScale(2, RoundingMode.HALF_UP)
        ?.takeIf { it.signum() > 0 }

@Composable
private fun ExpenseSectionHeader(title: String, modifier: Modifier = Modifier) {
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
