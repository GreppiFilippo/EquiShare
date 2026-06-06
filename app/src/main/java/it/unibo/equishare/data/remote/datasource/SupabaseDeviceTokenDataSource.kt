/** Defines Supabase Device Token Data Source app code. */
package it.unibo.equishare.data.remote.datasource

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class SupabaseDeviceTokenDataSource(private val client: SupabaseClient) {

    suspend fun registerToken(token: String) {
        client.postgrest.rpc(
            function = "register_user_device",
            parameters = buildJsonObject {
                put("p_token", token)
                put("p_platform", "android")
            },
        )
    }

    suspend fun unregisterToken(userId: String, token: String) {
        client.postgrest.from("user_devices").delete {
            filter {
                eq("user_id", userId)
                eq("token", token)
            }
        }
    }
}
