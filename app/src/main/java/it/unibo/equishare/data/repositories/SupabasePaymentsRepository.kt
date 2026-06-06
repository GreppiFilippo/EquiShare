/** Implements the Payments repository using Supabase and local data. */
package it.unibo.equishare.data.repositories

import it.unibo.equishare.data.remote.datasource.SupabasePaymentsDataSource
import it.unibo.equishare.data.remote.dto.PaymentDto
import it.unibo.equishare.domain.model.Currency
import it.unibo.equishare.domain.model.Money
import it.unibo.equishare.domain.model.Payment
import it.unibo.equishare.domain.repository.AuthRepository
import it.unibo.equishare.domain.repository.PaymentsRepository

class SupabasePaymentsRepository(
    private val remote: SupabasePaymentsDataSource,
    private val auth: AuthRepository,
) : PaymentsRepository {

    override suspend fun pay(
        groupId: String?,
        toUserId: String,
        amount: Money,
        currency: Currency,
        paymentDate: String,
        note: String?,
    ) {
        val uid = auth.currentUserId ?: error("Not signed in")
        remote.insertPayment(
            PaymentDto(
                groupId = groupId,
                fromUserId = uid,
                toUserId = toUserId,
                amount = amount.toDouble(),
                currency = currency.code,
                paymentDate = paymentDate,
                note = note,
                status = "completed",
                createdBy = uid,
            )
        )
    }

    override suspend fun paymentsByGroup(groupId: String): List<Payment> =
        remote.fetchPaymentsByGroup(groupId)
            .filter { it.status == "completed" }
            .map { dto ->
                val currency = Currency.fromCode(dto.currency)
                Payment(
                    id = dto.id.orEmpty(),
                    fromUserId = dto.fromUserId,
                    toUserId = dto.toUserId,
                    amount = Money.of(dto.amount, currency),
                    paymentDate = dto.paymentDate,
                )
            }
}
