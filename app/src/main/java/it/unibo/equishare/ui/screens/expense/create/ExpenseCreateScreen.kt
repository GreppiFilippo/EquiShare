/** Renders the expense create screen UI. */
package it.unibo.equishare.ui.screens.expense.create

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import it.unibo.equishare.R
import it.unibo.equishare.domain.model.Currency
import it.unibo.equishare.domain.model.ImageUpload
import it.unibo.equishare.ui.components.avatar.Avatar
import it.unibo.equishare.ui.components.buttons.EquiShareFab
import it.unibo.equishare.ui.components.permissions.ImageSourceBottomSheet
import it.unibo.equishare.ui.components.permissions.readPickedImage
import it.unibo.equishare.ui.components.receipt.ReceiptPreviewDialog
import it.unibo.equishare.ui.components.refresh.EquiSharePullToRefresh
import it.unibo.equishare.ui.components.snackbar.EquiShareSnackbarHost
import it.unibo.equishare.ui.components.topbar.CompactBackAppBar
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

data class MemberOption(
    val userId: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val isCurrentUser: Boolean = false,
)

data class AddExpenseUiState(
    val amount: String = "",
    val amountCurrency: Currency = Currency.EUR,
    val description: String = "",
    val date: String = LocalDate.now().toString(),
    val categories: List<ExpenseCategoryOption> = emptyList(),
    val selectedCategory: ExpenseCategoryOption? = null,
    val pendingCategoryId: String? = null,
    val isCategoriesLoading: Boolean = false,
    val categoriesError: String? = null,
    // Members & selections
    val members: List<MemberOption> = emptyList(),
    val currentUserId: String? = null,
    val paidByUserIds: Set<String> = emptySet(),
    val splitAmongUserIds: Set<String> = emptySet(),
    val isMembersLoading: Boolean = false,
    val membersError: String? = null,
    val receiptImageUri: String? = null,
    val receiptUpload: ImageUpload? = null,
    val descriptionError: String? = null,
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val saveError: String? = null,
    val isRefreshing: Boolean = false,
    val isEditMode: Boolean = false,
    val canEditExistingExpense: Boolean = true,
) {
    val isSaveEnabled: Boolean
        get() = amount.isPositiveSupportedAmountInput() &&
                description.isNotBlank() &&
                paidByUserIds.isNotEmpty() &&
                splitAmongUserIds.isNotEmpty() &&
                (!isEditMode || (canEditExistingExpense && currentUserId in paidByUserIds))
}

data class ExpenseCategoryOption(
    val id: String,
    val name: String,
    val code: String = "",
    val translations: Map<String, String> = emptyMap(),
    val iconKey: String? = null,
)

private fun ExpenseCategoryOption.localizedName(locale: Locale): String =
    translations[locale.language]?.takeIf { it.isNotBlank() }
        ?: translations.values.firstOrNull { it.isNotBlank() }
        ?: name

