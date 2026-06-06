/** Defines Room data access objects. */
package it.unibo.equishare.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CachedGroupDao {
    @Query("SELECT * FROM cached_groups WHERE owner_user_id = :ownerUserId ORDER BY joined_at DESC")
    suspend fun getGroups(ownerUserId: String): List<CachedGroupEntity>

    @Query("SELECT * FROM cached_groups WHERE owner_user_id = :ownerUserId AND id = :id LIMIT 1")
    suspend fun getGroup(ownerUserId: String, id: String): CachedGroupEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGroups(groups: List<CachedGroupEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGroup(group: CachedGroupEntity)

    @Query("DELETE FROM cached_groups WHERE owner_user_id = :ownerUserId")
    suspend fun clearGroups(ownerUserId: String)

    @Query("DELETE FROM cached_groups WHERE owner_user_id = :ownerUserId AND id = :id")
    suspend fun deleteGroup(ownerUserId: String, id: String)

    @Query(
        """
        UPDATE cached_groups
        SET name = CASE WHEN :name IS NULL THEN name ELSE :name END,
            description = CASE WHEN :description IS NULL THEN description ELSE :description END,
            avatar_url = CASE WHEN :avatarUrl IS NULL THEN avatar_url ELSE :avatarUrl END
        WHERE owner_user_id = :ownerUserId AND id = :id
        """
    )
    suspend fun updateGroup(
        ownerUserId: String,
        id: String,
        name: String?,
        description: String?,
        avatarUrl: String?,
    )

    @Query("UPDATE cached_groups SET is_favorite = :isFavorite WHERE owner_user_id = :ownerUserId AND id = :id")
    suspend fun updateFavorite(ownerUserId: String, id: String, isFavorite: Boolean)
}

@Dao
interface CachedGroupMemberDao {
    @Query(
        """
        SELECT * FROM cached_group_members
        WHERE owner_user_id = :ownerUserId AND group_id = :groupId
        ORDER BY is_current_user DESC, display_name ASC
        """
    )
    suspend fun getMembers(ownerUserId: String, groupId: String): List<CachedGroupMemberEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMembers(members: List<CachedGroupMemberEntity>)

    @Query("DELETE FROM cached_group_members WHERE owner_user_id = :ownerUserId AND group_id = :groupId")
    suspend fun clearMembers(ownerUserId: String, groupId: String)

    @Query(
        """
        DELETE FROM cached_group_members
        WHERE owner_user_id = :ownerUserId AND group_id = :groupId AND user_id = :userId
        """
    )
    suspend fun deleteMember(ownerUserId: String, groupId: String, userId: String)

    @Query("DELETE FROM cached_group_members WHERE owner_user_id = :ownerUserId")
    suspend fun clearAllMembersForUser(ownerUserId: String)
}

@Dao
interface CachedGroupCategoryDao {
    @Query("SELECT * FROM cached_group_categories ORDER BY sort_order ASC")
    suspend fun getCategories(): List<CachedGroupCategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategories(categories: List<CachedGroupCategoryEntity>)

    @Query("DELETE FROM cached_group_categories")
    suspend fun clearCategories()
}

@Dao
interface CachedExpenseDao {
    @Query(
        """
        SELECT * FROM cached_expenses
        WHERE owner_user_id = :ownerUserId AND group_id = :groupId AND status = 'posted'
        ORDER BY expense_date DESC, created_at DESC
        """
    )
    suspend fun getExpensesByGroup(ownerUserId: String, groupId: String): List<CachedExpenseEntity>

    @Query("SELECT * FROM cached_expenses WHERE owner_user_id = :ownerUserId AND id = :id LIMIT 1")
    suspend fun getExpense(ownerUserId: String, id: String): CachedExpenseEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExpenses(expenses: List<CachedExpenseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExpense(expense: CachedExpenseEntity)

    @Query("DELETE FROM cached_expenses WHERE owner_user_id = :ownerUserId AND group_id = :groupId")
    suspend fun clearExpensesForGroup(ownerUserId: String, groupId: String)

    @Query("DELETE FROM cached_expenses WHERE owner_user_id = :ownerUserId AND id = :id")
    suspend fun deleteExpense(ownerUserId: String, id: String)

    @Query("DELETE FROM cached_expenses WHERE owner_user_id = :ownerUserId")
    suspend fun clearAllExpensesForUser(ownerUserId: String)
}

@Dao
interface CachedExpenseCategoryDao {
    @Query("SELECT * FROM cached_expense_categories ORDER BY sort_order ASC")
    suspend fun getCategories(): List<CachedExpenseCategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategories(categories: List<CachedExpenseCategoryEntity>)

    @Query("DELETE FROM cached_expense_categories")
    suspend fun clearCategories()
}

@Dao
interface CachedActivityDao {
    @Query(
        """
        SELECT * FROM cached_activities
        WHERE owner_user_id = :ownerUserId
        ORDER BY created_at DESC
        LIMIT 50
        """
    )
    suspend fun getActivities(ownerUserId: String): List<CachedActivityEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertActivities(activities: List<CachedActivityEntity>)

    @Query("DELETE FROM cached_activities WHERE owner_user_id = :ownerUserId")
    suspend fun clearActivities(ownerUserId: String)
}

@Dao
interface CachedProfileDao {
    @Query("SELECT * FROM cached_profiles WHERE id = :userId LIMIT 1")
    suspend fun getProfile(userId: String): CachedProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: CachedProfileEntity)

    @Query("DELETE FROM cached_profiles WHERE id = :userId")
    suspend fun deleteProfile(userId: String)
}
