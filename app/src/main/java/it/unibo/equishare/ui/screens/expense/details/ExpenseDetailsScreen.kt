/** Renders the expense details screen UI. */
package it.unibo.equishare.ui.screens.expense.details

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.ImageNotSupported
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import it.unibo.equishare.R
import it.unibo.equishare.domain.model.GroupMember
import it.unibo.equishare.domain.model.SplitMethod
import it.unibo.equishare.ui.components.avatar.Avatar
import it.unibo.equishare.ui.components.receipt.ReceiptPreviewDialog
import it.unibo.equishare.ui.components.refresh.EquiSharePullToRefresh
import it.unibo.equishare.ui.components.topbar.CompactBackAppBar
import it.unibo.equishare.ui.theme.EquiShareTheme

// ─────────────────────────────────────────────────────────────────────────────
// UI State
// ─────────────────────────────────────────────────────────────────────────────

data class ExpenseInfoUiState(
    val amount: String = "",
    val groupName: String = "",
    val description: String = "",
    val date: String = "",
    val categoryName: String = "",
    val paidByName: String = "",
    val isPaidByCurrentUser: Boolean = false,
    val splitMethod: SplitMethod? = null,
    val members: List<GroupMember> = emptyList(),
    val paidByUserIds: Set<String> = emptySet(),
    val splitAmongUserIds: Set<String> = emptySet(),
    val receiptImageUrl: String? = null,
    val canModifyExpense: Boolean = false,
    val isRefreshing: Boolean = false,
)

// ─────────────────────────────────────────────────────────────────────────────
// Events
// ─────────────────────────────────────────────────────────────────────────────

sealed interface ExpenseInfoEvent {
    data object BackClicked : ExpenseInfoEvent
    data object EditClicked : ExpenseInfoEvent
    data object DeleteClicked : ExpenseInfoEvent
    data object PaidByClicked : ExpenseInfoEvent
    data object SplitMethodClicked : ExpenseInfoEvent
    data object ReceiptAreaClicked : ExpenseInfoEvent
}

// ─────────────────────────────────────────────────────────────────────────────
// Screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ExpenseInfoScreen(
    modifier: Modifier = Modifier,
    uiState: ExpenseInfoUiState,
    onEvent: (ExpenseInfoEvent) -> Unit,
    onRefresh: () -> Unit = {}
) {
    var previewReceiptUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var showPaidByDialog by rememberSaveable { mutableStateOf(false) }
    var showSplitDialog by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ExpenseInfoTopBar(
                onBackClick   = { onEvent(ExpenseInfoEvent.BackClicked) },
                onEditClick   = { onEvent(ExpenseInfoEvent.EditClicked) },
                onDeleteClick = { onEvent(ExpenseInfoEvent.DeleteClicked) },
                showModifyActions = uiState.canModifyExpense,
            )
        },
    ) { innerPadding ->
        EquiSharePullToRefresh(
            isRefreshing = uiState.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.padding(innerPadding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(top = 24.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {

                // ── Amount hero card ───────────────────────────────────────
                AmountCard(amount = uiState.amount)

                ReadOnlyOutlinedField(
                    label        = stringResource(R.string.expense_group),
                    value        = uiState.groupName,
                    leadingIcon  = Icons.Default.Group,
                    trailingIcon = null,
                )

                // ── Description field ──────────────────────────────────────
                ReadOnlyOutlinedField(
                    label        = stringResource(R.string.description),
                    value        = uiState.description,
                    leadingIcon  = Icons.AutoMirrored.Filled.TextSnippet,
                    trailingIcon = null,
                )

                // ── Date field ─────────────────────────────────────────────
                ReadOnlyOutlinedField(
                    label        = stringResource(R.string.date),
                    value        = uiState.date,
                    leadingIcon  = null,
                    trailingIcon = Icons.Default.CalendarToday,
                )

                // ── Category field ─────────────────────────────────────────
                ReadOnlyOutlinedField(
                    label        = stringResource(R.string.category),
                    value        = uiState.categoryName,
                    leadingIcon  = null,
                    trailingIcon = Icons.Default.ExpandMore,
                )

                // ── Split Details heading ──────────────────────────────────
                Text(
                    text = stringResource(R.string.split_details),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 8.dp),
                )

                // ── Split Details card ─────────────────────────────────────
                SplitDetailsCard(
                    paidBy = uiState.paidByDisplayLabel(),
                    splitMethod = uiState.splitMethodDisplayLabel(),
                    onPaidByClick = {
                        onEvent(ExpenseInfoEvent.PaidByClicked)
                        showPaidByDialog = true
                    },
                    onSplitMethodClick = {
                        onEvent(ExpenseInfoEvent.SplitMethodClicked)
                        showSplitDialog = true
                    },
                )

                // ── Receipt heading ────────────────────────────────────────
                Text(
                    text = stringResource(R.string.receipt),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 8.dp),
                )

                // ── Receipt image / placeholder ────────────────────────────
                ReceiptArea(
                    imageUrl = uiState.receiptImageUrl,
                    onClick = {
                        uiState.receiptImageUrl?.let { previewReceiptUrl = it }
                        onEvent(ExpenseInfoEvent.ReceiptAreaClicked)
                    },
                )
            }
        }
    }

    if (showPaidByDialog) {
        MemberSelectionDialog(
            title = stringResource(R.string.paid_by),
            subtitle = stringResource(R.string.paid_by_dialog_subtitle),
            members = uiState.members,
            selected = uiState.paidByUserIds,
            onDismiss = { showPaidByDialog = false },
        )
    }

    if (showSplitDialog) {
        MemberSelectionDialog(
            title = stringResource(R.string.split),
            subtitle = stringResource(R.string.split_dialog_subtitle),
            members = uiState.members,
            selected = uiState.splitAmongUserIds,
            onDismiss = { showSplitDialog = false },
        )
    }

    previewReceiptUrl?.let { url ->
        ReceiptPreviewDialog(
            imageUri = url,
            onDismiss = { previewReceiptUrl = null },
        )
    }
}