sealed interface AddExpenseEvent {
    data object BackClicked : AddExpenseEvent
    data class AmountChanged(val value: String) : AddExpenseEvent
    data class DescriptionChanged(val value: String) : AddExpenseEvent
    data class DateChanged(val value: String) : AddExpenseEvent
    data class CategorySelected(val category: ExpenseCategoryOption) : AddExpenseEvent
    data object PaidByClicked : AddExpenseEvent
    data object SplitMethodClicked : AddExpenseEvent
    data class PaidByConfirmed(val userIds: Set<String>) : AddExpenseEvent
    data class SplitConfirmed(val userIds: Set<String>) : AddExpenseEvent
    data object ReceiptAreaClicked : AddExpenseEvent
    data class ReceiptPicked(val localUri: String, val upload: ImageUpload) : AddExpenseEvent
    data object SaveClicked : AddExpenseEvent
    data object SaveErrorShown : AddExpenseEvent
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseScreen(
    modifier: Modifier = Modifier,
    uiState: AddExpenseUiState,
    onEvent: (AddExpenseEvent) -> Unit,
    onRefresh: () -> Unit = {}
) {
    val scrollState = rememberScrollState()
    val canSubmit = uiState.isSaveEnabled && !uiState.isLoading
    val isExpanded by remember(canSubmit) {
        derivedStateOf { scrollState.value == 0 && canSubmit }
    }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var isCategoryMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var showPaidByDialog by rememberSaveable { mutableStateOf(false) }
    var showSplitDialog by rememberSaveable { mutableStateOf(false) }
    var showImagePicker by rememberSaveable { mutableStateOf(false) }
    var previewReceiptUri by rememberSaveable { mutableStateOf<String?>(null) }
    // Local override for the picked receipt image so the user gets immediate
    // visual feedback; persisting it server-side is part of the Save flow.
    var pendingReceiptUri by rememberSaveable { mutableStateOf<String?>(null) }
    val effectiveReceiptUri = pendingReceiptUri ?: uiState.receiptImageUri

    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val errorMessage = uiState.saveError
    val imageReadError = stringResource(R.string.image_read_failed)
    val amountTooLargeError = stringResource(R.string.expense_amount_too_large)
    val permissionDeniedError = stringResource(R.string.expense_modify_not_allowed)
    val groupAccessLostError = stringResource(R.string.expense_group_access_lost)
    val invalidAmountError = stringResource(R.string.expense_invalid_amount)
    val expenseNotFoundError = stringResource(R.string.expense_not_found)
    val genericSaveError = stringResource(R.string.expense_save_failed)
    val cameraPermissionDeniedMessage = stringResource(R.string.camera_permission_rationale)
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            val message = when (errorMessage) {
                SAVE_ERROR_AMOUNT_TOO_LARGE -> amountTooLargeError
                SAVE_ERROR_EXPENSE_PERMISSION_DENIED -> permissionDeniedError
                SAVE_ERROR_GROUP_ACCESS_LOST -> groupAccessLostError
                SAVE_ERROR_INVALID_AMOUNT -> invalidAmountError
                SAVE_ERROR_EXPENSE_NOT_FOUND -> expenseNotFoundError
                SAVE_ERROR_GENERIC -> genericSaveError
                else -> genericSaveError
            }
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
            onEvent(AddExpenseEvent.SaveErrorShown)
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { EquiShareSnackbarHost(hostState = snackbarHostState) },
        topBar = {
            AddExpenseTopBar(
                title = stringResource(
                    if (uiState.isEditMode) R.string.edit_expense_title else R.string.add_expense
                ),
                onBackClick = { onEvent(AddExpenseEvent.BackClicked) },
            )
        },
        floatingActionButton = {
            AddExpenseSaveFab(
                expanded = isExpanded,
                isEnabled = canSubmit,
                isLoading = uiState.isLoading,
                onSaveClick = { onEvent(AddExpenseEvent.SaveClicked) },
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
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp)
                    .padding(top = 24.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {

            AmountCard(
                amount = uiState.amount,
                amountCurrency = uiState.amountCurrency,
                onAmountChange = { onEvent(AddExpenseEvent.AmountChanged(it)) },
                errorMessage = if (uiState.amount.isAmountTooLarge()) {
                    amountTooLargeError
                } else {
                    null
                },
            )

            ExpenseTextField(
                value = uiState.description,
                onValueChange = { onEvent(AddExpenseEvent.DescriptionChanged(it)) },
                label = stringResource(R.string.description),
                placeholder = stringResource(R.string.what_was_this_for),
                leadingIcon = Icons.AutoMirrored.Filled.TextSnippet,
                trailingIcon = null,
                errorMessage = uiState.descriptionError,
                keyboardOptions = KeyboardOptions.Default,
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDatePicker = true },
            ) {
                ExpenseTextField(
                    value = uiState.date,
                    onValueChange = {},
                    label = stringResource(R.string.date),
                    placeholder = "",
                    leadingIcon = null,
                    trailingIcon = Icons.Default.CalendarToday,
                    enabled = false,
                    readOnly = true,
                )
            }

            ExposedDropdownMenuBox(
                expanded = isCategoryMenuExpanded,
                onExpandedChange = { isCategoryMenuExpanded = !isCategoryMenuExpanded },
            ) {
                OutlinedTextField(
                    value = uiState.selectedCategory?.localizedName(locale).orEmpty(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.category_label)) },
                    placeholder = { Text(stringResource(R.string.select_category)) },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = isCategoryMenuExpanded)
                    },
                    modifier = Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true)
                        .fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )

                ExposedDropdownMenu(
                    expanded = isCategoryMenuExpanded,
                    onDismissRequest = { isCategoryMenuExpanded = false },
                ) {
                    uiState.categories.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category.localizedName(locale)) },
                            onClick = {
                                onEvent(AddExpenseEvent.CategorySelected(category))
                                isCategoryMenuExpanded = false
                            },
                        )
                    }
                }
            }
            if (uiState.isCategoriesLoading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            }
            if (uiState.categoriesError != null) {
                Text(
                    text = uiState.categoriesError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Text(
                text = stringResource(R.string.split_details),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 8.dp),
            )

            SplitDetailsCard(
                paidBy      = uiState.paidByDisplay(),
                splitMethod = uiState.splitDisplay(),
                onPaidByClick      = {
                    onEvent(AddExpenseEvent.PaidByClicked)
                    showPaidByDialog = true
                },
                onSplitMethodClick = {
                    onEvent(AddExpenseEvent.SplitMethodClicked)
                    showSplitDialog = true
                },
            )

            if (uiState.isMembersLoading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.loading_members),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (uiState.membersError != null) {
                Text(
                    text = stringResource(R.string.members_error),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Text(
                text = stringResource(R.string.receipt),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 8.dp),
            )

            ReceiptUploadArea(
                imageUri = effectiveReceiptUri,
                onClick  = {
                    if (effectiveReceiptUri == null) {
                        showImagePicker = true
                    } else {
                        previewReceiptUri = effectiveReceiptUri
                    }
                    onEvent(AddExpenseEvent.ReceiptAreaClicked)
                },
            )
        }
        }
    }

    if (showDatePicker) {
        val initialDateMillis = remember(uiState.date) { uiState.date.toDateMillisOrNull() }
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialDateMillis,
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { selectedMillis ->
                            val selectedDate = Instant.ofEpochMilli(selectedMillis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                                .toString()
                            onEvent(AddExpenseEvent.DateChanged(selectedDate))
                        }
                        showDatePicker = false
                    }
                ) {
                    Text(text = stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(text = stringResource(R.string.cancel))
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showPaidByDialog) {
        val requiredPayerId = uiState.currentUserId.takeIf { uiState.isEditMode }
        MemberSelectionDialog(
            title = stringResource(R.string.paid_by),
            subtitle = stringResource(R.string.paid_by_dialog_subtitle),
            members = uiState.members,
            initiallySelected = uiState.paidByUserIds,
            requiredSelectedUserId = requiredPayerId,
            requiredSelectionMessage = requiredPayerId?.let {
                stringResource(R.string.expense_current_user_payer_required)
            },
            onDismiss = { showPaidByDialog = false },
            onConfirm = { selected ->
                onEvent(AddExpenseEvent.PaidByConfirmed(selected))
                showPaidByDialog = false
            },
        )
    }

    if (showSplitDialog) {
        MemberSelectionDialog(
            title = stringResource(R.string.split),
            subtitle = stringResource(R.string.split_dialog_subtitle),
            members = uiState.members,
            initiallySelected = uiState.splitAmongUserIds,
            onDismiss = { showSplitDialog = false },
            onConfirm = { selected ->
                onEvent(AddExpenseEvent.SplitConfirmed(selected))
                showSplitDialog = false
            },
        )
    }

    if (showImagePicker) {
        ImageSourceBottomSheet(
            onDismiss = { showImagePicker = false },
            onImagePicked = { uri ->
                uri.readPickedImage(context)
                    .onSuccess { picked ->
                        pendingReceiptUri = picked.uri
                        onEvent(AddExpenseEvent.ReceiptPicked(picked.uri, picked.upload))
                    }
                    .onFailure {
                        snackbarScope.launch {
                            snackbarHostState.currentSnackbarData?.dismiss()
                            snackbarHostState.showSnackbar(imageReadError)
                        }
                    }
                showImagePicker = false
            },
            onPermissionDenied = {
                snackbarScope.launch {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(cameraPermissionDeniedMessage)
                }
            },
        )
    }

    previewReceiptUri?.let { uri ->
        ReceiptPreviewDialog(
            imageUri = uri,
            onDismiss = { previewReceiptUri = null },
            onChangeReceipt = {
                previewReceiptUri = null
                showImagePicker = true
            },
        )
    }
}

