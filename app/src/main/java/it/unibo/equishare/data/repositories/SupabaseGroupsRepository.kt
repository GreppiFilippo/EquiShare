/** Implements the Groups repository using Supabase and local data. */
package it.unibo.equishare.data.repositories

import it.unibo.equishare.data.local.EquiShareLocalDataSource
import it.unibo.equishare.data.local.UserPreferencesDataSource
import it.unibo.equishare.data.remote.datasource.GroupMemberProfileRow
import it.unibo.equishare.data.remote.datasource.GroupMemberRow
import it.unibo.equishare.data.remote.datasource.SupabaseGroupsDataSource
import it.unibo.equishare.data.remote.dto.GroupDto
import it.unibo.equishare.data.remote.dto.GroupMemberDto
import it.unibo.equishare.data.remote.mappers.toDomain
import it.unibo.equishare.domain.model.AppCategory
import it.unibo.equishare.domain.model.Currency
import it.unibo.equishare.domain.model.Group
import it.unibo.equishare.domain.model.GroupBalanceSummary
import it.unibo.equishare.domain.model.GroupMember
import it.unibo.equishare.domain.model.GroupMemberBalance
import it.unibo.equishare.domain.model.GroupSettings
import it.unibo.equishare.domain.model.GroupType
import it.unibo.equishare.domain.model.InviteResult
import it.unibo.equishare.domain.model.MemberRole
import it.unibo.equishare.domain.model.Money
import it.unibo.equishare.domain.repository.AuthRepository
import it.unibo.equishare.domain.repository.GroupsRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs

