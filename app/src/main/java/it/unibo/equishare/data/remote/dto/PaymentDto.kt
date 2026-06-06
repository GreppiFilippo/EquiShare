/** Defines remote DTOs for Payment data. */
package it.unibo.equishare.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PaymentDto(
    val id: String? = null,
    @SerialName("group_id")     val groupId: String? = null,
    @SerialName("from_user_id") val fromUserId: String,
    @SerialName("to_user_id")   val toUserId: String,
    val amount: Double,
    val currency: String,
    @SerialName("payment_date") val paymentDate: String,
    val note: String? = null,
    val status: String = "completed",
    @SerialName("created_by")   val createdBy: String? = null,
    @SerialName("created_at")   val createdAt: String? = null,
)
