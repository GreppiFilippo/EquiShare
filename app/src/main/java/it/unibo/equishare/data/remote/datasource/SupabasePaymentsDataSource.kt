/** Defines Supabase Payments Data Source app code. */
package it.unibo.equishare.data.remote.datasource

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import it.unibo.equishare.data.remote.dto.PaymentDto

class SupabasePaymentsDataSource(private val client: SupabaseClient) {

    suspend fun insertPayment(dto: PaymentDto) {
        client.postgrest.from("payments").insert(dto)
    }

    suspend fun fetchPaymentsByGroup(groupId: String): List<PaymentDto> =
        client.postgrest.from("payments")
            .select {
                filter { eq("group_id", groupId) }
                order("payment_date", Order.DESCENDING)
                order("created_at", Order.DESCENDING)
            }
            .decodeList()
}