class SupabaseGroupsRepository(
    private val remote: SupabaseGroupsDataSource,
    private val auth: AuthRepository,
    private val local: EquiShareLocalDataSource,
    private val preferences: UserPreferencesDataSource,
) : RefreshableRepository(), GroupsRepository {

    init { watchAuth(auth.isSignedIn) }

    override val groups: Flow<List<Group>> = refreshableCacheFirst { isForced ->
        val uid = auth.currentUserId ?: run { emit(emptyList()); return@refreshableCacheFirst }
        val cached = local.groups(uid)
        emit(cached)
        if (!isForced && !shouldRefreshFromRemote()) return@refreshableCacheFirst
        try {
            val rows = remote.fetchGroups()
            local.replaceGroups(uid, rows)
            preferences.setLastSyncAt(System.currentTimeMillis())
            emit(rows.map { it.toDomain() })
        } catch (t: Throwable) {
            t.rethrowIfCancellation()
        }
    }

    override fun getById(id: String): Flow<Group?> = refreshableCacheFirst { _isForced ->
        val uid = auth.currentUserId ?: run { emit(null); return@refreshableCacheFirst }
        emit(local.group(uid, id))
        try {
            val fresh = remote.fetchGroupById(id)?.also { local.upsertGroup(uid, it) }?.toDomain()
            emit(fresh)
        } catch (t: Throwable) {
            t.rethrowIfCancellation()
        }
    }

    override fun getSettingsById(id: String): Flow<GroupSettings?> = refreshableCacheFirst { _isForced ->
        val uid = auth.currentUserId ?: run { emit(null); return@refreshableCacheFirst }
        emit(local.groupSettings(uid, id))
        try {
            val fresh = remote.fetchGroupById(id)
                ?.also { local.upsertGroup(uid, it) }
                ?.let { row ->
                    GroupSettings(
                        name = row.name,
                        description = row.description,
                        avatarUrl = row.avatarUrl,
                        currentUserRole = MemberRole.fromDb(row.role),
                        memberCount = row.memberCount,
                        currentUserBalance = Money.of(row.balance, Currency.fromCode(row.baseCurrency)),
                    )
                }
            emit(fresh)
        } catch (t: Throwable) {
            t.rethrowIfCancellation()
        }
    }

    override fun getGroupMembers(groupId: String): Flow<List<GroupMember>> = refreshableCacheFirst { _isForced ->
        val currentUserId = auth.currentUserId ?: run { emit(emptyList()); return@refreshableCacheFirst }
        emit(local.groupMembers(currentUserId, groupId))
        try {
            val members = remote.fetchGroupMemberRows(groupId)
            val profileById = remote.fetchMemberProfiles(members.map { it.userId }.distinct())
            val domainMembers = members.map { member ->
                member.toGroupMember(profileById[member.userId], currentUserId)
            }
            local.replaceGroupMembers(currentUserId, groupId, domainMembers)
            emit(domainMembers)
        } catch (t: Throwable) {
            t.rethrowIfCancellation()
        }
    }

    override fun getCurrentUserGroupBalances(groupId: String): Flow<GroupBalanceSummary> = refreshableCacheFirst { _isForced ->
        val currentUserId = auth.currentUserId ?: run { emit(GroupBalanceSummary.empty()); return@refreshableCacheFirst }
        emit(GroupBalanceSummary.empty())
        try {
            val currencyCode = remote.fetchGroupCurrency(groupId) ?: "EUR"
            val currency = Currency.fromCode(currencyCode)
            val expenses = remote.fetchBalanceExpenses(groupId)
                .filter { Currency.fromCode(it.currency) == currency }
            val expenseIds = expenses.map { it.id }
            val participantsByExpenseId = remote.fetchExpenseParticipants(expenseIds)
                .groupBy { it.expenseId }
            val memberBalances = mutableMapOf<String, BigDecimal>()
            expenses.forEach { expense ->
                accumulateExpenseBalances(
                    currentUserId = currentUserId,
                    participants = participantsByExpenseId[expense.id].orEmpty(),
                    memberBalances = memberBalances,
                )
            }
            remote.fetchGroupPayments(groupId)
                .filter { Currency.fromCode(it.currency) == currency }
                .forEach { payment ->
                    val amount = BigDecimal.valueOf(payment.amount)
                    when {
                        payment.fromUserId == currentUserId && payment.toUserId != currentUserId ->
                            memberBalances.addTo(payment.toUserId, amount)
                        payment.toUserId == currentUserId && payment.fromUserId != currentUserId ->
                            memberBalances.addTo(payment.fromUserId, amount.negate())
                    }
                }
            emit(memberBalances.toGroupBalanceSummary(currency))
        } catch (t: Throwable) {
            t.rethrowIfCancellation()
        }
    }

    override suspend fun currentUserCanAccess(groupId: String): Boolean {
        val uid = auth.currentUserId ?: return false
        val group = remote.fetchGroupById(groupId) ?: return false
        return remote.fetchMemberRole(group.id, uid) != null
    }

    override suspend fun getGroupCategories(): List<AppCategory> =
        try {
            val categories = remote.fetchGroupCategories()
            local.replaceGroupCategories(categories)
            categories.map { it.toDomain() }
        } catch (t: Throwable) {
            t.rethrowIfCancellation()
            local.groupCategories()
        }

    // Wrapped in NonCancellable: navigation away cancels viewModelScope before the
    // group_members insert completes, leaving an orphan groups row RLS won't return.
    override suspend fun create(
        name: String,
        description: String?,
        type: GroupType,
        categoryId: String?,
    ): String = withContext(NonCancellable) {
        val uid = auth.currentUserId ?: error("Not signed in")
        val inserted = remote.insertGroup(
            GroupDto(
                name = name,
                description = description,
                type = type.dbValue,
                categoryId = categoryId,
                createdBy = uid,
            )
        )
        remote.insertGroupMember(
            GroupMemberDto(
                groupId = inserted.id,
                userId = uid,
                role = MemberRole.OWNER.dbValue,
            )
        )
        refresh()
        inserted.id
    }

    override suspend fun updateGroup(
        id: String,
        name: String?,
        description: String?,
        avatarUrl: String?,
    ) {
        remote.updateGroupFields(id, name, description, avatarUrl)
        auth.currentUserId?.let { local.updateGroup(it, id, name, description, avatarUrl) }
        refresh()
    }

    override suspend fun uploadGroupPhoto(groupId: String, bytes: ByteArray, mimeType: String): Result<String> =
        runCatching {
            val uid = auth.currentUserId ?: error("Not signed in")
            val url = remote.uploadGroupPhoto(uid, groupId, bytes, mimeType)
            updateGroup(id = groupId, avatarUrl = url)
            url
        }

    override suspend fun archive(id: String) = withContext(NonCancellable) {
        remote.archiveGroup(id)
        auth.currentUserId?.let { local.deleteGroup(it, id) }
        refresh()
    }

    // Projects deleted IDs back from PostgREST: an RLS denial returns 200 OK with
    // zero rows, which would otherwise silently wipe local cache while the server
    // row stays intact.
    override suspend fun delete(id: String): Result<Unit> = withContext(NonCancellable) {
        runCatching {
            require(currentUserCanManageGroup(id)) { "Only admins can delete groups" }
            val deleted = remote.deleteGroup(id)
            check(deleted.isNotEmpty()) { "No group rows were deleted (RLS denied?)" }
            auth.currentUserId?.let { local.deleteGroup(it, id) }
            refresh()
        }
    }

    override suspend fun leaveGroup(id: String, successorUserId: String?): Result<Unit> = withContext(NonCancellable) {
        runCatching {
            val uid = auth.currentUserId ?: error("Not signed in")
            require(remote.fetchMemberBalance(id, uid).isSettled()) {
                "You can leave the group only when your balance is settled"
            }
            val status = remote.leaveGroupWithSuccessor(id, successorUserId)
            check(status in setOf("LEFT", "DELETED", "TRANSFERRED")) {
                "Unexpected group leave status: $status"
            }
            local.deleteGroup(uid, id)
            refresh()
        }
    }

    override suspend fun removeMember(groupId: String, userId: String): Result<Unit> =
        runCatching {
            remote.setMemberLeftAt(groupId, userId)
            auth.currentUserId?.let { local.deleteGroupMember(it, groupId, userId) }
            refresh()
        }

    private suspend fun currentUserCanManageGroup(groupId: String): Boolean {
        val uid = auth.currentUserId ?: return false
        return remote.fetchMemberRole(groupId, uid)
            ?.role
            ?.let { MemberRole.fromDb(it).canManage } == true
    }

    private fun Double.isSettled(): Boolean = abs(this) < 0.005

    override suspend fun setFavorite(groupId: String, isFavorite: Boolean) {
        val uid = auth.currentUserId ?: return
        // 1. Optimistic local update — UI reflects the change instantly.
        local.setGroupFavorite(uid, groupId, isFavorite)
        // 2. Re-emit from local cache so the groups flow picks up the new value
        //    without triggering an unnecessary network fetch.
        refreshLocal()
        // 3. Persist to remote in the background; best-effort.
        repositoryScope.launch {
            try {
                remote.setGroupFavorite(groupId, isFavorite)
            } catch (t: Throwable) {
                t.rethrowIfCancellation()
                // Remote call failed — local optimistic state remains until the
                // next full refresh, which will reconcile with remote.
            }
        }
    }

    override suspend fun inviteMember(groupId: String, email: String): InviteResult = try {
        val payload = remote.inviteMemberByEmail(groupId, email)
        when (val status = payload["status"]?.jsonPrimitive?.content) {
            "OK" -> {
                refresh()
                InviteResult.Success(
                    userId      = payload["user_id"]?.jsonPrimitive?.content.orEmpty(),
                    displayName = payload["display_name"]?.jsonPrimitive?.content.orEmpty(),
                )
            }
            "ALREADY_MEMBER"  -> InviteResult.AlreadyMember
            "ALREADY_INVITED" -> InviteResult.AlreadyInvited(
                userId = payload["user_id"]?.jsonPrimitive?.content.orEmpty(),
            )
            "NOT_FOUND" -> InviteResult.NotFound
            "FORBIDDEN" -> InviteResult.Forbidden
            "SELF"      -> InviteResult.Self
            else        -> InviteResult.Error(IllegalStateException("Unexpected status: $status"))
        }
    } catch (t: Throwable) {
        InviteResult.Error(t)
    }

    private suspend fun shouldRefreshFromRemote(): Boolean {
        val last = preferences.lastSyncAt.firstOrNull() ?: return true
        return System.currentTimeMillis() - last > SYNC_TTL_MS
    }

    private companion object {
        const val SYNC_TTL_MS = 5 * 60 * 1000L
    }
}

