/** Manages state for the expense create screen. */
package it.unibo.equishare.ui.screens.expense.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.unibo.equishare.domain.model.Expense
import it.unibo.equishare.domain.model.Money
import it.unibo.equishare.domain.repository.AuthRepository
import it.unibo.equishare.domain.repository.ExpensesRepository
import it.unibo.equishare.domain.repository.GroupsRepository
import it.unibo.equishare.domain.repository.ProfileRepository
import it.unibo.equishare.domain.usecase.AddExpenseUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

class AddExpenseViewModel(
    private val expensesRepository: ExpensesRepository,
    private val groupsRepository: GroupsRepository,
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
    private val addExpense: AddExpenseUseCase,
) : ViewModel() {

    val groupId = MutableStateFlow<String?>(null)

    private val editExpenseId = MutableStateFlow<String?>(null)

    private val _uiState = MutableStateFlow(
        AddExpenseUiState(currentUserId = authRepository.currentUserId)
    )
    val uiState: StateFlow<AddExpenseUiState> = _uiState.asStateFlow()

    init {
        loadCategories()
        viewModelScope.launch {
            profileRepository.defaultCurrency.collectLatest { currency ->
                if (editExpenseId.value == null) {
                    _uiState.update { it.copy(amountCurrency = currency) }
                }
            }
        }
        viewModelScope.launch {
            groupId.collectLatest { id ->
                if (id != null) loadMembers(id)
            }
        }
    }

    fun onEvent(event: AddExpenseEvent) {
        when (event) {
            is AddExpenseEvent.AmountChanged      -> _uiState.update { it.copy(amount = event.value) }
            is AddExpenseEvent.DescriptionChanged -> _uiState.update { it.copy(description = event.value, descriptionError = null) }
            is AddExpenseEvent.DateChanged        -> _uiState.update { it.copy(date = event.value) }
            is AddExpenseEvent.CategorySelected   -> _uiState.update {
                it.copy(selectedCategory = event.category, pendingCategoryId = event.category.id)
            }
            is AddExpenseEvent.PaidByConfirmed    -> _uiState.update { state ->
                val userIds = if (state.isEditMode) {
                    state.currentUserId?.let { currentUserId -> event.userIds + currentUserId }
                        ?: event.userIds
                } else {
                    event.userIds
                }
                state.copy(paidByUserIds = userIds)
            }
            is AddExpenseEvent.SplitConfirmed     -> _uiState.update { it.copy(splitAmongUserIds = event.userIds) }
            is AddExpenseEvent.ReceiptPicked      -> _uiState.update {
                it.copy(receiptImageUri = event.localUri, receiptUpload = event.upload)
            }
            AddExpenseEvent.SaveClicked           -> submit()
            AddExpenseEvent.SaveErrorShown        -> _uiState.update { it.copy(saveError = null) }
            AddExpenseEvent.BackClicked,
            AddExpenseEvent.PaidByClicked,
            AddExpenseEvent.ReceiptAreaClicked,
            AddExpenseEvent.SplitMethodClicked    -> { /* dialog visibility handled by screen */ }
        }
    }

    fun setGroupId(id: String) {
        if (groupId.value != id) {
            groupId.value = id
        }
    }

    fun setEditExpenseId(id: String) {
        if (editExpenseId.value == id) return
        editExpenseId.value = id
        _uiState.update { it.copy(isEditMode = true, canEditExistingExpense = false) }
        viewModelScope.launch {
            val expense = expensesRepository.getById(id).first() ?: run {
                _uiState.update {
                    it.copy(
                        canEditExistingExpense = false,
                        saveError = SAVE_ERROR_EXPENSE_NOT_FOUND,
                    )
                }
                return@launch
            }
            // Load group members before restoring payer/sharer selection so
            // the pickers don't open with an empty list.
            setGroupId(expense.groupId)
            val participants = runCatching { expensesRepository.getParticipants(id) }
                .getOrDefault(emptyList())
            if (!participants.canBeModifiedByCurrentUser()) {
                _uiState.update {
                    it.copy(
                        canEditExistingExpense = false,
                        saveError = SAVE_ERROR_EXPENSE_PERMISSION_DENIED,
                    )
                }
                return@launch
            }
            // Payers are the participants with `paid_amount > 0`; sharers
            // are those with `owed_amount > 0`. The previous version mixed
            // both into the payers list, which then caused the use case to
            // generate participant rows with paid > 0 for every member —
            // breaking the "sum(paid) = total" invariant on save.
            val payerIds = participants.filter { !it.paid.isZero }.map { it.userId }
            val sharerIds = participants.filter { !it.owed.isZero }.map { it.userId }
            _uiState.update { it.copy(canEditExistingExpense = true, saveError = null) }
            prefillFormFrom(expense, payerIds, sharerIds)
        }
    }

    private fun prefillFormFrom(
        expense: Expense,
        payerIds: List<String>,
        sharerIds: List<String>,
    ) {
        _uiState.update { state ->
            state.copy(
                amount = "%.2f".format(expense.total.toDouble()),
                amountCurrency = expense.total.currency,
                description = expense.title,
                date = expense.expenseDate,
                pendingCategoryId = expense.categoryId,
                selectedCategory = state.categories.firstOrNull { it.id == expense.categoryId }
                    ?: state.selectedCategory,
                // Never overwrite the form selection with an EMPTY set —
                // if the participants fetch returned nothing (e.g. RLS),
                // fall back to whatever loadMembers populated as defaults
                // so the Save button isn't permanently disabled.
                paidByUserIds = when {
                    payerIds.isNotEmpty() -> payerIds.toSet()
                    state.paidByUserIds.isNotEmpty() -> state.paidByUserIds
                    else -> setOf(expense.paidByUserId)
                },
                splitAmongUserIds = if (sharerIds.isNotEmpty()) {
                    sharerIds.toSet()
                } else {
                    state.splitAmongUserIds
                },
                receiptImageUri = expense.receiptUrl,
                receiptUpload = null,
            )
        }
    }

    fun consumeSaved() {
        _uiState.update { it.copy(isSaved = false) }
    }

    fun refresh() {
        if (_uiState.value.isRefreshing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            groupsRepository.refresh()
            expensesRepository.refresh()
            loadCategories()
            groupId.value?.let { loadMembers(it) }
            delay(700)
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    private fun loadMembers(gid: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isMembersLoading = true, membersError = null) }
            runCatching { groupsRepository.getGroupMembers(gid).first() }
                .onSuccess { members ->
                    val options = members.map {
                        MemberOption(
                            userId = it.userId,
                            displayName = it.displayName,
                            avatarUrl = it.avatarUrl,
                            isCurrentUser = it.isCurrentUser,
                        )
                    }
                    val currentUid = authRepository.currentUserId
                    val defaultPaidBy: Set<String> = when {
                        currentUid != null && options.any { it.userId == currentUid } -> setOf(currentUid)
                        options.isNotEmpty() -> setOf(options.first().userId)
                        else -> emptySet()
                    }
                    val defaultSplit: Set<String> = options.map { it.userId }.toSet()
                    _uiState.update { state ->
                        state.copy(
                            members = options,
                            currentUserId = currentUid,
                            isMembersLoading = false,
                            membersError = null,
                            paidByUserIds = state.paidByUserIds.ifEmpty { defaultPaidBy },
                            splitAmongUserIds = state.splitAmongUserIds.ifEmpty { defaultSplit },
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isMembersLoading = false,
                            membersError = e.message ?: "Unable to load members",
                        )
                    }
                }
        }
    }

    private fun loadCategories() {
        viewModelScope.launch {
            _uiState.update { it.copy(isCategoriesLoading = true, categoriesError = null) }
            runCatching { expensesRepository.getExpenseCategories() }
                .onSuccess { categories ->
                    val options = categories.map {
                        ExpenseCategoryOption(
                            id = it.id,
                            code = it.code,
                            name = it.name,
                            translations = it.translations,
                            iconKey = it.iconKey,
                        )
                    }
                    _uiState.update {
                        it.copy(
                            categories = options,
                            selectedCategory = it.pendingCategoryId
                                ?.let { categoryId -> options.firstOrNull { category -> category.id == categoryId } }
                                ?: it.selectedCategory
                                    ?.let { selected -> options.firstOrNull { category -> category.id == selected.id } }
                                ?: options.firstOrNull(),
                            isCategoriesLoading = false,
                            categoriesError = null,
                        )
                    }
                }
                .onFailure {
                    _uiState.update {
                        it.copy(
                            categories = emptyList(),
                            selectedCategory = null,
                            isCategoriesLoading = false,
                            categoriesError = "Unable to load categories",
                        )
                    }
                }
        }
    }

    private fun submit() {
        val state = _uiState.value
        val gid = groupId.value ?: return
        if (state.isEditMode && !state.canEditExistingExpense) {
            _uiState.update { it.copy(saveError = SAVE_ERROR_EXPENSE_PERMISSION_DENIED) }
            return
        }
        if (state.isEditMode && state.currentUserId !in state.paidByUserIds) {
            _uiState.update { it.copy(saveError = SAVE_ERROR_EXPENSE_PERMISSION_DENIED) }
            return
        }
        if (!state.isSaveEnabled) return

        val payers = state.paidByUserIds.toList()
        val sharers = state.splitAmongUserIds.toList()
        if (payers.isEmpty() || sharers.isEmpty()) return

        _uiState.update { it.copy(isLoading = true, saveError = null) }
        viewModelScope.launch {
            if (currentGroupAccessLost(gid)) {
                showGroupAccessLost()
                return@launch
            }

            val parsedAmount = state.amount.parseAmountInput()
            if (parsedAmount?.let { it.signum() > 0 } != true) {
                _uiState.update { it.copy(isLoading = false, saveError = SAVE_ERROR_INVALID_AMOUNT) }
                return@launch
            }
            if (state.amount.isAmountTooLarge()) {
                _uiState.update { it.copy(isLoading = false, saveError = SAVE_ERROR_AMOUNT_TOO_LARGE) }
                return@launch
            }
            val total = Money.parse(state.amount, state.amountCurrency)
            if (total == null || total.isZero || total.isNegative) {
                _uiState.update { it.copy(isLoading = false, saveError = SAVE_ERROR_INVALID_AMOUNT) }
                return@launch
            }

            addExpense(
                AddExpenseUseCase.Input(
                    groupId = gid,
                    title = state.description,
                    total = total,
                    expenseDate = state.date.ifBlank { LocalDate.now().toString() },
                    categoryId = state.selectedCategory?.id,
                    payerIds = payers,
                    sharerIds = sharers,
                    preferredPrimaryPayerId = state.currentUserId,
                    receipt = state.receiptUpload,
                    editExpenseId = editExpenseId.value,
                )
            ).onSuccess {
                _uiState.update {
                    it.copy(
                        amount = "",
                        description = "",
                        date = LocalDate.now().toString(),
                        receiptImageUri = null,
                        receiptUpload = null,
                        descriptionError = null,
                        isLoading = false,
                        isSaved = true,
                        saveError = null,
                    )
                }
            }.onFailure {
                val errorCode = if (currentGroupAccessLost(gid)) {
                    SAVE_ERROR_GROUP_ACCESS_LOST
                } else {
                    SAVE_ERROR_GENERIC
                }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        saveError = errorCode,
                    )
                }
            }
        }
    }

    private suspend fun currentGroupAccessLost(groupId: String): Boolean =
        try {
            !groupsRepository.currentUserCanAccess(groupId)
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            false
        }

    private fun showGroupAccessLost() {
        _uiState.update {
            it.copy(
                isLoading = false,
                saveError = SAVE_ERROR_GROUP_ACCESS_LOST,
                canEditExistingExpense = false,
            )
        }
        groupsRepository.refresh()
        expensesRepository.refresh()
    }

    private fun List<it.unibo.equishare.domain.model.ExpenseParticipant>.canBeModifiedByCurrentUser(): Boolean {
        val currentUid = authRepository.currentUserId ?: return false
        return any { participant ->
            participant.userId == currentUid && !participant.paid.isZero
        }
    }
}
