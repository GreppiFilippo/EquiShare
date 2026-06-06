/** Implements the Add Expense use case. */
package it.unibo.equishare.domain.usecase

import it.unibo.equishare.domain.model.ExpenseParticipant
import it.unibo.equishare.domain.model.ImageUpload
import it.unibo.equishare.domain.model.Money
import it.unibo.equishare.domain.model.SplitMethod
import it.unibo.equishare.domain.repository.AuthRepository
import it.unibo.equishare.domain.repository.ExpensesRepository
import java.time.LocalDate

class AddExpenseUseCase(
    private val expenses: ExpensesRepository,
    private val auth: AuthRepository,
) {
    data class Input(
        val groupId: String,
        val title: String,
        val total: Money,
        val expenseDate: String = LocalDate.now().toString(),
        val categoryId: String? = null,
        val payerIds: List<String>,
        val sharerIds: List<String>,
        val splitMethod: SplitMethod = SplitMethod.EQUAL,
        val preferredPrimaryPayerId: String? = null,
        val receipt: ImageUpload? = null,
        val editExpenseId: String? = null,
    )

    suspend operator fun invoke(input: Input): Result<Unit> = runCatching {
        require(input.payerIds.isNotEmpty())  { "At least one payer is required" }
        require(input.sharerIds.isNotEmpty()) { "At least one sharer is required" }
        require(!input.total.isZero && !input.total.isNegative) { "Total must be positive" }
        require(input.title.isNotBlank())     { "Description must not be empty" }
        checkNotNull(auth.currentUserId)      { "Not signed in" }

        val paid = input.total.splitEqually(input.payerIds)
        val owed = input.total.splitEqually(input.sharerIds)
        val zero = Money.zero(input.total.currency)

        val allUserIds = (input.payerIds + input.sharerIds).distinct()
        val participants = allUserIds.map { uid ->
            ExpenseParticipant(
                userId = uid,
                paid = paid[uid] ?: zero,
                owed = owed[uid] ?: zero,
            )
        }

        val primary = input.preferredPrimaryPayerId
            ?.takeIf { it in input.payerIds }
            ?: input.payerIds.first()

        if (input.editExpenseId != null) {
            expenses.updateExpense(
                expenseId = input.editExpenseId,
                title = input.title.trim(),
                total = input.total,
                currency = input.total.currency,
                expenseDate = input.expenseDate,
                categoryId = input.categoryId,
                splitMethod = input.splitMethod,
                primaryPayerId = primary,
                participants = participants,
                receipt = input.receipt,
            )
        } else {
            expenses.addExpense(
                groupId = input.groupId,
                title = input.title.trim(),
                total = input.total,
                currency = input.total.currency,
                expenseDate = input.expenseDate,
                categoryId = input.categoryId,
                splitMethod = input.splitMethod,
                primaryPayerId = primary,
                participants = participants,
                receipt = input.receipt,
            )
        }
    }
}
