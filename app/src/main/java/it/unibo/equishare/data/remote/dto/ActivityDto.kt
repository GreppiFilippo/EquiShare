/** Defines remote DTOs for Activity data. */
package it.unibo.equishare.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ActivityLogDto(
    val id: String,
    @SerialName("actor_user_id") val actorUserId: String? = null,
    @SerialName("group_id")      val groupId: String? = null,
    @SerialName("expense_id")    val expenseId: String? = null,
    @SerialName("payment_id")    val paymentId: String? = null,
    @SerialName("activity_type") val activityType: String,
    val metadata: JsonElement? = null,
    @SerialName("created_at")    val createdAt: String,
)
