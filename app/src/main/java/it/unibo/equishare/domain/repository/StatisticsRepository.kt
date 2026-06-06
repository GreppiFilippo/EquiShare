/** Defines the Statistics repository contract. */
package it.unibo.equishare.domain.repository

import it.unibo.equishare.domain.model.CategorySpending
import kotlinx.coroutines.flow.Flow

interface StatisticsRepository {
    fun categorySpending(year: Int, month: Int): Flow<List<CategorySpending>>

    fun refresh()
}
