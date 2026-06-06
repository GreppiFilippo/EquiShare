/** Defines remote DTOs for Expense data. */
package it.unibo.equishare.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ExpenseIdDto(val id: String)

@Serializable
data class ExpenseDto(
    val id: String? = null,
    @SerialName("group_id")        val groupId: String? = null,
    val title: String,
    val description: String? = null,
    @SerialName("category_id")     val categoryId: String? = null,
    @SerialName("expense_date")    val expenseDate: String,
    val currency: String,
    @SerialName("total_amount")    val totalAmount: Double,
    @SerialName("paid_by_user_id") val paidByUserId: String,
    @SerialName("created_by")      val createdBy: String? = null,
    @SerialName("split_method")    val splitMethod: String = "equal",
    val status: String = "posted",
    @SerialName("receipt_url")     val receiptUrl: String? = null,
    @SerialName("deleted_at")      val deletedAt: String? = null,
    @SerialName("created_at")      val createdAt: String? = null,
)

// paid_amount and owed_amount must each sum to expenses.total_amount;
// the DB trigger validate_expense_participants enforces this on posted expenses.
@Serializable
data class ExpenseParticipantDto(
    val id: String? = null,
    @SerialName("expense_id")   val expenseId: String,
    @SerialName("user_id")      val userId: String,
    @SerialName("paid_amount")  val paidAmount: Double,
    @SerialName("owed_amount")  val owedAmount: Double,
    @SerialName("exact_amount") val exactAmount: Double? = null,
    val percentage: Double? = null,
    val shares: Double? = null,
    val note: String? = null,
)

@Serializable
data class ExpenseCategoryDto(
    val id: String,
    val code: String,
    val name: String = "",
    @SerialName("name_it") val nameIt: String? = null,
    @SerialName("name_en") val nameEn: String? = null,
    val icon: String? = null,
    @SerialName("sort_order") val sortOrder: Int = 0,
    @SerialName("is_active") val isActive: Boolean = true,
)
