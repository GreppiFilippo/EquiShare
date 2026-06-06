/** Defines the Statistics domain model. */
package it.unibo.equishare.domain.model

data class CategorySpending(
    val categoryId: String?,
    val categoryCode: String?,
    val categoryName: String?,
    val amount: Money,
    val translations: Map<String, String> = emptyMap(),
) {
    fun localizedName(language: String): String? =
        translations[language]?.takeIf { it.isNotBlank() }
            ?: translations.values.firstOrNull { it.isNotBlank() }
}
