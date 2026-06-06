/** Defines remote DTOs for Group data. */
package it.unibo.equishare.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GroupIdDto(val id: String)

@Serializable
data class GroupDto(
    val id: String? = null,
    val name: String,
    val description: String? = null,
    val type: String = "other",
    @SerialName("category_id")    val categoryId: String? = null,
    @SerialName("avatar_url")     val avatarUrl: String? = null,
    @SerialName("base_currency")  val baseCurrency: String = "EUR",
    @SerialName("simplify_debts") val simplifyDebts: Boolean = true,
    @SerialName("created_by")     val createdBy: String? = null,
    @SerialName("archived_at")    val archivedAt: String? = null,
    @SerialName("created_at")     val createdAt: String? = null,
)

@Serializable
data class GroupCategoryDto(
    val id: String,
    val code: String,
    @SerialName("name_it")    val nameIt: String,
    @SerialName("name_en")    val nameEn: String,
    @SerialName("icon_key")   val iconKey: String,
    @SerialName("group_type") val groupType: String = "other",
    @SerialName("sort_order") val sortOrder: Int = 0,
)

@Serializable
data class GroupMemberDto(
    val id: String? = null,
    @SerialName("group_id")              val groupId: String,
    @SerialName("user_id")               val userId: String,
    val role: String = "member",
    @SerialName("display_name_override") val displayNameOverride: String? = null,
    @SerialName("invited_by")            val invitedBy: String? = null,
    @SerialName("joined_at")             val joinedAt: String? = null,
    @SerialName("left_at")               val leftAt: String? = null,
)

@Serializable
data class UserGroupRow(
    val id: String,
    val name: String,
    val description: String? = null,
    val type: String,
    @SerialName("avatar_url")         val avatarUrl: String? = null,
    @SerialName("base_currency")      val baseCurrency: String,
    val role: String,
    @SerialName("joined_at")          val joinedAt: String? = null,
    @SerialName("member_count")       val memberCount: Int,
    val balance: Double,
    @SerialName("category_id")        val categoryId: String? = null,
    @SerialName("category_code")      val categoryCode: String? = null,
    @SerialName("category_name_it")   val categoryNameIt: String? = null,
    @SerialName("category_name_en")   val categoryNameEn: String? = null,
    @SerialName("category_icon_key")  val categoryIconKey: String? = null,
    @SerialName("is_favorite")        val isFavorite: Boolean = false,
)