@Composable
private fun ExpenseInfoUiState.paidByDisplayLabel(): String {
    if (paidByName.isBlank()) return ""
    return if (isPaidByCurrentUser) stringResource(R.string.you_label) else paidByName
}

@Composable
private fun ExpenseInfoUiState.splitMethodDisplayLabel(): String = when (splitMethod) {
    SplitMethod.EQUAL      -> stringResource(R.string.split_equally)
    SplitMethod.EXACT      -> stringResource(R.string.split_method_exact)
    SplitMethod.PERCENTAGE -> stringResource(R.string.split_method_percentage)
    SplitMethod.SHARES     -> stringResource(R.string.split_method_shares)
    SplitMethod.ADJUSTMENT -> stringResource(R.string.split_method_adjustment)
    null                   -> ""
}

// ─────────────────────────────────────────────────────────────────────────────
// Top App Bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ExpenseInfoTopBar(
    onBackClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    showModifyActions: Boolean,
) {
    val colorScheme = MaterialTheme.colorScheme
    CompactBackAppBar(
        title = stringResource(R.string.expense_info),
        onBackClick = onBackClick,
    ) {
        if (showModifyActions) {
            // Edit button — themed surface variant background
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(colorScheme.surfaceVariant)
                    .clickable { onEditClick() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = stringResource(R.string.edit_expense),
                    tint = colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            // Delete button — themed error tint
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(colorScheme.surfaceVariant)
                    .clickable { onDeleteClick() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete_expense),
                    tint = colorScheme.error,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
        }
    }
}

@Composable
private fun AmountCard(amount: String) {
    val colorScheme = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainerLow),
        border = BorderStroke(0.5.dp, colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Default.Receipt,
                contentDescription = null,
                tint = colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = amount,
                fontSize = 56.sp,
                fontWeight = FontWeight.Normal,
                color = colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ReadOnlyOutlinedField(
    label: String,
    value: String,
    leadingIcon: ImageVector?,
    trailingIcon: ImageVector?,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    OutlinedTextField(
        value = value,
        onValueChange = { /* read-only */ },
        label = { Text(label) },
        leadingIcon = leadingIcon?.let {
            { Icon(imageVector = it, contentDescription = null, tint = colorScheme.onSurfaceVariant) }
        },
        trailingIcon = trailingIcon?.let {
            { Icon(imageVector = it, contentDescription = null, tint = colorScheme.onSurfaceVariant) }
        },
        readOnly = true,
        enabled = false,
        singleLine = true,
        shape = RoundedCornerShape(4.dp),
        modifier = modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            disabledBorderColor    = colorScheme.outline,
            disabledLabelColor     = colorScheme.onSurfaceVariant,
            disabledTextColor      = colorScheme.onSurface,
            disabledLeadingIconColor  = colorScheme.onSurfaceVariant,
            disabledTrailingIconColor = colorScheme.onSurfaceVariant,
            disabledContainerColor = colorScheme.surface,
        ),
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Split Details Card — same visual language as AddExpenseScreen.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SplitDetailsCard(
    paidBy: String,
    splitMethod: String,
    onPaidByClick: () -> Unit,
    onSplitMethodClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainerLow),
        border = BorderStroke(0.5.dp, colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {

            SplitDetailRow(
                icon          = Icons.Default.Person,
                label         = stringResource(R.string.paid_by),
                value         = paidBy,
                onClick       = onPaidByClick,
            )

            HorizontalDivider(
                color     = colorScheme.outlineVariant,
                thickness = 0.5.dp,
                modifier  = Modifier.padding(horizontal = 16.dp),
            )

            SplitDetailRow(
                icon          = Icons.Default.Group,
                label         = stringResource(R.string.split),
                value         = splitMethod,
                onClick       = onSplitMethodClick,
            )
        }
    }
}

@Composable
private fun SplitDetailRow(
    icon: ImageVector,
    label: String,
    value: String,
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colorScheme.onSurface,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (value.isNotBlank()) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .padding(end = 8.dp),
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Member Selection Dialog (read-only)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MemberSelectionDialog(
    title: String,
    subtitle: String,
    members: List<GroupMember>,
    selected: Set<String>,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp),
            ) {
                items(items = members, key = { it.userId }) { member ->
                    MemberRow(
                        member = member,
                        checked = member.userId in selected,
                    )
                    if (member != members.lastOrNull()) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant,
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(start = 72.dp, end = 16.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.close))
            }
        },
    )
}

@Composable
private fun MemberRow(
    member: GroupMember,
    checked: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Avatar(
            imageUrl = member.avatarUrl,
            displayName = member.displayName,
            size = 40.dp,
        )
        Text(
            text = member.displayName,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            enabled = false,
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colorScheme.primary,
                uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                checkmarkColor = MaterialTheme.colorScheme.onPrimary,
                disabledCheckedColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f),
                disabledUncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
            ),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Receipt Area
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ReceiptArea(
    imageUrl: String?,
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colorScheme.surfaceContainerLow)
            .border(
                width = 1.5.dp,
                color = colorScheme.outlineVariant,
                shape = RoundedCornerShape(16.dp),
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = stringResource(R.string.receipt),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp)),
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.ImageNotSupported,
                    contentDescription = null,
                    tint = colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(40.dp),
                )
                Text(
                    text  = stringResource(R.string.no_receipt),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
