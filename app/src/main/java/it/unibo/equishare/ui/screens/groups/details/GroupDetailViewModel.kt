/** Manages state for the groups details screen. */
package it.unibo.equishare.ui.screens.groups.details

import android.content.Context
import android.content.res.Resources
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Sports
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.unibo.equishare.R
import it.unibo.equishare.domain.model.Currency
import it.unibo.equishare.domain.model.Expense
import it.unibo.equishare.domain.model.ExpenseCategory
import it.unibo.equishare.domain.model.GroupBalanceSummary
import it.unibo.equishare.domain.model.GroupMember
import it.unibo.equishare.domain.model.GroupMemberBalance
import it.unibo.equishare.domain.model.Money
import it.unibo.equishare.domain.model.Payment
import it.unibo.equishare.domain.repository.ExpensesRepository
import it.unibo.equishare.domain.repository.GroupsRepository
import it.unibo.equishare.domain.repository.PaymentsRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.WeekFields
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class GroupDetailViewModel(
    private val groupsRepository: GroupsRepository,
    private val expensesRepository: ExpensesRepository,
    private val paymentsRepository: PaymentsRepository,
    private val appLanguageManager: it.unibo.equishare.data.local.AppLanguageManager,
) : ViewModel() {

    private val weekFields get() = WeekFields.of(Locale.getDefault())
    private val expenseDateFormatter get() = DateTimeFormatter
        .ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(Locale.getDefault())

    val groupId = MutableStateFlow<String?>(null)
    private val isRefreshing = MutableStateFlow(false)
    private val isRecordingSettlement = MutableStateFlow(false)
    private val settlementFeedback = MutableStateFlow<SettlementFeedback?>(null)
    private val expenseCategories = MutableStateFlow<List<ExpenseCategory>>(emptyList())
    private val groupPayments = MutableStateFlow<List<Payment>>(emptyList())

    init {
        viewModelScope.launch { refreshExpenseCategories() }
        // Reload payments whenever the active group changes
        viewModelScope.launch {
            groupId.collect { id ->
                if (id != null) loadPayments(id)
                else groupPayments.value = emptyList()
            }
        }
    }

    // ── Inner context: group + expenses + members + balance + categories ──────

    private data class GroupContext(
        val group: it.unibo.equishare.domain.model.Group?,
        val expenses: List<Expense>,
        val members: List<GroupMember>,
        val balanceSummary: GroupBalanceSummary,
        val categories: List<ExpenseCategory>,
    )

    private val groupContextFlow = combine(
        groupId.flatMapLatest { id ->
            if (id == null) flowOf(null) else groupsRepository.getById(id)
        },
        groupId.flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else expensesRepository.expensesByGroup(id)
        },
        groupId.flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else groupsRepository.getGroupMembers(id)
        },
        groupId.flatMapLatest { id ->
            if (id == null) {
                flowOf(GroupBalanceSummary.empty())
            } else {
                groupsRepository.getCurrentUserGroupBalances(id)
            }
        },
        expenseCategories,
    ) { group, expenses, members, balanceSummary, categories ->
        GroupContext(group, expenses, members, balanceSummary, categories)
    }

    // ── Merge payments into content ───────────────────────────────────────────

    private val contentState = combine(
        groupContextFlow,
        groupPayments,
        appLanguageManager.languageTag
    ) { ctx, payments, languageTag ->
        val localizedResources = appLanguageManager.resources(languageTag)
        val (group, expenses, members, balanceSummary, categories) = ctx
        if (group == null) {
            GroupDetailUiState(isLoading = true)
        } else {
            val expenseSections = expenses.toSections(members, categories, localizedResources)
            val sectionsWithPayments = mergePaymentsIntoSections(expenseSections, payments, members, localizedResources)
            val total = expenses.fold(Money.zero(group.baseCurrency)) { acc, expense ->
                if (acc.currency == expense.total.currency) acc + expense.total else acc
            }
            GroupDetailUiState(
                groupName = group.name,
                groupDescription = localizedResources.getString(R.string.member_count, group.memberCount),
                groupPhotoUrl = group.avatarUrl,
                balances = balanceSummary.toUiBalances(members),
                totalGroupSpending = total.formatted(),
                totalToReceive = balanceSummary.totalYouAreOwed.formatted(),
                totalYouOwe = balanceSummary.totalYouOwe.formatted(),
                baseCurrencyCode = group.baseCurrency.code,
                expenses = sectionsWithPayments.flatMap(ExpenseSection::items),
                expenseSections = sectionsWithPayments,
            )
        }
    }


    val uiState = combine(
        contentState,
        isRefreshing,
        isRecordingSettlement,
        settlementFeedback,
    ) { state, refreshing, recordingSettlement, feedback ->
        state.copy(
            isRefreshing = refreshing,
            isRecordingSettlement = recordingSettlement,
            settlementFeedback = feedback,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = GroupDetailUiState(isLoading = true),
    )

    fun onEvent(event: GroupDetailEvent) {
        when (event) {
            is GroupDetailEvent.SettleDebtConfirmed ->
                settleDebt(event.memberId, event.amount)
            GroupDetailEvent.SettlementFeedbackConsumed ->
                settlementFeedback.update { null }
            else -> Unit
        }
    }

    fun refresh() {
        if (isRefreshing.value) return
        isRefreshing.update { true }
        viewModelScope.launch {
            groupsRepository.refresh()
            expensesRepository.refresh()
            refreshExpenseCategories()
            groupId.value?.let { loadPayments(it) }
            delay(700)
            isRefreshing.update { false }
        }
    }

    private fun settleDebt(memberId: String, amountInput: String) {
        if (isRecordingSettlement.value) return

        val state = uiState.value
        val balance = state.balances.firstOrNull {
            it.memberId == memberId && it.direction == BalanceDirection.YOU_OWE
        }
        val currency = Currency.fromCode(balance?.currencyCode ?: state.baseCurrencyCode)
        val amount = Money.parse(amountInput, currency)
        val maxAmount = balance?.amountValue
            ?.let { BigDecimal.valueOf(it).setScale(2, RoundingMode.HALF_UP) }

        if (
            balance == null ||
            amount == null ||
            amount.isZero ||
            maxAmount == null ||
            amount.amount > maxAmount
        ) {
            settlementFeedback.update { SettlementFeedback.ERROR }
            return
        }

        isRecordingSettlement.update { true }
        viewModelScope.launch {
            runCatching {
                val id = groupId.value ?: error("Missing group")
                paymentsRepository.pay(
                    groupId = id,
                    toUserId = memberId,
                    amount = amount,
                    currency = amount.currency,
                    paymentDate = LocalDate.now().toString(),
                )
            }.onSuccess {
                groupsRepository.refresh()
                groupId.value?.let { loadPayments(it) }
                settlementFeedback.update { SettlementFeedback.SUCCESS }
            }.onFailure {
                settlementFeedback.update { SettlementFeedback.ERROR }
            }
            isRecordingSettlement.update { false }
        }
    }

    fun setGroupId(id: String) {
        if (groupId.value != id) {
            groupId.value = id
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private suspend fun loadPayments(groupId: String) {
        runCatching { paymentsRepository.paymentsByGroup(groupId) }
            .onSuccess { groupPayments.value = it }
    }

    private suspend fun refreshExpenseCategories() {
        runCatching { expensesRepository.getExpenseCategories() }
            .onSuccess { expenseCategories.value = it }
    }

    private fun mergePaymentsIntoSections(
        sections: List<ExpenseSection>,
        payments: List<Payment>,
        members: List<GroupMember>,
        resources: Resources,
    ): List<ExpenseSection> {
        if (payments.isEmpty()) return sections

        val itemsByKey = linkedMapOf<String, MutableList<ExpenseItem>>()
        val titleByKey = mutableMapOf<String, String>()

        for (section in sections) {
            itemsByKey[section.key] = section.items.toMutableList()
            titleByKey[section.key] = section.title
        }

        for (payment in payments) {
            val bucket = payment.toBucket()
            val item = payment.toSettlementItem(members, resources)
            val key = bucket.name
            itemsByKey.getOrPut(key) { mutableListOf() }.add(item)
            if (!titleByKey.containsKey(key)) {
                titleByKey[key] = bucket.title(resources)
            }
        }

        return ExpenseSectionBucket.values().mapNotNull { bucket ->
            val items = itemsByKey[bucket.name] ?: return@mapNotNull null
            if (items.isEmpty()) return@mapNotNull null
            ExpenseSection(
                key = bucket.name,
                title = titleByKey[bucket.name] ?: bucket.title(resources),
                items = items,
            )
        }
    }

    private fun Payment.toBucket(): ExpenseSectionBucket {
        val today = LocalDate.now()
        val date = runCatching { LocalDate.parse(paymentDate) }.getOrNull()
        return when {
            date == null -> ExpenseSectionBucket.OLDER
            date == today -> ExpenseSectionBucket.TODAY
            date == today.minusDays(1) -> ExpenseSectionBucket.YESTERDAY
            date.isSameWeekAs(today) -> ExpenseSectionBucket.THIS_WEEK
            date.year == today.year && date.month == today.month -> ExpenseSectionBucket.THIS_MONTH
            else -> ExpenseSectionBucket.OLDER
        }
    }

    private fun Payment.toSettlementItem(members: List<GroupMember>, resources: Resources): ExpenseItem {
        val currentUserId = members.firstOrNull { it.isCurrentUser }?.userId
        val fromName = if (fromUserId == currentUserId) {
            resources.getString(R.string.you_label)
        } else {
            members.firstOrNull { it.userId == fromUserId }?.displayName
                ?: fromUserId.take(8)
        }
        val toName = if (toUserId == currentUserId) {
            resources.getString(R.string.you_label)
        } else {
            members.firstOrNull { it.userId == toUserId }?.displayName
                ?: toUserId.take(8)
        }
        val dateFormatted = runCatching { LocalDate.parse(paymentDate) }
            .map { it.format(expenseDateFormatter) }
            .getOrDefault(paymentDate)

        return ExpenseItem(
            id = id,
            title = resources.getString(R.string.settlement_label),
            paidByLabel = resources.getString(R.string.settlement_from_to, fromName, toName),
            dateLabel = dateFormatted,
            amount = amount.formatted(),
            icon = Icons.Default.Payments,
            isSettlement = true,
        )
    }

    private fun GroupBalanceSummary.toUiBalances(members: List<GroupMember>): List<MemberBalance> =
        memberBalances
            .filter { !it.balance.isZero }
            .sortedWith(
                compareBy<GroupMemberBalance> { it.balance.isPositive }
                    .thenByDescending { it.balance.amount.abs() },
            )
            .map { it.toUiBalance(members) }

    private fun GroupMemberBalance.toUiBalance(members: List<GroupMember>): MemberBalance {
        val member = members.firstOrNull { it.userId == memberId }
        val displayName = member?.displayName
            ?.takeIf { it.isNotBlank() }
            ?: member?.email?.substringBefore("@")?.takeIf { it.isNotBlank() }
            ?: memberId.take(8)
        val absoluteBalance = balance.abs()

        return MemberBalance(
            memberId = memberId,
            memberName = displayName,
            memberAvatarUrl = member?.avatarUrl,
            direction = if (balance.isPositive) BalanceDirection.OWED_TO_YOU else BalanceDirection.YOU_OWE,
            amount = absoluteBalance.formatted(),
            amountValue = absoluteBalance.toDouble(),
            currencyCode = absoluteBalance.currency.code,
        )
    }

    private fun Expense.toUi(
        members: List<GroupMember>,
        categories: List<ExpenseCategory>,
        resources: Resources,
    ): ExpenseItem = ExpenseItem(
        id = id,
        title = title,
        paidByLabel = resources.getString(
            R.string.expense_paid_by_label,
            payerNames(members, resources),
        ),
        dateLabel = dateLabel(),
        amount = total.formatted(),
        icon = categoryIcon(categories),
    )

    private fun List<Expense>.toSections(
        members: List<GroupMember>,
        categories: List<ExpenseCategory>,
        resources: Resources,
    ): List<ExpenseSection> {
        val buckets = linkedMapOf<ExpenseSectionBucket, MutableList<Expense>>()

        forEach { expense ->
            val bucket = expense.toSectionBucket()
            buckets.getOrPut(bucket) { mutableListOf() }.add(expense)
        }

        return buckets.map { (bucket, items) ->
            val sortedItems = items.sortedByDescending { it.sortTimestamp() }
            ExpenseSection(
                key = bucket.name,
                title = bucket.title(resources),
                items = sortedItems.map { it.toUi(members, categories, resources) },
            )
        }
    }

    private fun Expense.sortTimestamp(): OffsetDateTime {
        val created = createdAt?.let(::parseCreatedAtOrNull)
        if (created != null) return created
        val expenseDay = parsedExpenseDate()
        return expenseDay?.atStartOfDay()?.atOffset(ZoneOffset.UTC) ?: OffsetDateTime.MIN
    }

    private fun parseCreatedAtOrNull(raw: String): OffsetDateTime? =
        runCatching { OffsetDateTime.parse(raw) }
            .recoverCatching { OffsetDateTime.parse(raw + "Z") }
            .getOrNull()

    private fun Expense.toSectionBucket(): ExpenseSectionBucket {
        val today = LocalDate.now()
        val date = parsedExpenseDate()

        return when {
            date == null -> ExpenseSectionBucket.OLDER
            date == today -> ExpenseSectionBucket.TODAY
            date == today.minusDays(1) -> ExpenseSectionBucket.YESTERDAY
            date.isSameWeekAs(today) -> ExpenseSectionBucket.THIS_WEEK
            date.year == today.year && date.month == today.month -> ExpenseSectionBucket.THIS_MONTH
            else -> ExpenseSectionBucket.OLDER
        }
    }

    private fun Expense.dateLabel(): String =
        parsedExpenseDate()?.format(expenseDateFormatter).orEmpty().ifBlank { expenseDate }

    private fun Expense.parsedExpenseDate(): LocalDate? =
        runCatching { LocalDate.parse(expenseDate) }.getOrNull()

    private fun LocalDate.isSameWeekAs(other: LocalDate): Boolean =
        get(weekFields.weekBasedYear()) == other.get(weekFields.weekBasedYear()) &&
            get(weekFields.weekOfWeekBasedYear()) == other.get(weekFields.weekOfWeekBasedYear())

    private fun Expense.payerNames(members: List<GroupMember>, resources: Resources): String {
        val payerIds = payerUserIds.ifEmpty { listOf(paidByUserId) }
            .filter { it.isNotBlank() }
        val names = payerIds.map { id ->
            val member = members.firstOrNull { it.userId == id }
            when {
                member?.isCurrentUser == true -> resources.getString(R.string.you_label)
                member != null -> member.displayName
                else -> resources.getString(R.string.expense_unknown_payer)
            }
        }.distinct()

        return when {
            names.isEmpty() -> resources.getString(R.string.expense_unknown_payer)
            names.size <= 2 -> names.joinToString(", ")
            else -> resources.getString(
                R.string.expense_payers_compact,
                names.take(2).joinToString(", "),
                names.size - 2,
            )
        }
    }

    private fun Expense.categoryIcon(categories: List<ExpenseCategory>): ImageVector {
        val category = categories.firstOrNull { it.id == categoryId }
        val key = listOfNotNull(
            category?.iconKey,
            category?.code,
            category?.name,
        )
            .plus(category?.translations?.values.orEmpty())
            .joinToString(" ")
            .lowercase(Locale.getDefault())

        return when {
            key.contains("restaurant") || key.contains("food") || key.contains("cibo") ->
                Icons.Default.Restaurant
            key.contains("shopping") || key.contains("cart") ->
                Icons.Default.ShoppingCart
            key.contains("home") || key.contains("casa") ->
                Icons.Default.Home
            key.contains("flight") || key.contains("travel") || key.contains("trip") ||
                key.contains("viaggio") || key.contains("vacanza") ->
                Icons.Default.Flight
            key.contains("gift") || key.contains("regal") ->
                Icons.Default.CardGiftcard
            key.contains("party") || key.contains("celebration") || key.contains("fun") ||
                key.contains("festa") || key.contains("divertimento") ->
                Icons.Default.Celebration
            key.contains("school") || key.contains("scuola") ->
                Icons.Default.School
            key.contains("sport") ->
                Icons.Default.Sports
            key.contains("work") || key.contains("lavoro") ->
                Icons.Default.Work
            key.contains("money") || key.contains("attach_money") ->
                Icons.Default.AttachMoney
            else ->
                Icons.Default.Receipt
        }
    }
}

private enum class ExpenseSectionBucket {
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
