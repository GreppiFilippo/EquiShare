/** Defines the Payment domain model. */
package it.unibo.equishare.domain.model

/** Domain entity for a completed payment between two group members. */
data class Payment(
    val id: String,
    val fromUserId: String,
    val toUserId: String,
    val amount: Money,
    val paymentDate: String,   // ISO date string "yyyy-MM-dd"
)
