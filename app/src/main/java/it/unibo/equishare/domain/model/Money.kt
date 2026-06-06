/** Defines the Money domain model. */
package it.unibo.equishare.domain.model

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Currency value object. Wraps the ISO code and provides display helpers.
 *
 * Why: the codebase used `"EUR"`/`"USD"`/`"GBP"` as raw strings scattered across
 * repositories and view-models — classic Primitive Obsession smell. Centralising
 * the symbol/formatting here keeps the rest of the code currency-agnostic.
 */
enum class Currency(val code: String, val symbol: String) {
    EUR("EUR", "€"),
    USD("USD", "$"),
    GBP("GBP", "£");

    companion object {
        /** Lenient lookup; falls back to [EUR] for unknown / blank codes. */
        fun fromCode(code: String?): Currency =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: EUR
    }
}

/**
 * Money value object. Wraps an amount + currency and exposes domain-safe
 * operations.
 *
 * Internally uses [BigDecimal] with two fractional digits (HALF_UP) so that
 * accumulating totals and equal splits don't drift due to floating-point
 * representation — a real concern for an expense-sharing app.
 *
 * Construction helpers:
 *  - [of] for code that already has a Double (typical when coming from DB).
 *  - [zero] for additive identity.
 *  - [parse] for user input (returns null when not parseable).
 */
data class Money(val amount: BigDecimal, val currency: Currency) {

    init {
        require(amount.scale() <= 2) { "Money must be normalised to two decimals" }
    }

    operator fun plus(other: Money): Money {
        require(currency == other.currency) { "Cannot add ${currency.code} to ${other.currency.code}" }
        return Money((amount + other.amount).setScale(2, RoundingMode.HALF_UP), currency)
    }

    operator fun minus(other: Money): Money {
        require(currency == other.currency) { "Cannot subtract ${other.currency.code} from ${currency.code}" }
        return Money((amount - other.amount).setScale(2, RoundingMode.HALF_UP), currency)
    }

    fun abs(): Money = Money(amount.abs(), currency)

    val isPositive: Boolean get() = amount.signum() > 0
    val isNegative: Boolean get() = amount.signum() < 0
    val isZero: Boolean     get() = amount.signum() == 0

    /** "€150.00", "$5.00", … — locale-agnostic to keep this a pure value object. */
    fun formatted(): String = "%s%.2f".format(currency.symbol, amount.toDouble())

    /** Raw double for places that still need it (e.g. Supabase DTOs). */
    fun toDouble(): Double = amount.toDouble()

    /**
     * Split this amount equally across [keys], rounded to cents. Any rounding
     * remainder is distributed one cent at a time to the first keys, so
     * `sum(result.values) == this` exactly — no drift.
     *
     * Computed entirely in [BigDecimal]: previously we round-tripped via
     * `Double` (`cents / 100.0`), which for some cent counts produced binary
     * values like 14.289999… that JSON-serialised back to "14.29" but caused
     * the server-side check `sum(paid_amount) = total_amount` to fail on the
     * second decimal in edge cases.
     */
    fun <K> splitEqually(keys: List<K>): Map<K, Money> {
        if (keys.isEmpty()) return emptyMap()
        val totalCents = amount.movePointRight(2).toBigInteger().toLong()
        val baseCents = totalCents / keys.size
        val remainder = (totalCents - baseCents * keys.size).toInt()
        return keys.mapIndexed { index, key ->
            val cents = baseCents + if (index < remainder) 1 else 0
            key to Money(BigDecimal(cents).movePointLeft(2).setScale(2, RoundingMode.HALF_UP), currency)
        }.toMap()
    }

    companion object {
        fun of(value: Double, currency: Currency = Currency.EUR): Money =
            Money(BigDecimal(value).setScale(2, RoundingMode.HALF_UP), currency)

        fun of(value: BigDecimal, currency: Currency = Currency.EUR): Money =
            Money(value.setScale(2, RoundingMode.HALF_UP), currency)

        fun zero(currency: Currency = Currency.EUR): Money =
            Money(BigDecimal.ZERO.setScale(2), currency)

        /** Parse user input like "12.30", "12,30", "12". Returns null on failure. */
        fun parse(raw: String, currency: Currency = Currency.EUR): Money? {
            val normalized = raw.trim().replace(',', '.')
            val parsed = normalized.toBigDecimalOrNull() ?: return null
            if (parsed.signum() < 0) return null
            return of(parsed, currency)
        }
    }
}
