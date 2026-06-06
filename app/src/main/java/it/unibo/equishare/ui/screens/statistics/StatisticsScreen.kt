/** Renders the statistics screen UI. */
package it.unibo.equishare.ui.screens.statistics

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Sports
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.ehsannarmani.compose_charts.PieChart
import ir.ehsannarmani.compose_charts.models.LabelHelperProperties
import ir.ehsannarmani.compose_charts.models.Pie
import it.unibo.equishare.R
import it.unibo.equishare.ui.components.animations.EquiMotion
import it.unibo.equishare.ui.components.animations.animateListItemEntry
import it.unibo.equishare.ui.components.refresh.EquiSharePullToRefresh
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

private val StatisticsPalette = listOf(
    Color(0xFF006E1C),
    Color(0xFF006A6A),
    Color(0xFF855400),
    Color(0xFF6F43B5),
    Color(0xFFBA1A1A),
    Color(0xFF00658C),
    Color(0xFF5D6B00),
    Color(0xFF8C4A60),
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun StatisticsContent(
    uiState: StatisticsUiState,
    onEvent: (StatisticsEvent) -> Unit,
    modifier: Modifier = Modifier,
    onRefresh: () -> Unit = {},
) {
    val locale = LocalConfiguration.current.locales[0]
    val monthOptions = remember(locale) {
        Month.entries.map { month ->
            StatisticsMonthOption(
                value = month.value,
                label = month.getDisplayName(TextStyle.FULL, locale)
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() },
            )
        }
    }
    val selectedMonthLabel = monthOptions
        .firstOrNull { it.value == uiState.selectedMonth }
        ?.label
        ?: uiState.selectedMonthLabel
    val totalLabel = if (uiState.isMixedCurrencyTotal) {
        stringResource(R.string.statistics_mixed_currencies)
    } else {
        uiState.totalLabel
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { innerPadding ->
        Crossfade(
            targetState = uiState.isLoading,
            animationSpec = tween(durationMillis = 280, easing = EquiMotion.EmphasizedStandard),
            label = "statisticsLoadingCrossfade",
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
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
                            .verticalScroll(rememberScrollState())
                            .padding(
                                start = 16.dp,
                                top = 8.dp,
                                end = 16.dp,
                                bottom = 16.dp,
                            ),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.statistics_title),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.animateListItemEntry(index = 0),
                        )

                        PeriodFilterRow(
                            uiState = uiState,
                            selectedMonthLabel = selectedMonthLabel,
                            monthOptions = monthOptions,
                            onEvent = onEvent,
                            modifier = Modifier.animateListItemEntry(index = 1),
                        )

                        StatisticsPieCard(
                            slices = uiState.slices,
                            totalLabel = totalLabel,
                            modifier = Modifier.animateListItemEntry(index = 2),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PeriodFilterRow(
    uiState: StatisticsUiState,
    selectedMonthLabel: String,
    monthOptions: List<StatisticsMonthOption>,
    onEvent: (StatisticsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MonthDropdown(
            selectedLabel = selectedMonthLabel,
            options = monthOptions,
            onSelected = { onEvent(StatisticsEvent.MonthSelected(it)) },
            modifier = Modifier.weight(1f),
        )
        YearDropdown(
            selectedYear = uiState.selectedYear,
            options = uiState.yearOptions,
            onSelected = { onEvent(StatisticsEvent.YearSelected(it)) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MonthDropdown(
    selectedLabel: String,
    options: List<StatisticsMonthOption>,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = selectedLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        expanded = false
                        onSelected(option.value)
                    },
                )
            }
        }
    }
}

@Composable
private fun YearDropdown(
    selectedYear: Int,
    options: List<Int>,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = selectedYear.toString(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { year ->
                DropdownMenuItem(
                    text = { Text(year.toString()) },
                    onClick = {
                        expanded = false
                        onSelected(year)
                    },
                )
            }
        }
    }
}

@Composable
private fun StatisticsPieCard(
    slices: List<StatisticsSlice>,
    totalLabel: String,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val fallbackCategoryLabels = localizedFallbackCategoryLabels()
    val uncategorizedLabel = stringResource(R.string.statistics_uncategorized)
    val localizedSlices = slices.map { slice ->
        val resolvedName = (slice.localizedCategoryName?.takeIf { it.isNotBlank() }
            ?: slice.categoryCode?.lowercase(Locale.ROOT)?.let { fallbackCategoryLabels[it] }
            ?: slice.categoryName.takeIf { it.isNotBlank() }
            ?: uncategorizedLabel) + slice.currencySuffix
        slice.copy(categoryName = resolvedName)
    }
    val hasData = localizedSlices.isNotEmpty()
    val chartData = remember(localizedSlices, hasData, colorScheme.outlineVariant) {
        if (!hasData) {
            listOf(
                Pie(
                    label = "",
                    data = 1.0,
                    color = colorScheme.outlineVariant,
                    selectedColor = colorScheme.outlineVariant,
                    selected = false,
                )
            )
        } else {
            localizedSlices.mapIndexed { index, slice ->
                val color = StatisticsPalette[index % StatisticsPalette.size]
                Pie(
                    label = slice.categoryName,
                    data = slice.amount,
                    color = color,
                    selectedColor = color,
                    selected = false,
                )
            }
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        border = BorderStroke(0.5.dp, colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                val chartSize = maxWidth.coerceAtMost(306.dp)
                val pieSize = chartSize - 12.dp
                val centerSize = chartSize * 0.52f

                Box(
                    modifier = Modifier.size(chartSize),
                    contentAlignment = Alignment.Center,
                ) {
                    PieChart(
                        modifier = Modifier.size(pieSize),
                        data = chartData,
                        selectedScale = 1f,
                        spaceDegree = if (hasData) 3f else 0f,
                        style = Pie.Style.Stroke(width = 26.dp),
                        labelHelperProperties = LabelHelperProperties(enabled = false),
                    )
                    Box(
                        modifier = Modifier
                            .size(centerSize)
                            .clip(CircleShape)
                            .background(colorScheme.surface),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(horizontal = 10.dp),
                        ) {
                            Text(
                                text = totalLabel,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (hasData) colorScheme.onSurface else colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = if (hasData) {
                                    stringResource(R.string.statistics_expenses_label)
                                } else {
                                    stringResource(R.string.statistics_no_expenses)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }

            if (hasData) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    localizedSlices.forEachIndexed { index, slice ->
                        if (index > 0) {
                            HorizontalDivider(color = colorScheme.outlineVariant)
                        }
                        CategoryBreakdownRow(
                            slice = slice,
                            color = StatisticsPalette[index % StatisticsPalette.size],
                        )
                    }
                }
            } else {
                EmptyStatisticsHint()
            }
        }
    }
}

@Composable
private fun CategoryBreakdownRow(
    slice: StatisticsSlice,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = iconForCategoryCode(slice.categoryCode),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = slice.categoryName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = slice.amountLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurface,
                    maxLines = 1,
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(colorScheme.outlineVariant.copy(alpha = 0.6f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(slice.fraction.coerceIn(0f, 1f))
                        .height(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(color),
                )
            }
        }
    }
}

@Composable
private fun localizedFallbackCategoryLabels(): Map<String, String> = mapOf(
    "food" to stringResource(R.string.expense_category_food),
    "restaurant" to stringResource(R.string.expense_category_food),
    "restaurants" to stringResource(R.string.expense_category_food),
    "dining" to stringResource(R.string.expense_category_food),
    "shopping" to stringResource(R.string.expense_category_shopping),
    "shop" to stringResource(R.string.expense_category_shopping),
    "home" to stringResource(R.string.expense_category_home),
    "house" to stringResource(R.string.expense_category_home),
    "housing" to stringResource(R.string.expense_category_home),
    "travel" to stringResource(R.string.expense_category_travel),
    "flight" to stringResource(R.string.expense_category_travel),
    "gifts" to stringResource(R.string.expense_category_gifts),
    "gift" to stringResource(R.string.expense_category_gifts),
    "party" to stringResource(R.string.expense_category_entertainment),
    "fun" to stringResource(R.string.expense_category_entertainment),
    "entertainment" to stringResource(R.string.expense_category_entertainment),
    "celebration" to stringResource(R.string.expense_category_entertainment),
    "school" to stringResource(R.string.expense_category_school),
    "education" to stringResource(R.string.expense_category_school),
    "sports" to stringResource(R.string.expense_category_sports),
    "sport" to stringResource(R.string.expense_category_sports),
    "work" to stringResource(R.string.expense_category_work),
    "business" to stringResource(R.string.expense_category_work),
    "money" to stringResource(R.string.expense_category_other),
    "other" to stringResource(R.string.expense_category_other),
    "general" to stringResource(R.string.expense_category_other),
    "generic" to stringResource(R.string.expense_category_other),
)

private fun iconForCategoryCode(code: String?): ImageVector =
    when (code?.lowercase(Locale.ROOT)) {
        "food", "restaurant", "restaurants", "dining" -> Icons.Default.Restaurant
        "shopping", "shop" -> Icons.Default.ShoppingCart
        "home", "house", "housing" -> Icons.Default.Home
        "travel", "trip", "flight", "transport", "transportation" -> Icons.Default.Flight
        "gifts", "gift" -> Icons.Default.CardGiftcard
        "party", "fun", "entertainment", "celebration" -> Icons.Default.Celebration
        "school", "education" -> Icons.Default.School
        "sports", "sport" -> Icons.Default.Sports
        "work", "business" -> Icons.Default.Work
        "money", "other", "general", "generic" -> Icons.Default.AttachMoney
        else -> Icons.Outlined.PieChart
    }

@Composable
private fun EmptyStatisticsHint(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.PieChart,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.statistics_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