@Composable
private fun AddExpenseUiState.paidByDisplay(): String {
    if (paidByUserIds.isEmpty()) return ""
    if (paidByUserIds.size == 1) {
        val id = paidByUserIds.first()
        val member = members.firstOrNull { it.userId == id }
        return when {
            member == null -> ""
            member.isCurrentUser || id == currentUserId -> stringResource(R.string.you_label)
            else -> member.displayName
        }
    }
    return stringResource(R.string.paid_by_n_people, paidByUserIds.size)
}

@Composable
private fun AddExpenseUiState.splitDisplay(): String {
    if (splitAmongUserIds.isEmpty() || members.isEmpty()) return ""
    if (splitAmongUserIds.size == members.size) {
        return stringResource(R.string.split_equally)
    }
    return stringResource(R.string.split_among_n, splitAmongUserIds.size)
}

@Composable
private fun AddExpenseTopBar(
    onBackClick: () -> Unit,
    title: String = stringResource(R.string.add_expense),
) {
    CompactBackAppBar(
        title = title,
        onBackClick = onBackClick,
    )
}

@Composable
private fun AddExpenseSaveFab(
    expanded: Boolean,
    isEnabled: Boolean,
    isLoading: Boolean,
    onSaveClick: () -> Unit,
) {
    EquiShareFab(
        onClick = onSaveClick,
        expanded = expanded,
        enabled = isEnabled,
        contentDescription = stringResource(R.string.save),
        icon = {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                )
            }
        },
        text = {
            Text(
                text = stringResource(R.string.save),
                style = MaterialTheme.typography.labelLarge,
            )
        },
    )
}

