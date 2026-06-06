/** Defines the Group domain model. */
package it.unibo.equishare.domain.model

/**
 * Domain entity for a group. Repositories return this; the UI layer maps it
 * into a screen-specific UI state.
 *
 * Previously repositories returned `GroupItem` (a Compose-presentation type
 * living under `ui/screens/groups/list/`) which violated the Dependency
 * Inversion Principle — the data layer depended on UI types.
 */
data class Group(
    val id: String,
    val name: String,
    val description: String?,
    val type: GroupType,
    val category: GroupCategory,
    val avatarUrl: String?,
    val baseCurrency: Currency,
    val currentUserRole: MemberRole,
    val memberCount: Int,
    val balance: Money,
    val isFavorite: Boolean = false,
)

/** Aggregated view of a group used by the settings screen. */
data class GroupSettings(
    val name: String,
    val description: String?,
    val avatarUrl: String?,
    val currentUserRole: MemberRole,
    val memberCount: Int,
    /**
     * Net balance of the current user inside the group:
     *   • positive — others owe the user
     *   • negative — the user owes others
     *   • zero — fully settled, safe to leave
     * Used by the settings screen to gate "Leave Group" on a clean balance.
     */
    val currentUserBalance: Money,
)

data class GroupBalanceSummary(
    val memberBalances: List<GroupMemberBalance>,
    val totalYouOwe: Money,
    val totalYouAreOwed: Money,
) {
    companion object {
        fun empty(currency: Currency = Currency.EUR): GroupBalanceSummary =
            GroupBalanceSummary(
                memberBalances = emptyList(),
                totalYouOwe = Money.zero(currency),
                totalYouAreOwed = Money.zero(currency),
            )
    }
}

/**
 * Signed balance against another member from the current user's perspective:
 * positive means the member owes the current user, negative means the current
 * user owes that member.
 */
data class GroupMemberBalance(
    val memberId: String,
    val balance: Money,
)

/** Domain entity for a group member (a profile joined with its membership). */
data class GroupMember(
    val userId: String,
    val role: MemberRole,
    val displayName: String,
    val email: String,
    val avatarUrl: String?,
    val isCurrentUser: Boolean,
)
