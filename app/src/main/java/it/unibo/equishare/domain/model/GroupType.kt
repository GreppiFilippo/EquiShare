/** Defines the Group Type domain model. */
package it.unibo.equishare.domain.model

/**
 * Mirrors the `group_type` enum in Postgres. Used as a fallback when a group
 * has no explicit category (older rows, third-party imports, …).
 */
enum class GroupType(val dbValue: String) {
    HOME("home"),
    TRIP("trip"),
    COUPLE("couple"),
    FRIENDS("friends"),
    OTHER("other");

    companion object {
        fun fromDb(value: String?): GroupType =
            entries.firstOrNull { it.dbValue.equals(value, ignoreCase = true) } ?: OTHER
    }
}