private fun GroupMemberRow.toGroupMember(
    profile: GroupMemberProfileRow?,
    currentUserId: String,
): GroupMember {
    val displayName = displayNameOverride
        ?.takeIf { it.isNotBlank() }
        ?: profile?.fullName?.takeIf { it.isNotBlank() }
        ?: profile?.email?.substringBefore("@")?.takeIf { it.isNotBlank() }
        ?: userId.take(8)
    return GroupMember(
        userId = userId,
        role = MemberRole.fromDb(role),
        displayName = displayName,
        email = profile?.email.orEmpty(),
        avatarUrl = profile?.avatarUrl,
        isCurrentUser = userId == currentUserId,
    )
}

private val BALANCE_EPSILON = BigDecimal("0.005")

private fun accumulateExpenseBalances(
    currentUserId: String,
    participants: List<it.unibo.equishare.data.remote.dto.ExpenseParticipantDto>,
    memberBalances: MutableMap<String, BigDecimal>,
) {
    val creditors = participants.mapNotNull { participant ->
        val net = BigDecimal.valueOf(participant.paidAmount)
            .subtract(BigDecimal.valueOf(participant.owedAmount))
        if (net.isSettledAmount()) null else when {
            net.signum() > 0 -> MutableBalance(participant.userId, net)
            else -> null
        }
    }
    val debtors = participants.mapNotNull { participant ->
        val net = BigDecimal.valueOf(participant.paidAmount)
            .subtract(BigDecimal.valueOf(participant.owedAmount))
        if (net.isSettledAmount()) null else when {
            net.signum() < 0 -> MutableBalance(participant.userId, net.abs())
            else -> null
        }
    }

    var debtorIndex = 0
    var creditorIndex = 0
    while (debtorIndex < debtors.size && creditorIndex < creditors.size) {
        val debtor = debtors[debtorIndex]
        val creditor = creditors[creditorIndex]
        val amount = if (debtor.amount <= creditor.amount) debtor.amount else creditor.amount

        when {
            debtor.userId == currentUserId && creditor.userId != currentUserId ->
                memberBalances.addTo(creditor.userId, amount.negate())
            creditor.userId == currentUserId && debtor.userId != currentUserId ->
                memberBalances.addTo(debtor.userId, amount)
        }

        debtor.amount = debtor.amount.subtract(amount)
        creditor.amount = creditor.amount.subtract(amount)

        if (debtor.amount.isSettledAmount()) debtorIndex += 1
        if (creditor.amount.isSettledAmount()) creditorIndex += 1
    }
}