@Composable
private fun AmountCard(
    amount: String,
    amountCurrency: Currency,
    onAmountChange: (String) -> Unit,
    errorMessage: String? = null,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Default.Receipt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp),
            )

            Spacer(Modifier.height(20.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                Text(
                    text = amountCurrency.symbol,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Normal,
                    color = if (amount.isEmpty())
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.width(4.dp))

                BasicTextField(
                    value = amount,
                    onValueChange = { raw ->
                        onAmountChange(raw.sanitizeAmountInput())
                    },
                    textStyle = TextStyle(
                        fontSize = 56.sp,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Start,
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 72.dp),
                    decorationBox = { innerTextField ->
                        Box {
                            if (amount.isEmpty()) {
                                Text(
                                    text = "0.00",
                                    fontSize = 56.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                                )
                            }
                            innerTextField()
                        }
                    },
                )
            }

            if (errorMessage != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
        }
    }
}

private fun String.sanitizeAmountInput(): String {
    var hasSeparator = false
    return buildString(length) {
        this@sanitizeAmountInput.forEach { char ->
            when {
                char.isDigit() -> append(char)
                (char == '.' || char == ',') && !hasSeparator -> {
                    append(char)
                    hasSeparator = true
                }
            }
        }
    }
}

private fun String.toDateMillisOrNull(): Long? =
    runCatching {
        LocalDate.parse(this)
            .atStartOfDay()
            .toInstant(ZoneOffset.UTC)
            .toEpochMilli()
    }.getOrNull()

