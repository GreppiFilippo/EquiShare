/** Defines Supabase Statistics Data Source app code. */
package it.unibo.equishare.data.remote.datasource

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import it.unibo.equishare.data.remote.dto.ExpenseCategoryDto
import it.unibo.equishare.data.remote.dto.ExpenseParticipantDto
import it.unibo.equishare.domain.model.ExpenseStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class SupabaseStatisticsDataSource(private val client: SupabaseClient) {

    suspend fun fetchParticipantsForUser(userId: String): List<ExpenseParticipantDto> =
        client.postgrest.from("expense_participants")
            .select(Columns.list("expense_id, user_id, paid_amount, owed_amount")) {
                filter { eq("user_id", userId) }
            }
            .decodeList()

    suspend fun fetchExpenses(expenseIds: List<String>): List<StatisticsExpenseRow> {
        if (expenseIds.isEmpty()) return emptyList()
        return client.postgrest.from("expenses")
            .select(Columns.list("id, category_id, expense_date, currency, status, deleted_at")) {
                filter {
                    isIn("id", expenseIds)
                    eq("status", ExpenseStatus.POSTED.dbValue)
                }
            }
            .decodeList()
    }

    suspend fun fetchExpenseCategories(): List<ExpenseCategoryDto> =
        client.postgrest.from("expense_categories")
            .select { order("sort_order", Order.ASCENDING) }
            .decodeList()
}

@Serializable
data class StatisticsExpenseRow(
    val id: String,
    @SerialName("category_id")  val categoryId: String? = null,
    @SerialName("expense_date") val expenseDate: String,
    val currency: String,
    val status: String,
    @SerialName("deleted_at")   val deletedAt: String? = null,
)
