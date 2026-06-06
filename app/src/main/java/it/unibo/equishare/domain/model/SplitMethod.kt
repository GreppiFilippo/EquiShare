/** Defines the Split Method domain model. */
package it.unibo.equishare.domain.model

/** Mirrors the `split_method` enum in Postgres. */
enum class SplitMethod(val dbValue: String) {
    EQUAL("equal"),
    EXACT("exact"),
    PERCENTAGE("percentage"),
    SHARES("shares"),
    ADJUSTMENT("adjustment");

    companion object {
        fun fromDb(value: String?): SplitMethod =
            entries.firstOrNull { it.dbValue.equals(value, ignoreCase = true) } ?: EQUAL
    }
}
