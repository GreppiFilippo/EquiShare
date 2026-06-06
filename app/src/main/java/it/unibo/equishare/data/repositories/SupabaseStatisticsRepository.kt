/** Implements the Statistics repository using Supabase and local data. */
package it.unibo.equishare.data.repositories

import it.unibo.equishare.data.remote.datasource.SupabaseStatisticsDataSource
import it.unibo.equishare.domain.model.CategorySpending
import it.unibo.equishare.domain.model.Currency
import it.unibo.equishare.domain.model.LanguageCode
import it.unibo.equishare.domain.model.Money
import it.unibo.equishare.domain.repository.AuthRepository
import it.unibo.equishare.domain.repository.StatisticsRepository
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow

class SupabaseStatisticsRepository(
    private val remote: SupabaseStatisticsDataSource,
    private val auth: AuthRepository,
) : RefreshableRepository(), StatisticsRepository {

    init {
        watchAuth(auth.isSignedIn)
    }

    override fun categorySpending(year: Int, month: Int): Flow<List<CategorySpending>> = refreshable {
        val currentUserId = currentUserIdOrWait() ?: return@refreshable emptyList()
        val targetMonth = YearMonth.of(year, month)

        try {
            loadCategorySpending(currentUserId, targetMonth)
        } catch (t: Throwable) {
            t.rethrowIfCancellation()
            delay(500)
            try {
                loadCategorySpending(currentUserId, targetMonth)
            } catch (retryError: Throwable) {
                retryError.rethrowIfCancellation()
                emptyList()
            }
        }
    }

    private suspend fun currentUserIdOrWait(): String? {
        repeat(20) {
            auth.currentUserId?.let { return it }
            delay(100)
        }
        return auth.currentUserId
    }

    private suspend fun loadCategorySpending(
        currentUserId: String,
        targetMonth: YearMonth,
    ): List<CategorySpending> {
        val participants = remote.fetchParticipantsForUser(currentUserId)
            .filter { it.owedAmount > 0.0 }

        val expenseIds = participants.map { it.expenseId }.distinct()
        if (expenseIds.isEmpty()) return emptyList()

        val participantByExpenseId = participants.associateBy { it.expenseId }
        val expenses = remote.fetchExpenses(expenseIds)
            .filter { row ->
                row.deletedAt == null &&
                    row.expenseDate.toYearMonthOrNull() == targetMonth
            }

        if (expenses.isEmpty()) return emptyList()

        val categoriesById = remote.fetchExpenseCategories()
            .associateBy { it.id }

        return expenses
            .groupBy { CategoryCurrencyKey(it.categoryId, Currency.fromCode(it.currency)) }
            .map { (key, rows) ->
                val amount = rows.fold(BigDecimal.ZERO) { acc, expense ->
                    acc + BigDecimal.valueOf(participantByExpenseId[expense.id]?.owedAmount ?: 0.0)
                }
                val category = key.categoryId?.let { categoriesById[it] }
                CategorySpending(
                    categoryId = key.categoryId,
                    categoryCode = category?.code,
                    categoryName = category?.name,
                    amount = Money.of(amount, key.currency),
                    translations = buildTranslationMap(category?.nameIt, category?.nameEn),
                )
            }
            .filter { it.amount.isPositive }
            .sortedByDescending { it.amount.amount }
    }
}

private fun buildTranslationMap(it: String?, en: String?): Map<String, String> = buildMap {
    if (!it.isNullOrBlank()) put(LanguageCode.IT, it)
    if (!en.isNullOrBlank()) put(LanguageCode.EN, en)
}

private fun String.toYearMonthOrNull(): YearMonth? =
    runCatching { YearMonth.from(LocalDate.parse(this)) }.getOrNull()

private data class CategoryCurrencyKey(
    val categoryId: String?,
    val currency: Currency,
)
