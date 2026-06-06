/** Defines the Payments repository contract. */
package it.unibo.equishare.domain.repository

import it.unibo.equishare.domain.model.Currency
import it.unibo.equishare.domain.model.Money
import it.unibo.equishare.domain.model.Payment

interface PaymentsRepository {
    suspend fun pay(
        groupId: String?,
        toUserId: String,
        amount: Money,
        currency: Currency,
        paymentDate: String,
        note: String? = null,
    )

    /** Returns all completed payments for the given group, newest first. */
    suspend fun paymentsByGroup(groupId: String): List<Payment>
}
