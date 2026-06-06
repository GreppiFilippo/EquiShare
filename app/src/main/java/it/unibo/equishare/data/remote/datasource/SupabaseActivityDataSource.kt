/** Defines Supabase Activity Data Source app code. */
package it.unibo.equishare.data.remote.datasource

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import it.unibo.equishare.data.remote.dto.ActivityLogDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class SupabaseActivityDataSource(private val client: SupabaseClient) {

    suspend fun fetchActivityLogs(): List<ActivityLogDto> =
        client.postgrest.from("activity_log")
            .select {
                order("created_at", Order.DESCENDING)
                limit(50)
            }
            .decodeList()

    suspend fun fetchGroupContext(groupIds: List<String>): Map<String, ActivityGroupContextRow> {
        if (groupIds.isEmpty()) return emptyMap()
        val userGroups = client.postgrest.from("v_user_groups")
            .select(Columns.list("id, name, category_icon_key, type")) {
                filter { isIn("id", groupIds) }
            }
            .decodeList<ActivityGroupContextRow>()
            .associateBy { it.id }

        val missingGroupIds = groupIds.filterNot(userGroups::containsKey)
        if (missingGroupIds.isEmpty()) return userGroups

        val directGroups = runCatching {
            client.postgrest.from("groups")
                .select(Columns.list("id, name, type")) {
                    filter { isIn("id", missingGroupIds) }
                }
                .decodeList<ActivityGroupContextRow>()
                .associateBy { it.id }
        }.getOrDefault(emptyMap())

        return userGroups + directGroups
    }

    suspend fun fetchProfiles(userIds: List<String>): Map<String, ActivityActorProfileRow> {
        if (userIds.isEmpty()) return emptyMap()
        return client.postgrest.from("profiles")
            .select(Columns.list("id, full_name, email")) {
                filter { isIn("id", userIds) }
            }
            .decodeList<ActivityActorProfileRow>()
            .associateBy { it.id }
    }

    suspend fun respondToInvite(activityId: String, accept: Boolean): JsonObject {
        val response = client.postgrest.rpc(
            function = "respond_to_group_invitation",
            parameters = buildJsonObject {
                put("p_activity_id", activityId)
                put("p_accept", accept)
            }
        )
        return response.decodeAs()
    }
}

@Serializable
data class ActivityGroupContextRow(
    val id: String,
    val name: String,
    val type: String? = null,
    @SerialName("category_icon_key") val categoryIconKey: String? = null,
)

@Serializable
data class ActivityActorProfileRow(
    val id: String,
    @SerialName("full_name") val fullName: String? = null,
    val email: String? = null,
) {
    val displayLabel: String
        get() = fullName
            ?.takeIf { it.isNotBlank() }
            ?: email?.substringBefore("@")?.takeIf { it.isNotBlank() }
            ?: id.take(8)
}
