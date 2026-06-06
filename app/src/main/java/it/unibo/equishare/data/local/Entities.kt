/** Defines Room cache entities. */
package it.unibo.equishare.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(
    tableName = "cached_groups",
    primaryKeys = ["owner_user_id", "id"],
)
data class CachedGroupEntity(
    @ColumnInfo(name = "owner_user_id") val ownerUserId: String,
    val id: String,
    val name: String,
    val description: String?,
    val type: String,
    @ColumnInfo(name = "avatar_url") val avatarUrl: String?,
    @ColumnInfo(name = "base_currency") val baseCurrency: String,
    val role: String,
    @ColumnInfo(name = "joined_at") val joinedAt: String?,
    @ColumnInfo(name = "member_count") val memberCount: Int,
    val balance: Double,
    @ColumnInfo(name = "category_id") val categoryId: String?,
    @ColumnInfo(name = "category_code") val categoryCode: String?,
    @ColumnInfo(name = "category_name_it") val categoryNameIt: String?,
    @ColumnInfo(name = "category_name_en") val categoryNameEn: String?,
    @ColumnInfo(name = "category_icon_key") val categoryIconKey: String?,
    @ColumnInfo(name = "is_favorite", defaultValue = "0") val isFavorite: Boolean = false,
    @ColumnInfo(name = "cached_at") val cachedAt: String,
)

@Entity(
    tableName = "cached_group_members",
    primaryKeys = ["owner_user_id", "group_id", "user_id"],
)
data class CachedGroupMemberEntity(
    @ColumnInfo(name = "owner_user_id") val ownerUserId: String,
    @ColumnInfo(name = "group_id") val groupId: String,
    @ColumnInfo(name = "user_id") val userId: String,
    val role: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    val email: String,
    @ColumnInfo(name = "avatar_url") val avatarUrl: String?,
    @ColumnInfo(name = "is_current_user") val isCurrentUser: Boolean,
    @ColumnInfo(name = "cached_at") val cachedAt: String,
)

@Entity(tableName = "cached_group_categories")
data class CachedGroupCategoryEntity(
    @PrimaryKey val id: String,
    val code: String,
    @ColumnInfo(name = "name_it") val nameIt: String,
    @ColumnInfo(name = "name_en") val nameEn: String,
    @ColumnInfo(name = "icon_key") val iconKey: String,
    @ColumnInfo(name = "group_type") val groupType: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    @ColumnInfo(name = "cached_at") val cachedAt: String,
)

@Entity(
    tableName = "cached_expenses",
    primaryKeys = ["owner_user_id", "id"],
)
data class CachedExpenseEntity(
    @ColumnInfo(name = "owner_user_id") val ownerUserId: String,
    val id: String,
    @ColumnInfo(name = "group_id") val groupId: String,
    val title: String,
    val description: String?,
    @ColumnInfo(name = "category_id") val categoryId: String?,
    @ColumnInfo(name = "expense_date") val expenseDate: String,
    val currency: String,
    @ColumnInfo(name = "total_amount") val totalAmount: Double,
    @ColumnInfo(name = "paid_by_user_id") val paidByUserId: String,
    @ColumnInfo(name = "split_method") val splitMethod: String,
    val status: String,
    @ColumnInfo(name = "receipt_url") val receiptUrl: String?,
    @ColumnInfo(name = "created_by") val createdBy: String?,
    @ColumnInfo(name = "created_at") val createdAt: String?,
    @ColumnInfo(name = "cached_at") val cachedAt: String,
)

@Entity(tableName = "cached_expense_categories")
data class CachedExpenseCategoryEntity(
    @PrimaryKey val id: String,
    val code: String,
    val name: String,
    @ColumnInfo(name = "name_it") val nameIt: String,
    @ColumnInfo(name = "name_en") val nameEn: String,
    val icon: String?,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    @ColumnInfo(name = "cached_at") val cachedAt: String,
)

@Entity(
    tableName = "cached_activities",
    primaryKeys = ["owner_user_id", "id"],
)
data class CachedActivityEntity(
    @ColumnInfo(name = "owner_user_id") val ownerUserId: String,
    val id: String,
    val kind: String,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "group_id") val groupId: String?,
    @ColumnInfo(name = "expense_id") val expenseId: String?,
    @ColumnInfo(name = "payment_id") val paymentId: String?,
    @ColumnInfo(name = "group_name") val groupName: String?,
    @ColumnInfo(name = "group_category_key") val groupCategoryKey: String,
    @ColumnInfo(name = "actor_user_id") val actorUserId: String?,
    @ColumnInfo(name = "actor_display_name") val actorDisplayName: String?,
    @ColumnInfo(name = "target_user_id") val targetUserId: String?,
    @ColumnInfo(name = "target_display_name") val targetDisplayName: String?,
    val amount: Double?,
    @ColumnInfo(name = "amount_currency") val amountCurrency: String?,
    @ColumnInfo(name = "is_actor_current_user") val isActorCurrentUser: Boolean,
    @ColumnInfo(name = "is_target_current_user") val isTargetCurrentUser: Boolean,
    @ColumnInfo(name = "cached_at") val cachedAt: String,
)

@Entity(tableName = "cached_profiles")
data class CachedProfileEntity(
    @PrimaryKey val id: String,
    val email: String?,
    @ColumnInfo(name = "full_name") val fullName: String?,
    @ColumnInfo(name = "avatar_url") val avatarUrl: String?,
    @ColumnInfo(name = "default_currency") val defaultCurrency: String,
    val locale: String?,
    @ColumnInfo(name = "is_active") val isActive: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: String?,
    @ColumnInfo(name = "updated_at") val updatedAt: String?,
    @ColumnInfo(name = "cached_at") val cachedAt: String,
)
