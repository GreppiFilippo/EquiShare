/** Manages state for the expense details screen. */
package it.unibo.equishare.ui.screens.expense.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.unibo.equishare.domain.model.Expense
import it.unibo.equishare.domain.model.ExpenseCategory
import it.unibo.equishare.domain.model.ExpenseParticipant
import it.unibo.equishare.domain.model.Group
import it.unibo.equishare.domain.model.GroupMember
import it.unibo.equishare.domain.repository.AuthRepository
import it.unibo.equishare.domain.repository.ExpensesRepository
import it.unibo.equishare.domain.repository.GroupsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class ExpenseInfoViewModel(
    private val expensesRepository: ExpensesRepository,
    private val groupsRepository: GroupsRepository,
    private val authRepository: AuthRepository,
    private val appLanguageManager: it.unibo.equishare.data.local.AppLanguageManager,
) : ViewModel() {

    val expenseId = MutableStateFlow<String?>(null)
    private val isRefreshing = MutableStateFlow(false)
    private val categories = MutableStateFlow<List<ExpenseCategory>>(emptyList())

    init {
        loadCategories()
    }

    private val expenseFlow = expenseId.flatMapLatest { id ->
        if (id == null) flowOf<Expense?>(null) else expensesRepository.getById(id)
    }

    private val membersFlow = expenseFlow
        .map { it?.groupId }
        .distinctUntilChanged()
        .flatMapLatest { gid ->
            if (gid == null) flowOf(emptyList<GroupMember>())
            else groupsRepository.getGroupMembers(gid)
        }

    private val groupFlow = expenseFlow
        .map { it?.groupId }
        .distinctUntilChanged()
        .flatMapLatest { gid ->
            if (gid == null) flowOf<Group?>(null)
            else groupsRepository.getById(gid)
        }

    private val participantsFlow = expenseFlow.flatMapLatest { expense ->
        if (expense == null) {
            flowOf(emptyList())
        } else {
            flow { emit(expensesRepository.getParticipants(expense.id)) }
        }
    }

    private val contentState = combine(
        expenseFlow,
        groupFlow,
        membersFlow,
        participantsFlow,
        categories,
    ) { expense, group, members, participants, cats ->
        if (expense == null) {
            ExpenseInfoUiState()
        } else {
            buildUiState(expense, group, members, participants, cats)
        }
    }

    val uiState = combine(
        contentState,
        isRefreshing,
        appLanguageManager.languageTag
    ) { state, refreshing, _ ->
        state?.copy(isRefreshing = refreshing)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
    )

    fun onEvent(event: ExpenseInfoEvent) {
        when (event) {
            ExpenseInfoEvent.DeleteClicked -> {
                val id = expenseId.value ?: return
                if (uiState.value?.canModifyExpense != true) return
                viewModelScope.launch { expensesRepository.softDelete(id) }
            }
            else -> { /* navigation handled in Navigation.kt */ }
        }
    }

    fun setExpenseId(id: String) {
        if (expenseId.value != id) {
            expenseId.value = id
        }
    }

    fun refresh() {
        if (isRefreshing.value) return
        isRefreshing.update { true }
        viewModelScope.launch {
            expensesRepository.refresh()
            groupsRepository.refresh()
            loadCategories()
            delay(700)
            isRefreshing.update { false }
        }
    }

    private fun loadCategories() {
        viewModelScope.launch {
            runCatching { expensesRepository.getExpenseCategories() }
                .onSuccess { categories.value = it }
        }
    }

    // ── Mapping ──────────────────────────────────────────────────────────────

    private fun buildUiState(
        expense: Expense,
        group: Group?,
        members: List<GroupMember>,
        participants: List<ExpenseParticipant>,
        categories: List<ExpenseCategory>,
    ): ExpenseInfoUiState {
        val currentUid = authRepository.currentUserId
        val payer = members.firstOrNull { it.userId == expense.paidByUserId }
        val isCurrentUserPayer = expense.paidByUserId == currentUid
        val canModifyExpense = currentUid != null && participants.any { participant ->
            participant.userId == currentUid && !participant.paid.isZero
        }
        val payerIds = participants
            .filter { !it.paid.isZero }
            .map { it.userId }
            .toSet()
        val fallbackPayerIds = expense.payerUserIds
            .ifEmpty { listOf(expense.paidByUserId) }
            .filter { it.isNotBlank() }
            .toSet()
        val splitAmongUserIds = participants
            .filter { !it.owed.isZero }
            .map { it.userId }
            .toSet()

        // Fall back to a short user-id prefix when the member list hasn't loaded
        // yet — it's a better placeholder than the raw UUID and avoids an
        // empty row that hides the "paid by" label altogether.
        val paidByName = when {
            payer != null -> payer.displayName
            isCurrentUserPayer -> "" // the UI will substitute "You"
            else -> ""
        }

        val categoryName = categories.firstOrNull { it.id == expense.categoryId }
            ?.localizedName()
            .orEmpty()

        return ExpenseInfoUiState(
            amount        = expense.total.formatted(),
            groupName     = group?.name.orEmpty(),
            description   = expense.title,
            date          = formatDate(expense.expenseDate),
            categoryName  = categoryName,
            paidByName    = paidByName,
            isPaidByCurrentUser = isCurrentUserPayer,
            splitMethod   = expense.splitMethod,
            members       = members,
            paidByUserIds = if (payerIds.isNotEmpty()) payerIds else fallbackPayerIds,
            splitAmongUserIds = splitAmongUserIds,
            receiptImageUrl = expense.receiptUrl,
            canModifyExpense = canModifyExpense,
        )
    }

    private val displayFormatter get() =
        DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())

    private fun formatDate(raw: String): String = runCatching {
        LocalDate.parse(raw).format(displayFormatter)
    }.getOrDefault(raw)

    private fun ExpenseCategory.localizedName(): String =
        localizedName(Locale.getDefault().language)
}
