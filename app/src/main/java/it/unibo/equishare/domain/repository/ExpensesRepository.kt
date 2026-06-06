/** Defines the Expenses repository contract. */
package it.unibo.equishare.domain.repository

import it.unibo.equishare.domain.model.Currency
import it.unibo.equishare.domain.model.Expense
import it.unibo.equishare.domain.model.ExpenseCategory
import it.unibo.equishare.domain.model.ExpenseParticipant
import it.unibo.equishare.domain.model.ImageUpload
import it.unibo.equishare.domain.model.Money
import it.unibo.equishare.domain.model.SplitMethod
import kotlinx.coroutines.flow.Flow

interface ExpensesRepository {
    fun expensesByGroup(groupId: String): Flow<List<Expense>>
    fun getById(id: String): Flow<Expense?>

    suspend fun getParticipants(expenseId: String): List<ExpenseParticipant>

    suspend fun getExpenseCategories(): List<ExpenseCategory>

    suspend fun addExpense(
        groupId: String,
        title: String,
        total: Money,
        currency: Currency,
        expenseDate: String,
        categoryId: String? = null,
        splitMethod: SplitMethod = SplitMethod.EQUAL,
        primaryPayerId: String,
        participants: List<ExpenseParticipant>,
        receipt: ImageUpload? = null,
    )

    suspend fun updateExpense(
        expenseId: String,
        title: String,
        total: Money,
        currency: Currency,
        expenseDate: String,
        categoryId: String? = null,
        splitMethod: SplitMethod = SplitMethod.EQUAL,
        primaryPayerId: String,
        participants: List<ExpenseParticipant>,
        receipt: ImageUpload? = null,
    )

    suspend fun softDelete(id: String)

    fun refresh()
}
