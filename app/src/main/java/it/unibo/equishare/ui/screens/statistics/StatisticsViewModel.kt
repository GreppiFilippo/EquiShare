/** Manages state for the statistics screen. */
package it.unibo.equishare.ui.screens.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.unibo.equishare.domain.model.CategorySpending
import it.unibo.equishare.domain.model.Money
import it.unibo.equishare.domain.repository.StatisticsRepository
import java.time.Month
import java.time.YearMonth
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StatisticsMonthOption(
    val value: Int,
    val label: String,
)

data class StatisticsSlice(
    val categoryCode: String?,
    val categoryName: String,
    val localizedCategoryName: String?,
    val currencySuffix: String,
    val amount: Double,
    val amountLabel: String,
    val percentageLabel: String,
    val fraction: Float,
)

data class StatisticsUiState(
    val selectedMonth: Int = YearMonth.now().monthValue,
    val selectedMonthLabel: String = "",
    val selectedYear: Int = YearMonth.now().year,
    val monthOptions: List<StatisticsMonthOption> = emptyList(),
    val yearOptions: List<Int> = emptyList(),
    val slices: List<StatisticsSlice> = emptyList(),
    val totalLabel: String = "",
    val isMixedCurrencyTotal: Boolean = false,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
)

sealed interface StatisticsEvent {
    data class MonthSelected(val month: Int) : StatisticsEvent
    data class YearSelected(val year: Int) : StatisticsEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
class StatisticsViewModel(
    private val statisticsRepository: StatisticsRepository,
    private val appLanguageManager: it.unibo.equishare.data.local.AppLanguageManager,
) : ViewModel() {

    private val initialMonth = YearMonth.now()
    private val selectedMonth = MutableStateFlow(initialMonth.monthValue)
    private val selectedYear = MutableStateFlow(initialMonth.year)
    private val isRefreshing = MutableStateFlow(false)

    private val monthOptions = Month.entries.map { month ->
        StatisticsMonthOption(
            value = month.value,
            label = month.value.toString(),
        )
    }
    private val yearOptions = (initialMonth.year downTo initialMonth.year - 10).toList()

    private val categoryStats = combine(selectedYear, selectedMonth) { year, month ->
        year to month
    }.flatMapLatest { (year, month) ->
        statisticsRepository.categorySpending(year, month)
    }

    private val contentState = combine(
        categoryStats,
        selectedMonth,
        selectedYear,
        appLanguageManager.languageTag,
    ) { stats: List<CategorySpending>, month: Int, year: Int, languageTag ->
        val language = languageTag?.let { Locale.forLanguageTag(it).language }
            ?: Locale.getDefault().language
        StatisticsUiState(
            selectedMonth = month,
            selectedMonthLabel = monthOptions.first { it.value == month }.label,
            selectedYear = year,
            monthOptions = monthOptions,
            yearOptions = yearOptions,
            slices = stats.toSlices(language),
            totalLabel = stats.totalLabel(),
            isMixedCurrencyTotal = stats.hasMixedCurrencies(),
        )
    }

    val uiState = combine(contentState, isRefreshing) { state, refreshing ->
        state.copy(isRefreshing = refreshing)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StatisticsUiState(
            selectedMonth = initialMonth.monthValue,
            selectedYear = initialMonth.year,
            monthOptions = monthOptions,
            yearOptions = yearOptions,
            selectedMonthLabel = monthOptions.first { it.value == initialMonth.monthValue }.label,
            totalLabel = Money.zero().formatted(),
            isLoading = true,
        ),
    )

    fun onEvent(event: StatisticsEvent) {
        when (event) {
            is StatisticsEvent.MonthSelected -> selectedMonth.value = event.month
            is StatisticsEvent.YearSelected -> selectedYear.value = event.year
        }
    }

    fun refresh() {
        if (isRefreshing.value) return
        isRefreshing.update { true }
        viewModelScope.launch {
            statisticsRepository.refresh()
            delay(700)
            isRefreshing.update { false }
        }
    }

    fun refreshSilently() {
        statisticsRepository.refresh()
    }

    private fun List<CategorySpending>.toSlices(language: String): List<StatisticsSlice> {
        val total = sumOf { it.amount.toDouble() }
        if (total <= 0.0) return emptyList()

        val hasMultipleCurrencies = map { it.amount.currency }.distinct().size > 1
        return map { spending ->
            val amountValue = spending.amount.toDouble()
            val currencySuffix = if (hasMultipleCurrencies) " (${spending.amount.currency.code})" else ""
            StatisticsSlice(
                categoryCode = spending.categoryCode,
                categoryName = spending.fallbackCategoryName() + currencySuffix,
                localizedCategoryName = spending.localizedName(language),
                currencySuffix = currencySuffix,
                amount = amountValue,
                amountLabel = spending.amount.formatted(),
                percentageLabel = String.format(Locale.ROOT, "%.0f%%", amountValue / total * 100),
                fraction = (amountValue / total).toFloat().coerceIn(0f, 1f),
            )
        }
    }

    private fun List<CategorySpending>.totalLabel(): String {
        if (isEmpty()) return Money.zero().formatted()
        val currency = first().amount.currency
        if (any { it.amount.currency != currency }) return ""
        return fold(Money.zero(currency)) { acc, spending -> acc + spending.amount }.formatted()
    }

    private fun List<CategorySpending>.hasMixedCurrencies(): Boolean =
        isNotEmpty() && any { it.amount.currency != first().amount.currency }

    private fun CategorySpending.fallbackCategoryName(): String =
        translations.values.firstOrNull { it.isNotBlank() }
            ?: categoryName?.takeIf { it.isNotBlank() }
            ?: categoryCode?.takeIf { it.isNotBlank() }
            ?: ""
}
