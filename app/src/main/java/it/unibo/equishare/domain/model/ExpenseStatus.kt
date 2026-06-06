/** Defines the Expense Status domain model. */
package it.unibo.equishare.domain.model

/** Mirrors the `expense_status` enum in Postgres. */
enum class ExpenseStatus(val dbValue: String) {
    DRAFT("draft"),
    POSTED("posted"),
    CANCELLED("cancelled");

    companion object {
        fun fromDb(value: String?): ExpenseStatus =
            entries.firstOrNull { it.dbValue.equals(value, ignoreCase = true) } ?: POSTED
    }
}
