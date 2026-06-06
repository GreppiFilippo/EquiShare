/** Implements the Expenses repository using Supabase and local data. */
package it.unibo.equishare.data.repositories

import it.unibo.equishare.data.local.EquiShareLocalDataSource
import it.unibo.equishare.data.remote.datasource.SupabaseExpensesDataSource
import it.unibo.equishare.data.remote.dto.ExpenseDto
import it.unibo.equishare.data.remote.dto.ExpenseParticipantDto
import it.unibo.equishare.data.remote.mappers.toDomain
import it.unibo.equishare.domain.model.Currency
import it.unibo.equishare.domain.model.Expense
import it.unibo.equishare.domain.model.ExpenseCategory
import it.unibo.equishare.domain.model.ExpenseParticipant
import it.unibo.equishare.domain.model.ExpenseStatus
import it.unibo.equishare.domain.model.ImageUpload
import it.unibo.equishare.domain.model.Money
import it.unibo.equishare.domain.model.SplitMethod
import it.unibo.equishare.domain.repository.AuthRepository
import it.unibo.equishare.domain.repository.ExpensesRepository
import kotlinx.coroutines.flow.Flow
import java.time.Instant

class SupabaseExpensesRepository(
    private val remote: SupabaseExpensesDataSource,
    private val auth: AuthRepository,
    private val local: EquiShareLocalDataSource,
) : RefreshableRepository(), ExpensesRepository {

    init { watchAuth(auth.isSignedIn) }

    override fun expensesByGroup(groupId: String): Flow<List<Expense>> = refreshableCacheFirst { _isForced ->
        val uid = auth.currentUserId ?: run { emit(emptyList()); return@refreshableCacheFirst }
        emit(local.expensesByGroup(uid, groupId))
        // No TTL: always refresh in the background. Each group is independent and the
        // global lastSyncAt (owned by groups) would incorrectly suppress this fetch.
        try {
            val expenses = remote.fetchExpensesByGroup(groupId)
            local.replaceExpenses(uid, groupId, expenses)
            val payerIdsByExpenseId = runCatching {
                remote.fetchPayerIds(expenses.mapNotNull { it.id })
            }.getOrDefault(emptyMap())
            emit(expenses.map { expense ->
                expense.toDomain(payerUserIds = payerIdsByExpenseId[expense.id].orEmpty())
            })
        } catch (t: Throwable) {
            t.rethrowIfCancellation()
        }
    }

    override fun getById(id: String): Flow<Expense?> = refreshableCacheFirst { _isForced ->
        val uid = auth.currentUserId ?: run { emit(null); return@refreshableCacheFirst }
        emit(local.expense(uid, id))
        try {
            val fresh = remote.fetchExpenseById(id)?.also { local.upsertExpense(uid, it) }?.toDomain()
            emit(fresh)
        } catch (t: Throwable) {
            t.rethrowIfCancellation()
        }
    }

    override suspend fun getParticipants(expenseId: String): List<ExpenseParticipant> =
        try {
            remote.fetchParticipants(expenseId).map { dto ->
                ExpenseParticipant(
                    userId = dto.userId,
                    paid = Money.of(dto.paidAmount, Currency.EUR),
                    owed = Money.of(dto.owedAmount, Currency.EUR),
                )
            }
        } catch (t: Throwable) {
            t.rethrowIfCancellation()
            emptyList()
        }

    override suspend fun getExpenseCategories(): List<ExpenseCategory> =
        try {
            val categories = remote.fetchExpenseCategories()
            local.replaceExpenseCategories(categories)
            categories.map { it.toDomain() }
        } catch (t: Throwable) {
            t.rethrowIfCancellation()
            local.expenseCategories()
        }

    override suspend fun addExpense(
        groupId: String,
        title: String,
        total: Money,
        currency: Currency,
        expenseDate: String,
        categoryId: String?,
        splitMethod: SplitMethod,
        primaryPayerId: String,
        participants: List<ExpenseParticipant>,
        receipt: ImageUpload?,
    ) {
        require(participants.isNotEmpty()) { "Expense must have at least one participant" }
        val uid = auth.currentUserId ?: error("Not signed in")

        // Insert as DRAFT first: DB validation triggers only fire on POSTED status,
        // so all participant rows must exist before the status is promoted.
        val inserted = remote.insertExpense(
            ExpenseDto(
                groupId = groupId,
                title = title,
                categoryId = categoryId,
                expenseDate = expenseDate,
                currency = currency.code,
                totalAmount = total.toDouble(),
                paidByUserId = primaryPayerId,
                createdBy = uid,
                splitMethod = splitMethod.dbValue,
                status = ExpenseStatus.DRAFT.dbValue,
            )
        )
        remote.insertParticipants(
            participants.map { p ->
                ExpenseParticipantDto(
                    expenseId = inserted.id,
                    userId = p.userId,
                    paidAmount = p.paid.toDouble(),
                    owedAmount = p.owed.toDouble(),
                )
            }
        )
        val receiptUrl = receipt?.let { remote.uploadReceipt(uid, inserted.id, it.bytes, it.mimeType) }
        remote.updateExpenseStatus(inserted.id, ExpenseStatus.POSTED.dbValue, receiptUrl)
        local.upsertExpense(
            uid,
            ExpenseDto(
                id = inserted.id,
                groupId = groupId,
                title = title,
                categoryId = categoryId,
                expenseDate = expenseDate,
                currency = currency.code,
                totalAmount = total.toDouble(),
                paidByUserId = primaryPayerId,
                createdBy = uid,
                splitMethod = splitMethod.dbValue,
                status = ExpenseStatus.POSTED.dbValue,
                receiptUrl = receiptUrl,
                createdAt = Instant.now().toString(),
            ),
        )
        refresh()
    }

    override suspend fun updateExpense(
        expenseId: String,
        title: String,
        total: Money,
        currency: Currency,
        expenseDate: String,
        categoryId: String?,
        splitMethod: SplitMethod,
        primaryPayerId: String,
        participants: List<ExpenseParticipant>,
        receipt: ImageUpload?,
    ) {
        require(participants.isNotEmpty()) { "Expense must have at least one participant" }
        val uid = auth.currentUserId ?: error("Not signed in")
        require(remote.fetchParticipantAccessRow(expenseId, uid)) {
            "Only expense contributors can modify this expense"
        }

        // Set DRAFT while reconciling participants so the DB validator doesn't
        // fire against a half-updated participant set.
        remote.updateExpenseStatus(expenseId, ExpenseStatus.DRAFT.dbValue)

        // Update existing rows in place, insert new ones, delete obsolete ones.
        // "DELETE all + INSERT new" collides with the (expense_id, user_id) unique
        // constraint when PostgREST retries or RLS scopes the DELETE down.
        val existing = remote.fetchExistingParticipants(expenseId)
        val existingByUserId = existing.associateBy { it.userId }
        val newUserIds = participants.map { it.userId }.toSet()

        remote.deleteParticipants(
            existing.filter { it.userId !in newUserIds }.mapNotNull { it.id }
        )

        val toInsert = mutableListOf<ExpenseParticipantDto>()
        for (p in participants) {
            val prior = existingByUserId[p.userId]
            if (prior?.id != null) {
                remote.updateParticipant(prior.id, p.paid.toDouble(), p.owed.toDouble())
            } else {
                toInsert += ExpenseParticipantDto(
                    expenseId = expenseId,
                    userId = p.userId,
                    paidAmount = p.paid.toDouble(),
                    owedAmount = p.owed.toDouble(),
                )
            }
        }
        if (toInsert.isNotEmpty()) {
            remote.insertParticipants(toInsert)
        }

        val newReceiptUrl = receipt?.let { remote.uploadReceipt(uid, expenseId, it.bytes, it.mimeType) }
        remote.updateExpenseFields(
            expenseId = expenseId,
            title = title,
            categoryId = categoryId,
            expenseDate = expenseDate,
            currencyCode = currency.code,
            totalAmount = total.toDouble(),
            primaryPayerId = primaryPayerId,
            splitMethod = splitMethod.dbValue,
            receiptUrl = newReceiptUrl,
        )
        refresh()
    }

    override suspend fun softDelete(id: String) {
        val uid = auth.currentUserId ?: error("Not signed in")
        require(remote.fetchParticipantAccessRow(id, uid)) {
            "Only expense contributors can modify this expense"
        }
        remote.softDeleteExpense(id)
        local.deleteExpense(uid, id)
        refresh()
    }

}
