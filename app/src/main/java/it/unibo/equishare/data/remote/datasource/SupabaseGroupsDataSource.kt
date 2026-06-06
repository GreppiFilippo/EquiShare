/** Wraps Supabase calls for Groups data. */
package it.unibo.equishare.data.remote.datasource

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import it.unibo.equishare.data.remote.dto.ExpenseParticipantDto
import it.unibo.equishare.data.remote.dto.GroupCategoryDto
import it.unibo.equishare.data.remote.dto.GroupDto
import it.unibo.equishare.data.remote.dto.GroupIdDto
import it.unibo.equishare.data.remote.dto.GroupMemberDto
import it.unibo.equishare.data.remote.dto.UserGroupRow
import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class SupabaseGroupsDataSource(private val client: SupabaseClient) {

    suspend fun fetchGroups(): List<UserGroupRow> =
        client.postgrest.from("v_user_groups")
            .select { order("joined_at", Order.DESCENDING) }
            .decodeList()

    suspend fun fetchGroupById(id: String): UserGroupRow? =
        client.postgrest.from("v_user_groups")
            .select { filter { eq("id", id) } }
            .decodeSingleOrNull()

    suspend fun fetchGroupMemberRows(groupId: String): List<GroupMemberRow> =
        client.postgrest.from("group_members")
            .select(Columns.list("user_id, role, display_name_override, joined_at, left_at")) {
                filter { eq("group_id", groupId) }
                order("joined_at", Order.ASCENDING)
            }
            .decodeList<GroupMemberRow>()
            .filter { it.leftAt == null }

    suspend fun fetchMemberProfiles(userIds: List<String>): Map<String, GroupMemberProfileRow> {
        if (userIds.isEmpty()) return emptyMap()
        return client.postgrest.from("profiles")
            .select(Columns.list("id, email, full_name, avatar_url")) {
                filter { isIn("id", userIds) }
            }
            .decodeList<GroupMemberProfileRow>()
            .associateBy { it.id }
    }

    suspend fun fetchGroupCurrency(groupId: String): String? =
        client.postgrest.from("v_user_groups")
            .select(Columns.list("base_currency")) {
                filter { eq("id", groupId) }
            }
            .decodeSingleOrNull<GroupCurrencyRow>()
            ?.baseCurrency

    suspend fun fetchBalanceExpenses(groupId: String): List<GroupBalanceExpenseRow> =
        client.postgrest.from("expenses")
            .select(Columns.list("id, currency")) {
                filter {
                    eq("group_id", groupId)
                    eq("status", "posted")
                }
            }
            .decodeList()

    suspend fun fetchExpenseParticipants(expenseIds: List<String>): List<ExpenseParticipantDto> {
        if (expenseIds.isEmpty()) return emptyList()
        return client.postgrest.from("expense_participants")
            .select(Columns.list("expense_id, user_id, paid_amount, owed_amount")) {
                filter { isIn("expense_id", expenseIds) }
            }
            .decodeList()
    }

    suspend fun fetchGroupPayments(groupId: String): List<GroupPaymentBalanceRow> =
        client.postgrest.from("payments")
            .select(Columns.list("from_user_id, to_user_id, amount, currency, status")) {
                filter {
                    eq("group_id", groupId)
                    eq("status", "completed")
                }
            }
            .decodeList()

    suspend fun fetchGroupCategories(): List<GroupCategoryDto> =
        client.postgrest.from("group_categories")
            .select {
                filter { eq("is_active", true) }
                order("sort_order", Order.ASCENDING)
            }
            .decodeList()

    suspend fun insertGroup(dto: GroupDto): GroupIdDto =
        client.postgrest.from("groups")
            .insert(dto) { select(Columns.list("id")) }
            .decodeSingle()

    suspend fun insertGroupMember(dto: GroupMemberDto) {
        client.postgrest.from("group_members").insert(dto)
    }

    suspend fun updateGroupFields(id: String, name: String?, description: String?, avatarUrl: String?) {
        client.postgrest.from("groups").update({
            name?.let        { set("name", it) }
            description?.let { set("description", it) }
            avatarUrl?.let   { set("avatar_url", it) }
        }) { filter { eq("id", id) } }
    }

    suspend fun archiveGroup(id: String) {
        client.postgrest.from("groups").update({
            set("archived_at", Instant.now().toString())
        }) { filter { eq("id", id) } }
    }

    suspend fun deleteGroup(id: String): List<GroupIdDto> =
        client.postgrest.from("groups")
            .delete {
                select(Columns.list("id"))
                filter { eq("id", id) }
            }
            .decodeList()

    suspend fun leaveGroupWithSuccessor(groupId: String, successorUserId: String?): String? {
        val response = client.postgrest.rpc(
            function = "leave_group_with_successor",
            parameters = buildJsonObject {
                put("p_group_id", groupId)
                if (successorUserId == null) put("p_successor_user_id", JsonNull)
                else put("p_successor_user_id", successorUserId)
            }
        )
        return response.decodeAs<JsonObject>()["status"]?.jsonPrimitive?.content
    }

    suspend fun setMemberLeftAt(groupId: String, userId: String) {
        client.postgrest.from("group_members").update({
            set("left_at", Instant.now().toString())
        }) {
            filter {
                eq("group_id", groupId)
                eq("user_id", userId)
            }
        }
    }

    suspend fun fetchMemberRole(groupId: String, userId: String): GroupMemberRoleRow? =
        client.postgrest.from("group_members")
            .select(Columns.list("role, left_at")) {
                filter {
                    eq("group_id", groupId)
                    eq("user_id", userId)
                }
            }
            .decodeList<GroupMemberRoleRow>()
            .firstOrNull { it.leftAt == null }

    suspend fun fetchMemberBalance(groupId: String, userId: String): Double =
        client.postgrest.from("group_member_balances")
            .select(Columns.list("balance")) {
                filter {
                    eq("group_id", groupId)
                    eq("user_id", userId)
                }
            }
            .decodeSingleOrNull<GroupMemberBalanceRow>()
            ?.balance
            ?: 0.0

    suspend fun setGroupFavorite(groupId: String, isFavorite: Boolean) {
        client.postgrest.rpc(
            function = "set_group_favorite",
            parameters = buildJsonObject {
                put("p_group_id", groupId)
                put("p_is_favorite", isFavorite)
            }
        )
    }

    suspend fun inviteMemberByEmail(groupId: String, email: String): JsonObject {
        val response = client.postgrest.rpc(
            function = "invite_member_by_email",
            parameters = buildJsonObject {
                put("p_group_id", groupId)
                put("p_email", email.trim())
            }
        )
        return response.decodeAs()
    }

    suspend fun uploadGroupPhoto(uid: String, groupId: String, bytes: ByteArray, mimeType: String): String {
        val extension = storageExtensionForMime(mimeType)
        val path = "$uid/groups/$groupId/photo.$extension"
        val bucket = client.storage.from(GROUP_PHOTOS_BUCKET)
        bucket.upload(path = path, data = bytes) { upsert = true }
        return cacheBustedStorageUrl(bucket.publicUrl(path))
    }

    private companion object {
        const val GROUP_PHOTOS_BUCKET = "group-photos"
    }
}

@Serializable
data class GroupCurrencyRow(
    @SerialName("base_currency") val baseCurrency: String,
)

@Serializable
data class GroupBalanceExpenseRow(
    val id: String,
    val currency: String,
)

@Serializable
data class GroupPaymentBalanceRow(
    @SerialName("from_user_id") val fromUserId: String,
    @SerialName("to_user_id")   val toUserId: String,
    val amount: Double,
    val currency: String,
    val status: String,
)

@Serializable
data class GroupMemberRoleRow(
    val role: String,
    @SerialName("left_at") val leftAt: String? = null,
)

@Serializable
data class GroupMemberBalanceRow(val balance: Double)

@Serializable
data class GroupMemberRow(
    @SerialName("user_id")               val userId: String,
    val role: String,
    @SerialName("display_name_override") val displayNameOverride: String? = null,
    @SerialName("joined_at")             val joinedAt: String? = null,
    @SerialName("left_at")               val leftAt: String? = null,
)

@Serializable
data class GroupMemberProfileRow(
    val id: String,
    val email: String? = null,
    @SerialName("full_name")  val fullName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
)