private fun MutableMap<String, BigDecimal>.addTo(userId: String, amount: BigDecimal) {
    this[userId] = (this[userId] ?: BigDecimal.ZERO).add(amount)
}

private fun MutableMap<String, BigDecimal>.toGroupBalanceSummary(currency: Currency): GroupBalanceSummary {
    val balances = entries
        .filter { (memberId, amount) -> memberId.isNotBlank() && !amount.isSettledAmount() }
        .map { (memberId, amount) ->
            GroupMemberBalance(
                memberId = memberId,
                balance = Money.of(amount.setScale(2, RoundingMode.HALF_UP), currency),
            )
        }

    val totalYouAreOwed = balances
        .filter { it.balance.isPositive }
        .fold(Money.zero(currency)) { acc, balance -> acc + balance.balance }
    val totalYouOwe = balances
        .filter { it.balance.isNegative }
        .fold(Money.zero(currency)) { acc, balance -> acc + balance.balance.abs() }

    return GroupBalanceSummary(
        memberBalances = balances,
        totalYouOwe = totalYouOwe,
        totalYouAreOwed = totalYouAreOwed,
    )
}

private fun BigDecimal.isSettledAmount(): Boolean =
    abs().compareTo(BALANCE_EPSILON) < 0

private data class MutableBalance(
    val userId: String,
    var amount: BigDecimal,
)
