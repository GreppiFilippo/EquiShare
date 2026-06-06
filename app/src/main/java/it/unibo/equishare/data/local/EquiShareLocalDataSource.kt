/** Coordinates reads and writes against the local cache. */
package it.unibo.equishare.data.local

import androidx.room.withTransaction
import it.unibo.equishare.data.remote.dto.ExpenseCategoryDto
import it.unibo.equishare.data.remote.dto.ExpenseDto
import it.unibo.equishare.data.remote.dto.GroupCategoryDto
import it.unibo.equishare.data.remote.dto.ProfileDto
import it.unibo.equishare.data.remote.dto.UserGroupRow
import it.unibo.equishare.domain.model.ActivityEntry
import it.unibo.equishare.domain.model.AppCategory
import it.unibo.equishare.domain.model.Expense
import it.unibo.equishare.domain.model.ExpenseCategory
import it.unibo.equishare.domain.model.Group
import it.unibo.equishare.domain.model.GroupMember
import it.unibo.equishare.domain.model.GroupSettings
import java.time.Instant

class EquiShareLocalDataSource(
    private val database: EquiShareDatabase,
) {
    private val groupDao = database.groupDao()
    private val groupMemberDao = database.groupMemberDao()
    private val groupCategoryDao = database.groupCategoryDao()
    private val expenseDao = database.expenseDao()
    private val expenseCategoryDao = database.expenseCategoryDao()
    private val activityDao = database.activityDao()
    private val profileDao = database.profileDao()

    suspend fun groups(ownerUserId: String): List<Group> =
        groupDao.getGroups(ownerUserId).map { it.toDomain() }

    suspend fun group(ownerUserId: String, id: String): Group? =
        groupDao.getGroup(ownerUserId, id)?.toDomain()

    suspend fun groupSettings(ownerUserId: String, id: String): GroupSettings? =
        groupDao.getGroup(ownerUserId, id)?.toSettings()

    suspend fun replaceGroups(ownerUserId: String, rows: List<UserGroupRow>) {
        val cachedAt = now()
        database.withTransaction {
            groupDao.clearGroups(ownerUserId)
            if (rows.isNotEmpty()) {
                groupDao.upsertGroups(rows.map { it.toEntity(ownerUserId, cachedAt) })
            }
        }
    }

    suspend fun upsertGroup(ownerUserId: String, row: UserGroupRow) {
        groupDao.upsertGroup(row.toEntity(ownerUserId, now()))
    }

    suspend fun updateGroup(
        ownerUserId: String,
        id: String,
        name: String?,
        description: String?,
        avatarUrl: String?,
    ) {
        groupDao.updateGroup(ownerUserId, id, name, description, avatarUrl)
    }

    suspend fun deleteGroup(ownerUserId: String, id: String) {
        groupDao.deleteGroup(ownerUserId, id)
    }

    suspend fun setGroupFavorite(ownerUserId: String, id: String, isFavorite: Boolean) {
        groupDao.updateFavorite(ownerUserId, id, isFavorite)
    }

    suspend fun groupMembers(ownerUserId: String, groupId: String): List<GroupMember> =
        groupMemberDao.getMembers(ownerUserId, groupId).map { it.toDomain() }

    suspend fun replaceGroupMembers(ownerUserId: String, groupId: String, members: List<GroupMember>) {
        val cachedAt = now()
        database.withTransaction {
            groupMemberDao.clearMembers(ownerUserId, groupId)
            if (members.isNotEmpty()) {
                groupMemberDao.upsertMembers(members.map { it.toEntity(ownerUserId, groupId, cachedAt) })
            }
        }
    }

    suspend fun deleteGroupMember(ownerUserId: String, groupId: String, userId: String) {
        groupMemberDao.deleteMember(ownerUserId, groupId, userId)
    }

    suspend fun groupCategories(): List<AppCategory> =
        groupCategoryDao.getCategories().map { it.toDomain() }

    suspend fun replaceGroupCategories(categories: List<GroupCategoryDto>) {
        val cachedAt = now()
        database.withTransaction {
            groupCategoryDao.clearCategories()
            if (categories.isNotEmpty()) {
                groupCategoryDao.upsertCategories(categories.map { it.toEntity(cachedAt) })
            }
        }
    }

    suspend fun expensesByGroup(ownerUserId: String, groupId: String): List<Expense> =
        expenseDao.getExpensesByGroup(ownerUserId, groupId).map { it.toDomain() }

    suspend fun expense(ownerUserId: String, id: String): Expense? =
        expenseDao.getExpense(ownerUserId, id)?.toDomain()

    suspend fun replaceExpenses(ownerUserId: String, groupId: String, expenses: List<ExpenseDto>) {
        val cachedAt = now()
        database.withTransaction {
            expenseDao.clearExpensesForGroup(ownerUserId, groupId)
            if (expenses.isNotEmpty()) {
                expenseDao.upsertExpenses(expenses.map { it.toEntity(ownerUserId, cachedAt) })
            }
        }
    }

    suspend fun upsertExpense(ownerUserId: String, expense: ExpenseDto) {
        expenseDao.upsertExpense(expense.toEntity(ownerUserId, now()))
    }

    suspend fun deleteExpense(ownerUserId: String, id: String) {
        expenseDao.deleteExpense(ownerUserId, id)
    }

    suspend fun expenseCategories(): List<ExpenseCategory> =
        expenseCategoryDao.getCategories().map { it.toDomain() }

    suspend fun replaceExpenseCategories(categories: List<ExpenseCategoryDto>) {
        val cachedAt = now()
        database.withTransaction {
            expenseCategoryDao.clearCategories()
            if (categories.isNotEmpty()) {
                expenseCategoryDao.upsertCategories(categories.map { it.toEntity(cachedAt) })
            }
        }
    }

    suspend fun activities(ownerUserId: String): List<ActivityEntry> =
        activityDao.getActivities(ownerUserId).map { it.toDomain() }

    suspend fun replaceActivities(ownerUserId: String, activities: List<ActivityEntry>) {
        val cachedAt = now()
        database.withTransaction {
            activityDao.clearActivities(ownerUserId)
            if (activities.isNotEmpty()) {
                activityDao.upsertActivities(activities.map { it.toEntity(ownerUserId, cachedAt) })
            }
        }
    }

    suspend fun profile(userId: String): ProfileDto? =
        profileDao.getProfile(userId)?.toDto()

    suspend fun upsertProfile(profile: ProfileDto) {
        profileDao.upsertProfile(profile.toEntity(now()))
    }

    suspend fun clearUserData(userId: String) {
        database.withTransaction {
            groupDao.clearGroups(userId)
            groupMemberDao.clearAllMembersForUser(userId)
            expenseDao.clearAllExpensesForUser(userId)
            activityDao.clearActivities(userId)
            profileDao.deleteProfile(userId)
        }
    }

    private fun now(): String = Instant.now().toString()
}