@Composable
private fun ExpenseTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    leadingIcon: ImageVector?,
    trailingIcon: ImageVector?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    errorMessage: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = if (placeholder.isNotBlank()) {
            { Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else null,
        leadingIcon = leadingIcon?.let {
            { Icon(imageVector = it, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        },
        trailingIcon = trailingIcon?.let {
            { Icon(imageVector = it, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        },
        isError = errorMessage != null,
        supportingText = errorMessage?.let { { Text(it) } },
        enabled = enabled,
        readOnly = readOnly,
        singleLine = true,
        keyboardOptions = keyboardOptions,
        shape = RoundedCornerShape(4.dp),
        modifier = modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor      = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor    = MaterialTheme.colorScheme.outline,
            disabledBorderColor     = MaterialTheme.colorScheme.outline,
            focusedLabelColor       = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor     = MaterialTheme.colorScheme.onSurfaceVariant,
            disabledLabelColor      = MaterialTheme.colorScheme.onSurfaceVariant,
            focusedTextColor        = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor      = MaterialTheme.colorScheme.onSurface,
            disabledTextColor       = MaterialTheme.colorScheme.onSurface,
            cursorColor             = MaterialTheme.colorScheme.primary,
            focusedContainerColor   = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            disabledContainerColor  = MaterialTheme.colorScheme.surface,
        ),
    )
}

@Composable
private fun SplitDetailsCard(
    paidBy: String,
    splitMethod: String,
    onPaidByClick: () -> Unit,
    onSplitMethodClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {

            SplitDetailRow(
                icon          = Icons.Default.Person,
                label         = stringResource(R.string.paid_by),
                selectedValue = paidBy,
                onClick       = onPaidByClick,
            )

            HorizontalDivider(
                color     = MaterialTheme.colorScheme.outlineVariant,
                thickness = 0.5.dp,
                modifier  = Modifier.padding(horizontal = 16.dp),
            )

            SplitDetailRow(
                icon          = Icons.Default.Group,
                label         = stringResource(R.string.split),
                selectedValue = splitMethod,
                onClick       = onSplitMethodClick,
            )
        }
    }
}

@Composable
private fun SplitDetailRow(
    icon: ImageVector,
    label: String,
    selectedValue: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier            = Modifier.weight(1f),
            verticalAlignment   = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector     = icon,
                contentDescription = null,
                tint            = MaterialTheme.colorScheme.onSurface,
                modifier        = Modifier.size(20.dp),
            )
            Text(
                text            = label,
                style           = MaterialTheme.typography.bodyLarge,
                color           = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (selectedValue.isNotBlank()) {
            Text(
                text          = selectedValue,
                style         = MaterialTheme.typography.bodyMedium,
                color         = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier      = Modifier
                    .weight(1f, fill = false)
                    .padding(end = 8.dp),
            )
        }

        Icon(
            imageVector     = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint            = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier        = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun MemberSelectionDialog(
    title: String,
    subtitle: String,
    members: List<MemberOption>,
    initiallySelected: Set<String>,
    requiredSelectedUserId: String? = null,
    requiredSelectionMessage: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
) {
    var selected by rememberSaveable(initiallySelected, requiredSelectedUserId) {
        mutableStateOf(requiredSelectedUserId?.let { initiallySelected + it } ?: initiallySelected)
    }

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
                requiredSelectionMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        text = {
            // Cap the list to ~5 visible rows; scroll beyond that.
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp),
            ) {
                items(items = members, key = { it.userId }) { member ->
                    MemberRow(
                        member = member,
                        checked = member.userId in selected,
                        enabled = member.userId != requiredSelectedUserId,
                        onToggle = {
                            if (member.userId != requiredSelectedUserId) {
                                selected = if (member.userId in selected) {
                                    selected - member.userId
                                } else {
                                    selected + member.userId
                                }
                            }
                        },
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
            TextButton(
                onClick = { onConfirm(selected) },
                enabled = selected.isNotEmpty(),
            ) {
                Text(text = stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun MemberRow(
    member: MemberOption,
    checked: Boolean,
    enabled: Boolean = true,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(enabled = enabled) { onToggle() }
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
            onCheckedChange = { onToggle() },
            enabled = enabled,
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colorScheme.primary,
                uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                checkmarkColor = MaterialTheme.colorScheme.onPrimary,
            ),
        )
    }
}

@Composable
private fun ReceiptUploadArea(
    imageUri: String?,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(171.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(
                width = 1.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(16.dp),
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        if (imageUri != null) {
            AsyncImage(
                model = imageUri,
                contentDescription = stringResource(R.string.receipt),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp)),
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(
                    imageVector     = Icons.Default.AddAPhoto,
                    contentDescription = null,
                    tint            = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier        = Modifier.size(40.dp),
                )
                Text(
                    text          = stringResource(R.string.tap_to_take_picture),
                    style         = MaterialTheme.typography.bodyLarge,
                    color         = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign     = TextAlign.Center,
                    modifier      = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                )
            }
        }
    }
}
