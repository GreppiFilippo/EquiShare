/** Defines the Member Role domain model. */
package it.unibo.equishare.domain.model

/**
 * Role of a user inside a group. Mirrors the `member_role` enum in Postgres.
 *
 * Previously these values lived as raw `"owner" | "admin" | "member"` strings
 * scattered across repositories, view-models and even Compose code. Centralising
 * them here removes the magic strings and lets the compiler check exhaustiveness.
 */
enum class MemberRole(val dbValue: String) {
    OWNER("owner"),
    ADMIN("admin"),
    MEMBER("member");

    /** Both owner and admin can manage the group (kick, edit, invite, delete). */
    val canManage: Boolean get() = this == OWNER || this == ADMIN

    companion object {
        fun fromDb(value: String?): MemberRole =
            entries.firstOrNull { it.dbValue.equals(value, ignoreCase = true) } ?: MEMBER
    }
}
