/** Defines the Expense domain model. */
package it.unibo.equishare.domain.model

/**
 * Domain entity for an expense. Repositories return this; the UI layer maps it
 * into a screen-specific UI state.
 */
data class Expense(
    val id: String,
    val groupId: String,
    val title: String,
    val description: String?,
    val categoryId: String?,
    val expenseDate: String,
    val total: Money,
    val paidByUserId: String,
    val payerUserIds: List<String> = emptyList(),
    val splitMethod: SplitMethod,
    val status: ExpenseStatus,
    val receiptUrl: String?,
    val createdBy: String?,
    val createdAt: String?,
)

/** One participant row when creating a new expense. */
data class ExpenseParticipant(
    val userId: String,
    val paid: Money,
    val owed: Money,
)

/** Category of an expense (separate from group category). */
data class ExpenseCategory(
    val id: String,
    val name: String,
    val code: String = "",
    val iconKey: String? = null,
    val translations: Map<String, String> = emptyMap(),
) {
    fun localizedName(language: String): String =
        translations[language]?.takeIf { it.isNotBlank() }
            ?: translations.values.firstOrNull { it.isNotBlank() }
            ?: name
}
