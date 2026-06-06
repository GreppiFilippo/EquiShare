/** Renders and validates the expense amount input. */
package it.unibo.equishare.ui.screens.expense.create

import java.math.BigDecimal

internal const val SAVE_ERROR_AMOUNT_TOO_LARGE = "AMOUNT_TOO_LARGE"
internal const val SAVE_ERROR_EXPENSE_PERMISSION_DENIED = "EXPENSE_PERMISSION_DENIED"
internal const val SAVE_ERROR_GROUP_ACCESS_LOST = "GROUP_ACCESS_LOST"
internal const val SAVE_ERROR_INVALID_AMOUNT = "INVALID_AMOUNT"
internal const val SAVE_ERROR_EXPENSE_NOT_FOUND = "EXPENSE_NOT_FOUND"
internal const val SAVE_ERROR_GENERIC = "SAVE_FAILED"

private val MAX_EXPENSE_AMOUNT = BigDecimal("999999999999.99")

internal fun String.parseAmountInput(): BigDecimal? {
    val normalized = trim().replace(',', '.')
    if (normalized.isBlank() || normalized == ".") return null
    return normalized.toBigDecimalOrNull()
}

internal fun String.isPositiveSupportedAmountInput(): Boolean =
    parseAmountInput()?.let { amount ->
        amount.signum() > 0 && amount <= MAX_EXPENSE_AMOUNT
    } == true

internal fun String.isAmountTooLarge(): Boolean =
    parseAmountInput()?.let { it > MAX_EXPENSE_AMOUNT } == true
