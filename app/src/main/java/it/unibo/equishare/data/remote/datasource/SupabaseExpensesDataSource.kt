/** Defines Supabase Expenses Data Source app code. */
package it.unibo.equishare.data.remote.datasource

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import it.unibo.equishare.data.remote.dto.ExpenseCategoryDto
import it.unibo.equishare.data.remote.dto.ExpenseDto
import it.unibo.equishare.data.remote.dto.ExpenseIdDto
import it.unibo.equishare.data.remote.dto.ExpenseParticipantDto
import it.unibo.equishare.domain.model.ExpenseStatus
import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class SupabaseExpensesDataSource(private val client: SupabaseClient) {

    suspend fun fetchExpensesByGroup(groupId: String): List<ExpenseDto> =
        client.postgrest.from("expenses")
            .select {
                filter {
                    eq("group_id", groupId)
                    eq("status", ExpenseStatus.POSTED.dbValue)
                }
                order("expense_date", Order.DESCENDING)
            }
            .decodeList()

    suspend fun fetchExpenseById(id: String): ExpenseDto? =
        client.postgrest.from("expenses")
            .select { filter { eq("id", id) } }
            .decodeSingleOrNull()

    suspend fun fetchParticipants(expenseId: String): List<ExpenseParticipantDto> =
        client.postgrest.from("expense_participants")
            .select { filter { eq("expense_id", expenseId) } }
            .decodeList()

    suspend fun fetchPayerIds(expenseIds: List<String>): Map<String, List<String>> {
        if (expenseIds.isEmpty()) return emptyMap()
        return client.postgrest.from("expense_participants")
            .select(Columns.list("expense_id, user_id, paid_amount, owed_amount")) {
                filter { isIn("expense_id", expenseIds) }
            }
            .decodeList<ExpenseParticipantDto>()
            .filter { it.paidAmount > 0.0 }
            .groupBy { it.expenseId }
            .mapValues { (_, rows) -> rows.map { it.userId }.distinct() }
    }

    suspend fun fetchExpenseCategories(): List<ExpenseCategoryDto> =
        client.postgrest.from("expense_categories")
            .select { order("sort_order", Order.ASCENDING) }
            .decodeList<ExpenseCategoryDto>()
            .filter { it.isActive }

    suspend fun insertExpense(dto: ExpenseDto): ExpenseIdDto =
        client.postgrest.from("expenses")
            .insert(dto) { select(Columns.list("id")) }
            .decodeSingle()

    suspend fun insertParticipants(participants: List<ExpenseParticipantDto>) {
        client.postgrest.from("expense_participants").insert(participants)
    }

    suspend fun updateExpenseStatus(expenseId: String, status: String, receiptUrl: String? = null) {
        client.postgrest.from("expenses").update({
            receiptUrl?.let { set("receipt_url", it) }
            set("status", status)
        }) { filter { eq("id", expenseId) } }
    }

    suspend fun fetchExistingParticipants(expenseId: String): List<ExpenseParticipantDto> =
        client.postgrest.from("expense_participants")
            .select { filter { eq("expense_id", expenseId) } }
            .decodeList()

    suspend fun deleteParticipants(ids: List<String>) {
        if (ids.isEmpty()) return
        client.postgrest.from("expense_participants")
            .delete { filter { isIn("id", ids) } }
    }

    suspend fun updateParticipant(id: String, paidAmount: Double, owedAmount: Double) {
        client.postgrest.from("expense_participants").update({
            set("paid_amount", paidAmount)
            set("owed_amount", owedAmount)
            // Explicit Double? so the compiler picks the numeric overload of `set`.
            set("exact_amount", null as Double?)
            set("percentage", null as Double?)
            set("shares", null as Double?)
        }) { filter { eq("id", id) } }
    }

    suspend fun updateExpenseFields(
        expenseId: String,
        title: String,
        categoryId: String?,
        expenseDate: String,
        currencyCode: String,
        totalAmount: Double,
        primaryPayerId: String,
        splitMethod: String,
        receiptUrl: String?,
    ) {
        client.postgrest.from("expenses").update({
            set("title", title)
            set("category_id", categoryId)
            set("expense_date", expenseDate)
            set("currency", currencyCode)
            set("total_amount", totalAmount)
            set("paid_by_user_id", primaryPayerId)
            set("split_method", splitMethod)
            receiptUrl?.let { set("receipt_url", it) }
            set("status", ExpenseStatus.POSTED.dbValue)
        }) { filter { eq("id", expenseId) } }
    }

    suspend fun softDeleteExpense(id: String) {
        client.postgrest.from("expenses").update({
            set("deleted_at", Instant.now().toString())
            set("status", ExpenseStatus.CANCELLED.dbValue)
        }) { filter { eq("id", id) } }
    }

    suspend fun fetchParticipantAccessRow(expenseId: String, userId: String): Boolean =
        client.postgrest.from("expense_participants")
            .select(Columns.list("expense_id")) {
                filter {
                    eq("expense_id", expenseId)
                    eq("user_id", userId)
                    gt("paid_amount", 0.0)
                }
                limit(1)
            }
            .decodeList<ExpenseParticipantAccessRow>()
            .isNotEmpty()

    suspend fun uploadReceipt(ownerUserId: String, expenseId: String, bytes: ByteArray, mimeType: String): String {
        val extension = storageExtensionForMime(mimeType)
        val path = "$ownerUserId/expenses/$expenseId/receipt.$extension"
        val bucket = client.storage.from(EXPENSE_FILES_BUCKET)
        bucket.upload(path = path, data = bytes) { upsert = true }
        return cacheBustedStorageUrl(bucket.publicUrl(path))
    }

    private companion object {
        const val EXPENSE_FILES_BUCKET = "expense-files"
    }
}

@Serializable
internal data class ExpenseParticipantAccessRow(
    @SerialName("expense_id") val expenseId: String,
)
